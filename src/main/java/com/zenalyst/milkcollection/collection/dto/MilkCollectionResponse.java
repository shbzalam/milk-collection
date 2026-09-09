package com.zenalyst.milkcollection.collection.dto;

import com.zenalyst.milkcollection.collection.entity.MilkCollection;
import com.zenalyst.milkcollection.collection.entity.MilkCollectionStatus;
import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.farmer.dto.FarmerSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record MilkCollectionResponse(
        Long id,
        Long runId,
        String runNumber,
        Long runStopId,
        int stopSequenceNumber,
        CollectionPointSummary collectionPoint,
        FarmerSummary farmer,
        BigDecimal quantityLitres,
        Instant collectedAt,
        MilkCollectionStatus status,
        @Schema(description = "Tanker load after this collection")
        RunLoadSummary load,
        @Schema(description = "Projected arrival at the chilling plant over the remaining stops, "
                + "using the configured travel-time model")
        Instant projectedPlantArrival,
        @Schema(description = "Age of the oldest milk in the tanker when it reaches the plant, "
                + "on the current projection")
        Duration projectedHoldingDuration,
        @Schema(description = "How much of the holding limit is still unused")
        Duration holdingTimeRemaining) {

    public static MilkCollectionResponse of(MilkCollection collection, RunLoadSummary load,
                                            Instant projectedPlantArrival,
                                            Duration projectedHoldingDuration,
                                            Duration holdingTimeRemaining) {
        var runStop = collection.getRunStop();
        return new MilkCollectionResponse(
                collection.getId(),
                runStop.getCollectionRun().getId(),
                runStop.getCollectionRun().getRunNumber(),
                runStop.getId(),
                runStop.getSequenceNumber(),
                CollectionPointSummary.from(runStop.getRouteStop().getCollectionPoint()),
                FarmerSummary.from(collection.getFarmer()),
                collection.getQuantityLitres(),
                collection.getCollectedAt(),
                collection.getStatus(),
                load,
                projectedPlantArrival,
                projectedHoldingDuration,
                holdingTimeRemaining);
    }
}
