package com.zenalyst.milkcollection.run.dto;

import com.zenalyst.milkcollection.collection.dto.MilkCollectionSummary;
import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RunStopResponse(
        Long id,
        int sequenceNumber,
        CollectionPointSummary collectionPoint,
        @Schema(description = "Projected at run creation from the travel- and service-time model")
        Instant plannedArrivalTime,
        Instant actualArrivalTime,
        Instant actualDepartureTime,
        RunStopStatus status,
        BigDecimal collectedLitres,
        @Schema(description = "One entry per farmer served here - a stop shared by two farmers "
                + "carries two collections and remains a single physical stop")
        List<MilkCollectionSummary> collections) {

    public static RunStopResponse of(RunStop stop, List<MilkCollectionSummary> collections) {
        return new RunStopResponse(stop.getId(), stop.getSequenceNumber(),
                CollectionPointSummary.from(stop.getRouteStop().getCollectionPoint()),
                stop.getPlannedArrivalTime(), stop.getActualArrivalTime(),
                stop.getActualDepartureTime(), stop.getStatus(),
                collections.stream().map(MilkCollectionSummary::quantityLitres)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                collections);
    }
}
