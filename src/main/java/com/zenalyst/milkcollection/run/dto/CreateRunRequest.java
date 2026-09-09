package com.zenalyst.milkcollection.run.dto;

import com.zenalyst.milkcollection.common.domain.Shift;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateRunRequest(
        @Schema(description = "Must be the PUBLISHED version of a route") @NotNull Long routeVersionId,
        @NotNull Long tankerId,
        @NotNull Long chillingPlantId,
        @Schema(example = "2026-09-09", description = "Local collection date") @NotNull LocalDate runDate,
        @NotNull Shift shift,
        @Schema(example = "05:00", description = "Local departure time from the plant; "
                + "defaults to the configured start time for the shift")
        LocalTime plannedStartTime) {
}
