package com.zenalyst.milkcollection.tracking.dto;

import com.zenalyst.milkcollection.tracking.entity.TankerLocation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record TankerLocationResponse(
        Long id,
        Long tankerId,
        String tankerCode,
        @Schema(description = "The run this position was reported during, if any")
        Long collectionRunId,
        String runNumber,
        double latitude,
        double longitude,
        Instant recordedAt) {

    public static TankerLocationResponse from(TankerLocation location) {
        return new TankerLocationResponse(
                location.getId(),
                location.getTanker().getId(),
                location.getTanker().getTankerCode(),
                location.getCollectionRun() != null ? location.getCollectionRun().getId() : null,
                location.getCollectionRun() != null ? location.getCollectionRun().getRunNumber() : null,
                location.getLatitude(),
                location.getLongitude(),
                location.getRecordedAt());
    }
}
