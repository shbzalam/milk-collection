package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.geo.Coordinates;

import java.math.BigDecimal;

/**
 * A stop of an existing route version, enriched with the demand information needed to project
 * a timetable and check feasibility.
 */
public record PlannedStop(
        Long routeStopId,
        Long collectionPointId,
        String collectionPointCode,
        int sequenceNumber,
        Coordinates location,
        long farmerCount,
        BigDecimal expectedLitres) {
}
