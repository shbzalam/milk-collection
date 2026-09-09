package com.zenalyst.milkcollection.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Inputs to the deterministic travel-time and service-time model.
 *
 * <p>MVP ASSUMPTIONS: straight-line distance is inflated by {@code roadWindingFactor} to
 * approximate road distance and divided by {@code averageSpeedKmph}. Service time at a stop
 * is {@code stopBaseServiceDuration + farmerCount * perFarmerServiceDuration}.
 * No live traffic and no external routing service are used.
 */
@Validated
@ConfigurationProperties(prefix = "routing")
public record RoutingProperties(
        @Positive double averageSpeedKmph,
        @DecimalMin("1.0") double roadWindingFactor,
        @NotNull Duration stopBaseServiceDuration,
        @NotNull Duration perFarmerServiceDuration) {
}
