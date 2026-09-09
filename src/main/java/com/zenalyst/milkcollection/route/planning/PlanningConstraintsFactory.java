package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.config.MilkProperties;
import com.zenalyst.milkcollection.config.OperationsProperties;
import com.zenalyst.milkcollection.config.RoutingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Single place where configuration becomes planning constraints, so an optimisation preview
 * and the feasibility check that gates a real run can never disagree about the rules.
 */
@Component
@RequiredArgsConstructor
public class PlanningConstraintsFactory {

    private final MilkProperties milkProperties;
    private final RoutingProperties routingProperties;
    private final OperationsProperties operationsProperties;

    public PlanningConstraints create(Coordinates chillingPlantLocation, Shift shift,
                                      LocalTime shiftStartTime) {
        return new PlanningConstraints(
                chillingPlantLocation,
                shift,
                shiftStartTime != null ? shiftStartTime : operationsProperties.startTimeFor(shift),
                milkProperties.maxHoldingDuration(),
                routingProperties.stopBaseServiceDuration(),
                routingProperties.perFarmerServiceDuration());
    }
}
