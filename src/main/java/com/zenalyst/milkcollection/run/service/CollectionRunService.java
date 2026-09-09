package com.zenalyst.milkcollection.run.service;

import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import com.zenalyst.milkcollection.chillingplant.service.ChillingPlantService;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.config.AppProperties;
import com.zenalyst.milkcollection.config.OperationsProperties;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.route.entity.RouteVersion;
import com.zenalyst.milkcollection.route.planning.PlannedStop;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.PlanningConstraintsFactory;
import com.zenalyst.milkcollection.route.planning.PlanningDataLoader;
import com.zenalyst.milkcollection.route.planning.ProjectedSchedule;
import com.zenalyst.milkcollection.route.planning.RouteFeasibilityService;
import com.zenalyst.milkcollection.route.repository.RouteStopRepository;
import com.zenalyst.milkcollection.route.service.RouteVersionService;
import com.zenalyst.milkcollection.run.dto.CollectionRunResponse;
import com.zenalyst.milkcollection.run.dto.CreateRunRequest;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.service.TankerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Creating and reading collection runs.
 *
 * <p>Creation is the gate where a plan meets reality: the route version must be published, the
 * tanker must be free and available, and the projected schedule must fit both the tanker's
 * capacity and the milk holding limit. Nothing is persisted for an infeasible run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionRunService {

    private static final DateTimeFormatter RUN_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CollectionRunRepository collectionRunRepository;
    private final RunStopRepository runStopRepository;
    private final RouteStopRepository routeStopRepository;
    private final RunQueryService runQueryService;
    private final RouteVersionService routeVersionService;
    private final TankerService tankerService;
    private final ChillingPlantService chillingPlantService;
    private final PlanningDataLoader planningDataLoader;
    private final PlanningConstraintsFactory constraintsFactory;
    private final RouteFeasibilityService routeFeasibilityService;
    private final OperationsProperties operationsProperties;
    private final AppProperties appProperties;

    @Transactional
    public CollectionRunResponse create(CreateRunRequest request) {
        RouteVersion routeVersion = routeVersionService.require(request.routeVersionId());
        if (!routeVersion.getStatus().isRunnable()) {
            throw new BusinessRuleException(ErrorCode.ROUTE_VERSION_NOT_PUBLISHED,
                    "Route version %d of route %s is %s; a run can only execute the published version"
                            .formatted(routeVersion.getVersionNumber(),
                                    routeVersion.getRoute().getRouteCode(), routeVersion.getStatus()));
        }

        Tanker tanker = tankerService.require(request.tankerId());
        if (!tanker.isActive()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Tanker " + tanker.getTankerCode() + " is " + tanker.getStatus());
        }
        ChillingPlant plant = chillingPlantService.require(request.chillingPlantId());
        if (!plant.isActive()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Chilling plant " + plant.getCode() + " is not active");
        }
        requireTankerSlotFree(tanker, request.runDate(), request.shift());

        LocalTime startTime = request.plannedStartTime() != null
                ? request.plannedStartTime()
                : operationsProperties.startTimeFor(request.shift());
        Instant plannedStart = toInstant(request.runDate(), startTime);

        List<PlannedStop> plannedStops =
                planningDataLoader.loadRouteVersionStops(routeVersion.getId(), request.shift());
        if (plannedStops.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                    "Route version " + routeVersion.getId() + " has no stops to run");
        }

        PlanningConstraints constraints =
                constraintsFactory.create(plant.coordinates(), request.shift(), startTime);
        // Reject an impossible run before anyone drives it, rather than turning milk away later.
        ProjectedSchedule schedule = routeFeasibilityService.requireFeasible(
                plannedStops, constraints, plannedStart, tanker.getCapacityLitres());

        CollectionRun run = collectionRunRepository.save(CollectionRun.builder()
                .runNumber(generateRunNumber(request.runDate(), request.shift()))
                .routeVersion(routeVersion)
                .tanker(tanker)
                .chillingPlant(plant)
                .runDate(request.runDate())
                .shift(request.shift())
                .plannedStartTime(plannedStart)
                .status(RunStatus.PLANNED)
                .build());

        runStopRepository.saveAll(schedule.stops().stream()
                .map(stop -> RunStop.builder()
                        .collectionRun(run)
                        .routeStop(routeStopRepository.getReferenceById(stop.routeStopId()))
                        .sequenceNumber(stop.sequenceNumber())
                        .plannedArrivalTime(stop.plannedArrival())
                        .status(RunStopStatus.PENDING)
                        .build())
                .toList());

        log.info("Created run {} for tanker {} on {} {} with {} stops, {} L expected, "
                        + "projected plant arrival {} (holding {})",
                run.getRunNumber(), tanker.getTankerCode(), request.runDate(), request.shift(),
                schedule.stops().size(), schedule.totalExpectedLitres(), schedule.plantArrival(),
                schedule.milkHoldingDuration());
        return runQueryService.getDetail(run.getId());
    }

    /**
     * A tanker cannot be booked twice for the same date and shift. The same rule is enforced by
     * a partial unique index, so a race between two dispatchers fails at the database rather
     * than producing two runs for one vehicle.
     */
    private void requireTankerSlotFree(Tanker tanker, LocalDate runDate, Shift shift) {
        if (collectionRunRepository.existsByTankerIdAndRunDateAndShiftAndStatusNot(
                tanker.getId(), runDate, shift, RunStatus.CANCELLED)) {
            throw new BusinessRuleException(ErrorCode.TANKER_ALREADY_ASSIGNED,
                    "Tanker %s already has a run on %s for the %s shift"
                            .formatted(tanker.getTankerCode(), runDate, shift));
        }
    }

    /**
     * The dairy operates in one timezone, so a local collection date plus a local departure
     * time resolve to exactly one instant. Everything downstream - schedules, holding time,
     * ETA - then works purely in {@link Instant}.
     */
    private Instant toInstant(LocalDate runDate, LocalTime localTime) {
        return runDate.atTime(localTime).atZone(appProperties.timeZone()).toInstant();
    }

    private String generateRunNumber(LocalDate runDate, Shift shift) {
        return "RUN-%s-%s-%05d".formatted(runDate.format(RUN_NUMBER_DATE),
                shift == Shift.MORNING ? "M" : "E", collectionRunRepository.nextRunNumber());
    }
}
