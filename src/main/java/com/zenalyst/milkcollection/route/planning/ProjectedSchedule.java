package com.zenalyst.milkcollection.route.planning;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The timetable a run is expected to follow, projected from its planned start with the
 * configured travel- and service-time model.
 *
 * <p>{@code milkHoldingDuration} is measured from arrival at the <b>first</b> stop - the moment
 * the first litre enters the tanker - to arrival at the chilling plant. That is the age of the
 * oldest milk in the load, and therefore the number the holding-time limit applies to.
 */
public record ProjectedSchedule(
        Instant departureFromPlant,
        List<ProjectedStop> stops,
        Instant plantArrival,
        Duration milkHoldingDuration,
        Duration totalRouteDuration,
        BigDecimal totalExpectedLitres) {

    public record ProjectedStop(
            Long routeStopId,
            Long collectionPointId,
            String collectionPointCode,
            int sequenceNumber,
            Instant plannedArrival,
            Instant plannedDeparture,
            long farmerCount,
            BigDecimal expectedLitres) {
    }
}
