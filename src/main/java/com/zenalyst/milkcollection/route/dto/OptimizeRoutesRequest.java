package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.common.domain.Shift;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record OptimizeRoutesRequest(
        @NotNull Long chillingPlantId,
        @NotNull Shift shift,
        @Schema(example = "05:00", description = "Optional; defaults to the configured shift start")
        LocalTime shiftStartTime,
        @Schema(description = "Optional; defaults to every active collection point")
        List<Long> collectionPointIds,
        @Schema(description = "Optional; defaults to every active tanker")
        List<Long> tankerIds) {
}
