package com.zenalyst.milkcollection.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;

/**
 * Default shift start times, used when a run is created without an explicit planned start.
 */
@Validated
@ConfigurationProperties(prefix = "operations")
public record OperationsProperties(
        @NotNull LocalTime morningStartTime,
        @NotNull LocalTime eveningStartTime) {
}
