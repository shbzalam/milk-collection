package com.zenalyst.milkcollection.common.travel;

import com.zenalyst.milkcollection.common.geo.Coordinates;

import java.time.Duration;

/**
 * Estimates driving time between two points.
 *
 * <p>Deliberately a one-method interface: the MVP ships a deterministic geometric
 * implementation, and swapping in a real routing service (OSRM, Google Directions,
 * a cached distance matrix) is a single-bean change with no impact on planning or ETA code.
 */
public interface TravelTimeProvider {

    Duration estimateTravelTime(Coordinates from, Coordinates to);
}
