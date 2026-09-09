package com.zenalyst.milkcollection.village.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.village.entity.Village;

import java.time.Instant;

public record VillageResponse(
        Long id,
        String code,
        String name,
        double latitude,
        double longitude,
        EntityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static VillageResponse from(Village village) {
        return new VillageResponse(village.getId(), village.getCode(), village.getName(),
                village.getLatitude(), village.getLongitude(), village.getStatus(),
                village.getCreatedAt(), village.getUpdatedAt());
    }
}
