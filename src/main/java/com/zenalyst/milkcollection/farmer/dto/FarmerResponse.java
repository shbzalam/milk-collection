package com.zenalyst.milkcollection.farmer.dto;

import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import com.zenalyst.milkcollection.village.dto.VillageSummary;

import java.math.BigDecimal;
import java.time.Instant;

public record FarmerResponse(
        Long id,
        String farmerCode,
        String name,
        String phone,
        VillageSummary village,
        CollectionPointSummary collectionPoint,
        BigDecimal expectedMorningQuantityLitres,
        BigDecimal expectedEveningQuantityLitres,
        EntityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static FarmerResponse from(Farmer farmer) {
        return new FarmerResponse(farmer.getId(), farmer.getFarmerCode(), farmer.getName(),
                farmer.getPhone(), VillageSummary.from(farmer.getVillage()),
                CollectionPointSummary.from(farmer.getCollectionPoint()),
                farmer.getExpectedMorningQuantityLitres(), farmer.getExpectedEveningQuantityLitres(),
                farmer.getStatus(), farmer.getCreatedAt(), farmer.getUpdatedAt());
    }
}
