package com.zenalyst.milkcollection.chillingplant.dto;

import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import com.zenalyst.milkcollection.common.domain.EntityStatus;

public record ChillingPlantResponse(
        Long id,
        String code,
        String name,
        double latitude,
        double longitude,
        EntityStatus status) {

    public static ChillingPlantResponse from(ChillingPlant plant) {
        return new ChillingPlantResponse(plant.getId(), plant.getCode(), plant.getName(),
                plant.getLatitude(), plant.getLongitude(), plant.getStatus());
    }
}
