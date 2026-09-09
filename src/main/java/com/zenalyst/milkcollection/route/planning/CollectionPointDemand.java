package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.geo.Coordinates;

import java.math.BigDecimal;

/**
 * What the optimizer needs to know about one collection point: where it is, how many farmers
 * deliver there (service time) and how much milk to expect (capacity).
 *
 * <p>A plain value object rather than the {@code CollectionPoint} entity, so planning code is a
 * pure function of its inputs - no lazy loading, no persistence context, unit-testable without
 * a database.
 */
public record CollectionPointDemand(
        Long collectionPointId,
        String collectionPointCode,
        Coordinates location,
        long farmerCount,
        BigDecimal expectedLitres) {
}
