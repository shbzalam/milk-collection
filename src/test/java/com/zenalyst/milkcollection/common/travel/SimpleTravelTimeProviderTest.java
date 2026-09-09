package com.zenalyst.milkcollection.common.travel;

import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.config.RoutingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleTravelTimeProviderTest {

    private final SimpleTravelTimeProvider provider = new SimpleTravelTimeProvider(
            new RoutingProperties(30.0, 1.3, Duration.ofMinutes(3), Duration.ofMinutes(2)));

    @Test
    @DisplayName("applies the winding factor and average speed to straight-line distance")
    void appliesWindingFactorAndSpeed() {
        Coordinates from = new Coordinates(18.5000, 73.8000);
        Coordinates to = new Coordinates(18.5900, 73.8000);   // ~10 km due north

        double straightLineKm = from.haversineDistanceKm(to);
        double expectedSeconds = straightLineKm * 1.3 / 30.0 * 3600;

        Duration estimate = provider.estimateTravelTime(from, to);

        assertThat(estimate.getSeconds()).isCloseTo(Math.round(expectedSeconds),
                org.assertj.core.data.Offset.offset(1L));
        // 10 km of road at 30 km/h with a 1.3 factor is a little over 25 minutes.
        assertThat(estimate).isBetween(Duration.ofMinutes(25), Duration.ofMinutes(27));
    }

    @Test
    void sameLocationTakesNoTime() {
        Coordinates point = new Coordinates(18.5, 73.8);
        assertThat(provider.estimateTravelTime(point, point)).isZero();
    }

    @Test
    @DisplayName("is deterministic - the same inputs always produce the same estimate")
    void isDeterministic() {
        Coordinates from = new Coordinates(18.50, 73.80);
        Coordinates to = new Coordinates(18.70, 74.10);

        assertThat(provider.estimateTravelTime(from, to))
                .isEqualTo(provider.estimateTravelTime(from, to));
    }
}
