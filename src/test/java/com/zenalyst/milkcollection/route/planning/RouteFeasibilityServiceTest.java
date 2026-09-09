package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.common.travel.TravelTimeProvider;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feasibility decisions, on top of the projection tested in {@link ScheduleProjectorTest}.
 * Same geometry: the plant at 18.50N, NEAR 5 min out, FAR 16 min out, one minute per kilometre.
 * The two-stop route used below runs 42 minutes with a 26-minute holding time and 120 L.
 */
class RouteFeasibilityServiceTest {

    private static final Coordinates PLANT = new Coordinates(18.50, 73.80);
    private static final Coordinates NEAR = new Coordinates(18.55, 73.80);
    private static final Coordinates FAR = new Coordinates(18.65, 73.80);
    private static final Instant DEPARTURE = Instant.parse("2026-09-09T05:00:00Z");

    private final TravelTimeProvider travelTime = (from, to) ->
            Duration.ofMinutes((long) from.haversineDistanceKm(to));
    private final ScheduleProjector projector = new ScheduleProjector(travelTime);
    private final RouteFeasibilityService service = new RouteFeasibilityService(projector);

    @Test
    void flagsExpectedVolumeAboveTankerCapacity() {
        PlanningConstraints constraints = constraints(Duration.ofHours(4));

        FeasibilityReport report = service.assess(twoStopRoute(constraints),
                new BigDecimal("100"), constraints);

        assertThat(report.feasible()).isFalse();
        assertThat(report.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo(ErrorCode.TANKER_CAPACITY_EXCEEDED);
            assertThat(violation.message()).contains("120 L", "100 L");
        });
    }

    @Test
    void flagsHoldingTimeAboveTheConfiguredLimit() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(20));

        FeasibilityReport report = service.assess(twoStopRoute(constraints),
                new BigDecimal("5000"), constraints);

        assertThat(report.feasible()).isFalse();
        assertThat(report.violations()).singleElement().satisfies(violation ->
                assertThat(violation.code()).isEqualTo(ErrorCode.MILK_HOLDING_TIME_EXCEEDED));
    }

    @Test
    @DisplayName("both problems are reported together so a planner sees the whole picture")
    void reportsEveryViolation() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(20));

        FeasibilityReport report = service.assess(twoStopRoute(constraints),
                new BigDecimal("100"), constraints);

        assertThat(report.violations()).extracting(FeasibilityReport.Violation::code)
                .containsExactly(ErrorCode.TANKER_CAPACITY_EXCEEDED,
                        ErrorCode.MILK_HOLDING_TIME_EXCEEDED);
    }

    @Test
    @DisplayName("exactly at the limits is still feasible - both bounds are inclusive")
    void treatsTheLimitsAsInclusive() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(26));
        ProjectedSchedule schedule = twoStopRoute(constraints);

        FeasibilityReport report = service.assess(schedule, new BigDecimal("120"), constraints);

        assertThat(schedule.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(26));
        assertThat(report.feasible()).isTrue();
        assertThat(report.violations()).isEmpty();
    }

    @Test
    void requireFeasibleThrowsTheMatchingBusinessError() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(20));
        List<PlannedStop> stops = List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"));

        assertThatThrownBy(() -> service.requireFeasible(stops, constraints, DEPARTURE,
                new BigDecimal("5000")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.MILK_HOLDING_TIME_EXCEEDED));
    }

    @Test
    void requireFeasibleReturnsTheScheduleWhenEverythingFits() {
        ProjectedSchedule schedule = service.requireFeasible(
                List.of(stop(1, "CP-NEAR", 1, NEAR, 2, "50")),
                constraints(Duration.ofHours(4)), DEPARTURE, new BigDecimal("5000"));

        assertThat(schedule.stops()).hasSize(1);
        // 3 min base + 2 farmers x 2 min service, then 5 min back to the plant.
        assertThat(schedule.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(12));
    }

    @Test
    @DisplayName("an empty route is feasible but pointless - publication validation rejects it")
    void treatsARouteWithNoStopsAsFeasible() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(1));

        FeasibilityReport report = service.assess(
                projector.projectFromPlant(List.of(), constraints, DEPARTURE),
                new BigDecimal("5000"), constraints);

        assertThat(report.feasible()).isTrue();
    }

    private ProjectedSchedule twoStopRoute(PlanningConstraints constraints) {
        return projector.projectFromPlant(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints, DEPARTURE);
    }

    private static PlannedStop stop(long routeStopId, String code, int sequence,
                                    Coordinates location, long farmers, String litres) {
        return new PlannedStop(routeStopId, routeStopId * 10, code, sequence, location, farmers,
                new BigDecimal(litres));
    }

    private static PlanningConstraints constraints(Duration maxHolding) {
        return new PlanningConstraints(PLANT, Shift.MORNING, LocalTime.of(5, 0), maxHolding,
                Duration.ofMinutes(3), Duration.ofMinutes(2));
    }
}
