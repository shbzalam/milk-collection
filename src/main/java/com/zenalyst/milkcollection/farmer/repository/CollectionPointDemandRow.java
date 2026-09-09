package com.zenalyst.milkcollection.farmer.repository;

import java.math.BigDecimal;

/**
 * Projection for the per-collection-point demand aggregate. Interface-based projection so
 * Spring Data can materialise it directly from the grouped query.
 */
public interface CollectionPointDemandRow {

    Long getCollectionPointId();

    long getFarmerCount();

    BigDecimal getExpectedMorningLitres();

    BigDecimal getExpectedEveningLitres();
}
