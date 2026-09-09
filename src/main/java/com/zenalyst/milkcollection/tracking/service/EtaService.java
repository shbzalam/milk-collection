package com.zenalyst.milkcollection.tracking.service;

import com.zenalyst.milkcollection.collection.dto.MilkCollectionSummary;
import com.zenalyst.milkcollection.collection.repository.MilkCollectionRepository;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.config.AppProperties;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.farmer.dto.FarmerSummary;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import com.zenalyst.milkcollection.farmer.repository.FarmerRepository;
import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.route.planning.ProjectedSchedule;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import com.zenalyst.milkcollection.run.service.RunScheduleService;
import com.zenalyst.milkcollection.tracking.dto.FarmerCollectionState;
import com.zenalyst.milkcollection.tracking.dto.FarmerNextCollectionResponse;
import com.zenalyst.milkcollection.tracking.dto.TankerLocationResponse;
import com.zenalyst.milkcollection.tracking.entity.TankerLocation;
import com.zenalyst.milkcollection.tracking.repository.TankerLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Answers the question the dairy currently cannot: "where is the tanker, and when will it reach
 * me?".
 *
 * <p>The estimate is built from the run's actual progress rather than from its original plan:
 * <ol>
 *   <li>find today's run whose route version covers the farmer's collection point;</li>
 *   <li>work out where the tanker is - its last reported position, or the last stop it reached;</li>
 *   <li>project the remaining stops forward with the configured travel- and service-time
 *       model, using the same projector that approved the run.</li>
 * </ol>
 *
 * <p>The result is an estimate from geometry and an assumed average speed. It is not
 * traffic-aware, and every response says so in {@code estimateBasis}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtaService {

    private static final String ESTIMATE_BASIS =
            "Estimated from the tanker's last reported position over its remaining stops, at the "
                    + "configured average speed. No live traffic or external routing data is used.";
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final EnumSet<RunStatus> RELEVANT_RUN_STATUSES =
            EnumSet.of(RunStatus.PLANNED, RunStatus.STARTED, RunStatus.IN_PROGRESS);

    private final FarmerRepository farmerRepository;
    private final CollectionRunRepository collectionRunRepository;
    private final RunStopRepository runStopRepository;
    private final MilkCollectionRepository milkCollectionRepository;
    private final TankerLocationRepository tankerLocationRepository;
    private final RunScheduleService runScheduleService;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FarmerNextCollectionResponse nextCollectionFor(Long farmerId) {
        Farmer farmer = farmerRepository.findWithAssociationsById(farmerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Farmer", farmerId));
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, appProperties.timeZone());
        Long collectionPointId = farmer.getCollectionPoint().getId();

        List<CollectionRun> runs = collectionRunRepository.findRunsServingCollectionPoint(
                collectionPointId, today, RELEVANT_RUN_STATUSES);
        if (runs.isEmpty()) {
            return noRunScheduled(farmer, today);
        }
        // Ordered by planned start, so an in-progress morning run is answered before an
        // evening run that has not left yet.
        CollectionRun run = runs.get(0);

        RunStop farmerStop = runStopRepository
                .findByRunIdAndCollectionPointId(run.getId(), collectionPointId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Run %s does not stop at collection point %d"
                                .formatted(run.getRunNumber(), collectionPointId)));

        Optional<TankerLocation> lastFix =
                tankerLocationRepository.findFirstByCollectionRunIdOrderByRecordedAtDesc(run.getId());
        List<MilkCollectionSummary> myCollections = milkCollectionRepository
                .findByRunStopIdAndFarmerId(farmerStop.getId(), farmerId)
                .map(MilkCollectionSummary::from)
                .map(List::of)
                .orElseGet(List::of);

        return switch (farmerStop.getStatus()) {
            case COMPLETED -> answer(farmer, run, lastFix, FarmerCollectionState.COLLECTED,
                    collectedMessage(farmer, farmerStop, myCollections), null, 0, myCollections, now);
            case SKIPPED -> answer(farmer, run, lastFix, FarmerCollectionState.SKIPPED,
                    "The tanker is not stopping at %s on run %s."
                            .formatted(farmer.getCollectionPoint().getCode(), run.getRunNumber()),
                    null, null, myCollections, now);
            case ARRIVED, COLLECTING -> answer(farmer, run, lastFix,
                    FarmerCollectionState.AT_YOUR_COLLECTION_POINT,
                    "Tanker %s is at %s now.".formatted(run.getTanker().getTankerCode(),
                            farmer.getCollectionPoint().getCode()),
                    farmerStop.getActualArrivalTime(), 0, myCollections, now);
            case PENDING -> estimateArrival(farmer, run, farmerStop, lastFix, myCollections, now);
        };
    }

    private FarmerNextCollectionResponse estimateArrival(
            Farmer farmer, CollectionRun run, RunStop farmerStop,
            Optional<TankerLocation> lastFix, List<MilkCollectionSummary> myCollections, Instant now) {

        List<RunStop> allStops = runStopRepository.findByRunIdWithCollectionPoint(run.getId());
        Origin origin = resolveOrigin(run, allStops, lastFix, now);
        ProjectedSchedule schedule = runScheduleService.projectFrom(
                run, origin.coordinates(), origin.departure(), origin.afterSequenceNumber());

        Long collectionPointId = farmer.getCollectionPoint().getId();
        Instant estimatedArrival = schedule.stopFor(collectionPointId)
                .map(ProjectedSchedule.ProjectedStop::plannedArrival)
                .orElse(null);
        int stopsBefore = schedule.stopsBefore(collectionPointId);

        FarmerCollectionState state = run.getStatus() == RunStatus.PLANNED
                ? FarmerCollectionState.SCHEDULED
                : FarmerCollectionState.EN_ROUTE;

        return answer(farmer, run, lastFix, state,
                pendingMessage(state, run, farmer, estimatedArrival, stopsBefore),
                estimatedArrival, stopsBefore, myCollections, now);
    }

    /**
     * Where the tanker is, when it can next move, and which stops are already behind it.
     *
     * <p>Progress is taken from the last stop with a recorded arrival, not simply the
     * highest-numbered non-pending stop: a stop skipped out of order has no arrival time and
     * must not be mistaken for progress.
     */
    private Origin resolveOrigin(CollectionRun run, List<RunStop> stops,
                                 Optional<TankerLocation> lastFix, Instant now) {
        if (run.getStatus() == RunStatus.PLANNED) {
            // Still at the plant. It cannot leave before its planned start.
            Instant departure = now.isAfter(run.getPlannedStartTime())
                    ? now : run.getPlannedStartTime();
            return new Origin(run.getChillingPlant().coordinates(), departure, 0);
        }

        RunStop lastReached = stops.stream()
                .filter(stop -> stop.getActualArrivalTime() != null)
                .max(Comparator.comparingInt(RunStop::getSequenceNumber))
                .orElse(null);
        Coordinates reportedPosition = lastFix.map(TankerLocation::coordinates).orElse(null);

        if (lastReached == null) {
            // On the road but not yet at any stop.
            return new Origin(
                    reportedPosition != null ? reportedPosition : run.getChillingPlant().coordinates(),
                    now, 0);
        }
        if (lastReached.getStatus().acceptsCollections()) {
            // Standing at a stop: the stop's own coordinates are authoritative, and the farmers
            // still queueing there delay departure.
            long served = milkCollectionRepository.countByRunStopId(lastReached.getId());
            return new Origin(runScheduleService.locationOf(lastReached),
                    now.plus(runScheduleService.outstandingServiceAt(lastReached, served)),
                    lastReached.getSequenceNumber());
        }
        // The last stop is finished; the tanker is driving to the next one.
        return new Origin(
                reportedPosition != null ? reportedPosition : runScheduleService.locationOf(lastReached),
                now, lastReached.getSequenceNumber());
    }

    private FarmerNextCollectionResponse noRunScheduled(Farmer farmer, LocalDate today) {
        log.debug("No run serving collection point {} on {}",
                farmer.getCollectionPoint().getCode(), today);
        return new FarmerNextCollectionResponse(
                FarmerSummary.from(farmer),
                CollectionPointSummary.from(farmer.getCollectionPoint()),
                FarmerCollectionState.NO_RUN_SCHEDULED,
                "No collection run covering %s is planned or under way on %s."
                        .formatted(farmer.getCollectionPoint().getCode(), today),
                null, null, null, null, null, List.of(), ESTIMATE_BASIS);
    }

    private FarmerNextCollectionResponse answer(
            Farmer farmer, CollectionRun run, Optional<TankerLocation> lastFix,
            FarmerCollectionState state, String message, Instant estimatedArrival,
            Integer stopsBefore, List<MilkCollectionSummary> collections, Instant now) {

        Duration timeToArrival = estimatedArrival == null || estimatedArrival.isBefore(now)
                ? null : Duration.between(now, estimatedArrival);

        return new FarmerNextCollectionResponse(
                FarmerSummary.from(farmer),
                CollectionPointSummary.from(farmer.getCollectionPoint()),
                state,
                message,
                new FarmerNextCollectionResponse.RunReference(
                        run.getId(), run.getRunNumber(), run.getRunDate(), run.getShift(),
                        run.getStatus(), run.getPlannedStartTime(), run.getActualStartTime(),
                        run.getTanker().getTankerCode(), run.getTanker().getRegistrationNumber(),
                        run.getRouteVersion().getRoute().getRouteCode(),
                        run.getRouteVersion().getVersionNumber()),
                lastFix.map(TankerLocationResponse::from).orElse(null),
                estimatedArrival,
                timeToArrival,
                stopsBefore,
                collections,
                ESTIMATE_BASIS);
    }

    private String pendingMessage(FarmerCollectionState state, CollectionRun run, Farmer farmer,
                                  Instant estimatedArrival, int stopsBefore) {
        String pointCode = farmer.getCollectionPoint().getCode();
        String tankerCode = run.getTanker().getTankerCode();
        if (estimatedArrival == null) {
            return "Tanker %s is assigned to %s on run %s, but no arrival estimate is available."
                    .formatted(tankerCode, pointCode, run.getRunNumber());
        }
        String expected = formatLocal(estimatedArrival);
        if (state == FarmerCollectionState.SCHEDULED) {
            return "Tanker %s leaves the plant at %s and should reach %s around %s, with %d stop(s) before yours."
                    .formatted(tankerCode, formatLocal(run.getPlannedStartTime()), pointCode,
                            expected, stopsBefore);
        }
        return "Tanker %s is on the road with %d stop(s) before yours and should reach %s around %s."
                .formatted(tankerCode, stopsBefore, pointCode, expected);
    }

    private String collectedMessage(Farmer farmer, RunStop stop,
                                    List<MilkCollectionSummary> collections) {
        String pointCode = farmer.getCollectionPoint().getCode();
        if (collections.isEmpty()) {
            return "Collection at %s is finished, but no milk was recorded for you on this run."
                    .formatted(pointCode);
        }
        MilkCollectionSummary collection = collections.get(0);
        return "Collection at %s is finished; %s L was recorded for you at %s."
                .formatted(pointCode, collection.quantityLitres(),
                        formatLocal(collection.collectedAt()));
    }

    private String formatLocal(Instant instant) {
        return LOCAL_TIME.format(instant.atZone(appProperties.timeZone()));
    }

    /** Where a projection starts: a position, the moment the tanker can leave it, and progress. */
    private record Origin(Coordinates coordinates, Instant departure, int afterSequenceNumber) {
    }
}
