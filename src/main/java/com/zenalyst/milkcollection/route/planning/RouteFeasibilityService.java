package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a route can actually be driven by a given tanker without spoiling milk or
 * overflowing the tank.
 *
 * <p>This runs <b>before</b> execution, when a run is created. Catching an impossible plan
 * there is the whole point: turning a farmer's milk away at 6 a.m. because the route was never
 * feasible is a planning failure, not an operational one.
 *
 * <p>Deliberately free of repositories - it takes value objects and returns value objects, so
 * every rule here is unit-testable without a database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteFeasibilityService {

    private final ScheduleProjector scheduleProjector;

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
     * Projects the whole route from the plant and validates it, throwing the first violation.
     * Used on the write path, where an infeasible run must not be persisted at all.
     */
    public ProjectedSchedule requireFeasible(List<PlannedStop> stops, PlanningConstraints constraints,
                                             Instant departureFromPlant, BigDecimal tankerCapacityLitres) {
        ProjectedSchedule schedule =
                scheduleProjector.projectFromPlant(stops, constraints, departureFromPlant);
        FeasibilityReport report = assess(schedule, tankerCapacityLitres, constraints);
        if (!report.feasible()) {
            FeasibilityReport.Violation first = report.violations().get(0);
            log.info("Rejected infeasible plan: {}", report.violations());
            throw new BusinessRuleException(first.code(), first.message());
        }
        return schedule;
    }
}
