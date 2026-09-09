package com.zenalyst.milkcollection.collectionpoint.dto;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;

/** Compact reference used when a collection point is embedded in another resource. */
public record CollectionPointSummary(
        Long id,
        String code,
        String name,
        double latitude,
        double longitude) {

    public static CollectionPointSummary from(CollectionPoint point) {
        return new CollectionPointSummary(point.getId(), point.getCode(), point.getName(),
                point.getLatitude(), point.getLongitude());
    }
}
