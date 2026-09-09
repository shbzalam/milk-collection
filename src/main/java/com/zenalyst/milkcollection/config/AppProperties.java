package com.zenalyst.milkcollection.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * Application-wide settings.
 *
 * <p>The dairy operates in a single timezone, so every {@code LocalDate}/{@code LocalTime}
 * in the domain is resolved against this zone when converted to an {@link java.time.Instant}.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull ZoneId timeZone) {
}
