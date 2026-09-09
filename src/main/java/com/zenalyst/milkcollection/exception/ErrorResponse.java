package com.zenalyst.milkcollection.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Consistent error payload returned for every failed request")
public record ErrorResponse(
        @Schema(example = "2026-09-09T04:31:00Z") Instant timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "TANKER_CAPACITY_EXCEEDED") String code,
        @Schema(example = "Collection would exceed tanker capacity") String message,
        @Schema(example = "/api/v1/runs/1/stops/2/collections") String path,
        @Schema(description = "Field-level details, present only for request validation failures")
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(Instant timestamp, ErrorCode code, String message, String path) {
        return new ErrorResponse(timestamp, code.status().value(), code.name(), message, path, null);
    }
}
