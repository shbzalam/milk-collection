package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.geo.Coordinates;

import java.time.Duration;
import java.time.LocalTime;

/**
 * The rules a plan has to respect. Everything here is configuration or run context, never a
 * hardcoded constant, so the same optimizer can plan for a different plant, shift or
 * holding-time limit.
 *
 * @param chillingPlantLocation    where every route starts and ends
 * @param shift                    which shift is being planned (selects expected quantities)
 * @param shiftStartTime           local time the tanker leaves the plant
 * @param maxHoldingDuration       longest the first-collected milk may stay in the tanker
 * @param stopBaseServiceDuration  fixed handling time per stop
 * @param perFarmerServiceDuration additional handling time per farmer at a stop
 */
public record PlanningConstraints(
        Coordinates chillingPlantLocation,
        Shift shift,
        LocalTime shiftStartTime,
        Duration maxHoldingDuration,
        Duration stopBaseServiceDuration,
        Duration perFarmerServiceDuration) {

    /**
     * Time spent standing at a stop. Two farmers at one collection point take longer to serve
     * than one, which is precisely why farmer counts are part of planning.
     */
    public Duration serviceDurationFor(long farmerCount) {
        return stopBaseServiceDuration.plus(perFarmerServiceDuration.multipliedBy(farmerCount));
    }
}
