package com.zenalyst.milkcollection.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

public record RouteStopRequest(
        @NotNull Long collectionPointId,
        @Schema(description = "Optional; when omitted the stop is appended after the existing ones")
        @Positive Integer sequenceNumber,
        @Schema(example = "05:40", description = "Planner's intended time of day; informational")
        LocalTime plannedArrivalTime,
        @Schema(example = "05:47") LocalTime plannedDepartureTime) {
}
