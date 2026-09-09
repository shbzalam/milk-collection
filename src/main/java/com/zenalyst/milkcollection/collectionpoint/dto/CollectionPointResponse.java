package com.zenalyst.milkcollection.collectionpoint.dto;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.village.dto.VillageSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CollectionPointResponse(
        Long id,
        String code,
        String name,
        VillageSummary village,
        double latitude,
        double longitude,
        EntityStatus status,
        @Schema(description = "Number of farmers assigned here; returned on the single-resource "
                + "endpoint only, because it costs an extra aggregate query")
        Long farmerCount,
        Instant createdAt,
        Instant updatedAt) {

    public static CollectionPointResponse from(CollectionPoint point) {
        return from(point, null);
    }

    public static CollectionPointResponse from(CollectionPoint point, Long farmerCount) {
        return new CollectionPointResponse(point.getId(), point.getCode(), point.getName(),
                VillageSummary.from(point.getVillage()), point.getLatitude(), point.getLongitude(),
                point.getStatus(), farmerCount, point.getCreatedAt(), point.getUpdatedAt());
    }
}
