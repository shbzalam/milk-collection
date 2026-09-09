package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.common.travel.TravelTimeProvider;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a route can actually be driven by a given tanker without spoiling milk or
 * overflowing the tank.
 *
 * <p>This runs <b>before</b> execution, when a run is created. Catching an impossible plan at
 * that point is the whole idea: rejecting a farmer's milk at 6 a.m. because the route was never
 * feasible is a planning failure, not an operational one.
 *
 * <p>Deliberately free of repositories - it takes value objects and returns value objects, so
 * every rule here is unit-testable without a database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteFeasibilityService {

    private final TravelTimeProvider travelTimeProvider;

    /**
     * Projects arrival and departure times for each stop, then the arrival back at the plant.
     *
     * <p>Stops are visited in the order given; this method does not reorder anything. All
     * arithmetic is on {@link Instant}s derived from a single departure instant, so there is no
     * local-time or midnight-wrap arithmetic anywhere in the calculation.
     */
    public ProjectedSchedule project(List<PlannedStop> stops, PlanningConstraints constraints,
                                     Instant departureFromPlant) {
        List<ProjectedSchedule.ProjectedStop> projected = new ArrayList<>(stops.size());
        Coordinates position = constraints.chillingPlantLocation();
        Instant cursor = departureFromPlant;
        BigDecimal totalLitres = BigDecimal.ZERO;

        for (PlannedStop stop : stops) {
            Instant arrival = cursor.plus(travelTimeProvider.estimateTravelTime(position, stop.location()));
            Instant departure = arrival.plus(constraints.serviceDurationFor(stop.farmerCount()));
            projected.add(new ProjectedSchedule.ProjectedStop(
                    stop.routeStopId(), stop.collectionPointId(), stop.collectionPointCode(),
                    stop.sequenceNumber(), arrival, departure, stop.farmerCount(), stop.expectedLitres()));
            totalLitres = totalLitres.add(stop.expectedLitres());
            position = stop.location();
            cursor = departure;
        }

        Instant plantArrival = stops.isEmpty()
                ? departureFromPlant
                : cursor.plus(travelTimeProvider.estimateTravelTime(
                        position, constraints.chillingPlantLocation()));
        Duration holding = projected.isEmpty()
                ? Duration.ZERO
                : Duration.between(projected.get(0).plannedArrival(), plantArrival);

        return new ProjectedSchedule(departureFromPlant, List.copyOf(projected), plantArrival,
                holding, Duration.between(departureFromPlant, plantArrival), totalLitres);
    }

    /**
     * Checks a projected schedule against the two hard constraints. Both are reported rather
     * than short-circuiting, so a planner sees everything wrong with a plan at once.
     */
    public FeasibilityReport assess(ProjectedSchedule schedule, BigDecimal tankerCapacityLitres,
                                    PlanningConstraints constraints) {
        List<FeasibilityReport.Violation> violations = new ArrayList<>(2);

        if (schedule.totalExpectedLitres().compareTo(tankerCapacityLitres) > 0) {
            violations.add(new FeasibilityReport.Violation(ErrorCode.TANKER_CAPACITY_EXCEEDED,
                    "Expected %s L exceeds the tanker capacity of %s L"
                            .formatted(schedule.totalExpectedLitres(), tankerCapacityLitres)));
        }
        if (schedule.milkHoldingDuration().compareTo(constraints.maxHoldingDuration()) > 0) {
            violations.add(new FeasibilityReport.Violation(ErrorCode.MILK_HOLDING_TIME_EXCEEDED,
                    "Milk from the first stop would be %s old on arrival at the plant, over the %s limit"
                            .formatted(schedule.milkHoldingDuration(), constraints.maxHoldingDuration())));
        }
        return new FeasibilityReport(violations.isEmpty(), List.copyOf(violations), schedule);
    }

    /**
     * Projects and validates in one step, throwing the first violation. Used on the write path
     * where an infeasible plan must not be persisted.
     */
    public ProjectedSchedule requireFeasible(List<PlannedStop> stops, PlanningConstraints constraints,
                                             Instant departureFromPlant, BigDecimal tankerCapacityLitres) {
        FeasibilityReport report = assess(project(stops, constraints, departureFromPlant),
                tankerCapacityLitres, constraints);
        if (!report.feasible()) {
            FeasibilityReport.Violation first = report.violations().get(0);
            log.info("Rejected infeasible plan: {}", report.violations());
            throw new BusinessRuleException(first.code(), first.message());
        }
        return report.schedule();
    }
}
