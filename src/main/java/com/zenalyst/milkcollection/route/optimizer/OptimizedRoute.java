package com.zenalyst.milkcollection.route.optimizer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/**
 * A complete plan proposal: one route per tanker used, plus an explicit account of anything
 * that could not be covered. Nothing is silently dropped - if the fleet cannot serve a
 * collection point, the planner is told which one and why.
 */
public record OptimizedRoute(
        List<ProposedRoute> routes,
        List<UnassignedCollectionPoint> unassigned,
        OptimizationSummary summary) {

    /** One tanker's proposed itinerary, plant to plant. */
    public record ProposedRoute(
            Long tankerId,
            String tankerCode,
            BigDecimal tankerCapacityLitres,
            BigDecimal totalExpectedLitres,
            BigDecimal capacityUtilisationPercent,
            LocalTime departureFromPlant,
            LocalTime plantArrivalTime,
            Duration totalRouteDuration,
            /* Age of the first-collected milk when the tanker reaches the plant. */
            Duration milkHoldingDuration,
            List<ProposedStop> stops) {
    }

    public record ProposedStop(
            int sequenceNumber,
            Long collectionPointId,
            String collectionPointCode,
            LocalTime plannedArrivalTime,
            LocalTime plannedDepartureTime,
            long farmerCount,
            BigDecimal expectedLitres) {
    }

    public record UnassignedCollectionPoint(Long collectionPointId, String collectionPointCode,
                                            String reason) {
    }

    public record OptimizationSummary(
            int collectionPointsConsidered,
            int collectionPointsAssigned,
            int collectionPointsUnassigned,
            int tankersAvailable,
            int tankersUsed,
            long farmersCovered,
            BigDecimal totalExpectedLitres) {
    }
}
