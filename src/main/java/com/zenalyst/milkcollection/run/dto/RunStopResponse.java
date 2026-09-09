package com.zenalyst.milkcollection.run.dto;

import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record RunStopResponse(
        Long id,
        int sequenceNumber,
        CollectionPointSummary collectionPoint,
        @Schema(description = "Projected at run creation from the travel- and service-time model")
        Instant plannedArrivalTime,
        Instant actualArrivalTime,
        Instant actualDepartureTime,
        RunStopStatus status) {

    public static RunStopResponse from(RunStop stop) {
        return new RunStopResponse(stop.getId(), stop.getSequenceNumber(),
                CollectionPointSummary.from(stop.getRouteStop().getCollectionPoint()),
                stop.getPlannedArrivalTime(), stop.getActualArrivalTime(),
                stop.getActualDepartureTime(), stop.getStatus());
    }
}
