package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.route.entity.RouteStop;

import java.time.LocalTime;

public record RouteStopResponse(
        Long id,
        int sequenceNumber,
        CollectionPointSummary collectionPoint,
        LocalTime plannedArrivalTime,
        LocalTime plannedDepartureTime) {

    public static RouteStopResponse from(RouteStop stop) {
        return new RouteStopResponse(stop.getId(), stop.getSequenceNumber(),
                CollectionPointSummary.from(stop.getCollectionPoint()),
                stop.getPlannedArrivalTime(), stop.getPlannedDepartureTime());
    }
}
