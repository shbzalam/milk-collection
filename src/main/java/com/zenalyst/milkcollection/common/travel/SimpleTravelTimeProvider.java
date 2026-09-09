package com.zenalyst.milkcollection.common.travel;

import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.config.RoutingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Deterministic travel-time estimate:
 *
 * <pre>
 *   roadDistanceKm = haversineKm * roadWindingFactor
 *   duration       = roadDistanceKm / averageSpeedKmph
 * </pre>
 *
 * <p>MVP ASSUMPTION. This is a documented geometric approximation, not a traffic-aware
 * estimate. It uses no external map or routing API, has no network dependency, and returns
 * the same answer for the same inputs - which is what makes route feasibility checks and
 * ETA calculations testable.
 */
@Component
@RequiredArgsConstructor
public class SimpleTravelTimeProvider implements TravelTimeProvider {

    private static final int SECONDS_PER_HOUR = 3600;

    private final RoutingProperties routingProperties;

    @Override
    public Duration estimateTravelTime(Coordinates from, Coordinates to) {
        double roadDistanceKm = from.haversineDistanceKm(to) * routingProperties.roadWindingFactor();
        double hours = roadDistanceKm / routingProperties.averageSpeedKmph();
        return Duration.ofSeconds(Math.round(hours * SECONDS_PER_HOUR));
    }
}
