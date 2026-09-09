package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.route.optimizer.OptimizedRoute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalTime;

@Schema(description = "A route plan proposal. Nothing is persisted: creating route versions "
        + "from a proposal stays an explicit decision by the planner.")
public record OptimizePlanResponse(
        Long chillingPlantId,
        String chillingPlantCode,
        Shift shift,
        LocalTime shiftStartTime,
        @Schema(description = "The estimates this plan rests on, echoed back so a plan is never "
                + "read as if it were based on live traffic or measured road distances")
        PlanningAssumptions assumptions,
        OptimizedRoute plan) {

    public record PlanningAssumptions(
            Duration maxMilkHoldingDuration,
            double averageSpeedKmph,
            double roadWindingFactor,
            Duration stopBaseServiceDuration,
            Duration perFarmerServiceDuration,
            @Schema(example = "Straight-line distance x winding factor / average speed; "
                    + "no live traffic and no external routing service")
            String travelTimeModel) {
    }
}
