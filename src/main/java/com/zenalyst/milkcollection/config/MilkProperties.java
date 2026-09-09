package com.zenalyst.milkcollection.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Milk quality constraints.
 *
 * <p>MVP ASSUMPTION: the assignment does not specify how long milk may sit in a tanker.
 * {@code maxHoldingDuration} defaults to 4 hours purely as a configurable placeholder;
 * it is not presented as a real dairy regulation.
 */
@Validated
@ConfigurationProperties(prefix = "milk")
public record MilkProperties(@NotNull Duration maxHoldingDuration) {
}
