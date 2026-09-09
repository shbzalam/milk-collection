package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.common.travel.TravelTimeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection engine, with a one-minute-per-kilometre travel model so every expected time
 * is derivable by hand: the plant is at 18.50N, NEAR is ~5 km / 5 min out and FAR is
 * ~16 km / 16 min out.
 */
class ScheduleProjectorTest {

    private static final Coordinates PLANT = new Coordinates(18.50, 73.80);
    private static final Coordinates NEAR = new Coordinates(18.55, 73.80);
    private static final Coordinates FAR = new Coordinates(18.65, 73.80);
    private static final Instant DEPARTURE = Instant.parse("2026-09-09T05:00:00Z");

    private final TravelTimeProvider travelTime = (from, to) ->
            Duration.ofMinutes((long) from.haversineDistanceKm(to));

    private final ScheduleProjector projector = new ScheduleProjector(travelTime);

    @Test
    @DisplayName("projects arrival and departure per stop, then arrival back at the plant")
    void projectsTheTimetable() {
        ProjectedSchedule schedule = projector.projectFromPlant(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        assertThat(schedule.departureFromOrigin()).isEqualTo(DEPARTURE);
        assertThat(schedule.stops()).hasSize(2);
        assertThat(schedule.stops().get(0).plannedArrival()).isEqualTo(at(16));
        assertThat(schedule.stops().get(0).plannedDeparture()).isEqualTo(at(21));
        assertThat(schedule.stops().get(1).plannedArrival()).isEqualTo(at(32));
        assertThat(schedule.stops().get(1).plannedDeparture()).isEqualTo(at(37));
        assertThat(schedule.plantArrival()).isEqualTo(at(42));
        assertThat(schedule.totalDuration()).isEqualTo(Duration.ofMinutes(42));
        assertThat(schedule.totalExpectedLitres()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("holding time is measured from the first collection, not from leaving the plant")
    void measuresHoldingFromTheFirstCollection() {
        ProjectedSchedule schedule = projector.projectFromPlant(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        // 42 minutes of route, but the first litre only entered the tanker after 16 minutes.
        assertThat(schedule.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(26));
        // Milk loaded later is younger on arrival.
        assertThat(schedule.holdingDurationFrom(at(32))).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("service time grows with the number of farmers at a stop")
    void serviceTimeAccountsForEveryFarmerAtAStop() {
        ProjectedSchedule schedule = projector.projectFromPlant(
                List.of(stop(1, "CP-SHARED", 1, NEAR, 2, "100")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        ProjectedSchedule.ProjectedStop stop = schedule.stops().get(0);
        // 3 min base + 2 farmers x 2 min.
        assertThat(Duration.between(stop.plannedArrival(), stop.plannedDeparture()))
                .isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    @DisplayName("stops are visited in the order given - projection never reorders a route")
    void keepsTheGivenStopOrder() {
        ProjectedSchedule schedule = projector.projectFromPlant(
                List.of(stop(1, "CP-NEAR", 1, NEAR, 1, "50"), stop(2, "CP-FAR", 2, FAR, 1, "70")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        assertThat(schedule.stops())
                .extracting(ProjectedSchedule.ProjectedStop::collectionPointCode)
                .containsExactly("CP-NEAR", "CP-FAR");
        // Nearest first means the long leg is driven with milk already on board: 5+5+11+5+16.
        assertThat(schedule.milkHoldingDuration()).isEqualTo(Duration.ofMinutes(37));
    }

    @Test
    @DisplayName("projecting from a mid-route position covers only the stops that are left")
    void projectsFromAnArbitraryPosition() {
        // The tanker is standing at FAR and only NEAR is left.
        ProjectedSchedule schedule = projector.project(FAR, DEPARTURE,
                List.of(stop(2, "CP-NEAR", 2, NEAR, 1, "50")), constraints(Duration.ofHours(4)));

        assertThat(schedule.stops()).hasSize(1);
        assertThat(schedule.stops().get(0).plannedArrival()).isEqualTo(at(11));
        assertThat(schedule.plantArrival()).isEqualTo(at(21));
    }

    @Test
    @DisplayName("with no stops left the tanker heads straight to the plant from where it is")
    void projectsTheRunHomeWithNoStopsLeft() {
        ProjectedSchedule schedule = projector.project(FAR, DEPARTURE, List.of(),
                constraints(Duration.ofHours(4)));

        assertThat(schedule.stops()).isEmpty();
        assertThat(schedule.plantArrival()).isEqualTo(at(16));
        assertThat(schedule.milkHoldingDuration()).isZero();
        assertThat(schedule.totalExpectedLitres()).isEqualByComparingTo("0");
    }

    @Test
    void reportsHowManyStopsPrecedeAGivenCollectionPoint() {
        ProjectedSchedule schedule = projector.projectFromPlant(
                List.of(stop(1, "CP-FAR", 1, FAR, 1, "70"), stop(2, "CP-NEAR", 2, NEAR, 1, "50")),
                constraints(Duration.ofHours(4)), DEPARTURE);

        assertThat(schedule.stopsBefore(10L)).isZero();
        assertThat(schedule.stopsBefore(20L)).isEqualTo(1);
        assertThat(schedule.stopFor(20L)).isPresent();
        assertThat(schedule.stopFor(999L)).isEmpty();
    }

    private static Instant at(int minutesAfterDeparture) {
        return DEPARTURE.plus(Duration.ofMinutes(minutesAfterDeparture));
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
