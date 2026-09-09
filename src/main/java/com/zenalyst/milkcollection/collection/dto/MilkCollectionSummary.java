package com.zenalyst.milkcollection.collection.dto;

import com.zenalyst.milkcollection.collection.entity.MilkCollection;
import com.zenalyst.milkcollection.collection.entity.MilkCollectionStatus;
import com.zenalyst.milkcollection.farmer.dto.FarmerSummary;

import java.math.BigDecimal;
import java.time.Instant;

/** One collection as it appears nested under a run stop. */
public record MilkCollectionSummary(
        Long id,
        FarmerSummary farmer,
        BigDecimal quantityLitres,
        Instant collectedAt,
        MilkCollectionStatus status) {

    public static MilkCollectionSummary from(MilkCollection collection) {
        return new MilkCollectionSummary(collection.getId(),
                FarmerSummary.from(collection.getFarmer()), collection.getQuantityLitres(),
                collection.getCollectedAt(), collection.getStatus());
    }
}
