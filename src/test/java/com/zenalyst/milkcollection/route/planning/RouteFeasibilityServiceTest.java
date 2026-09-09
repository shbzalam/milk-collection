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
 * Same one-minute-per-kilometre model as the optimizer tests: the plant is at 18.50N, NEAR is
 * ~5 km / 5 min out and FAR is ~16 km / 16 min out.
 */
class RouteFeasibilityServiceTest {

    private static final Coordinates PLANT = new Coordinates(18.50, 73.80);
    private static final Coordinates NEAR = new Coordinates(18.55, 73.80);
    private static final Coordinates FAR = new Coordinates(18.65, 73.80);
    private static final Instant DEPARTURE = Instant.parse("2026-09-09T05:00:00Z");

    private final TravelTimeProvider travelTime = (from, to) ->
            Duration.ofMinutes((long) from.haversineDistanceKm(to));

    private final RouteFeasibilityService service = new RouteFeasibilityService(travelTime);

    @Test
    @DisplayName("projects arrival and departure per stop, then arrival back at the plant")
    void projectsTheTimetable() {
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        assertThat(schedule.departureFromPlant()).isEqualTo(DEPARTURE);
        assertThat(schedule.stops()).hasSize(2);
        assertThat(schedule.stops().get(0).plannedArrival()).isEqualTo(DEPARTURE.plus(Duration.ofMinutes(16)));
        assertThat(schedule.stops().get(0).plannedDeparture()).isEqualTo(DEPARTURE.plus(Duration.ofMinutes(21)));
        assertThat(schedule.stops().get(1).plannedArrival()).isEqualTo(DEPARTURE.plus(Duration.ofMinutes(32)));
        assertThat(schedule.stops().get(1).plannedDeparture()).isEqualTo(DEPARTURE.plus(Duration.ofMinutes(37)));
        assertThat(schedule.plantArrival()).isEqualTo(DEPARTURE.plus(Duration.ofMinutes(42)));
        assertThat(schedule.totalRouteDuration()).isEqualTo(Duration.ofMinutes(42));
        assertThat(schedule.totalExpectedLitres()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("holding time is measured from the first collection, not from leaving the plant")
    void measuresHoldingFromTheFirstCollection() {
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        // 42 minutes of route, but the first litre only entered the tanker after 16 minutes.
        assertThat(schedule.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(26));
    }

    @Test
    @DisplayName("stops are visited in the order given - projection never reorders a route")
    void keepsTheGivenStopOrder() {
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-NEAR", 1, NEAR, 1, "50"), stop(2, "CP-FAR", 2, FAR, 1, "70")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        assertThat(schedule.stops())
                .extracting(ProjectedSchedule.ProjectedStop::collectionPointCode)
                .containsExactly("CP-NEAR", "CP-FAR");
        // Nearest first means the long leg happens with milk already on board: 5 + 5 + 11 + 5 + 16
        assertThat(schedule.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(37));
    }

    @Test
    void handlesARouteWithNoStops() {
        ProjectedSchedule schedule = service.project(List.of(), constraints(Duration.ofHours(4)), DEPARTURE);

        assertThat(schedule.stops()).isEmpty();
        assertThat(schedule.plantArrival()).isEqualTo(DEPARTURE);
        assertThat(schedule.milkHoldingDuration()).isZero();
        assertThat(schedule.totalExpectedLitres()).isEqualByComparingTo("0");
    }

    @Test
    void flagsExpectedVolumeAboveTankerCapacity() {
        PlanningConstraints constraints = constraints(Duration.ofHours(4));
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints, DEPARTURE);

        FeasibilityReport report = service.assess(schedule, new BigDecimal("100"), constraints);

        assertThat(report.feasible()).isFalse();
        assertThat(report.violations()).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.code()).isEqualTo(ErrorCode.TANKER_CAPACITY_EXCEEDED);
                    assertThat(violation.message()).contains("120 L", "100 L");
                });
    }

    @Test
    void flagsHoldingTimeAboveTheConfiguredLimit() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(20));
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints, DEPARTURE);

        FeasibilityReport report = service.assess(schedule, new BigDecimal("5000"), constraints);

        assertThat(report.feasible()).isFalse();
        assertThat(report.violations()).singleElement()
                .satisfies(violation ->
                        assertThat(violation.code()).isEqualTo(ErrorCode.MILK_HOLDING_TIME_EXCEEDED));
    }

    @Test
    @DisplayName("both problems are reported together so a planner sees the whole picture")
    void reportsEveryViolation() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(20));
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints, DEPARTURE);

        FeasibilityReport report = service.assess(schedule, new BigDecimal("100"), constraints);

        assertThat(report.violations()).extracting(FeasibilityReport.Violation::code)
                .containsExactly(ErrorCode.TANKER_CAPACITY_EXCEEDED, ErrorCode.MILK_HOLDING_TIME_EXCEEDED);
    }

    @Test
    @DisplayName("exactly at the limit is still feasible - the rules are inclusive bounds")
    void treatsTheLimitsAsInclusive() {
        PlanningConstraints constraints = constraints(Duration.ofMinutes(26));
        ProjectedSchedule schedule = service.project(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints, DEPARTURE);

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

    // --- fixtures ----------------------------------------------------------------

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
