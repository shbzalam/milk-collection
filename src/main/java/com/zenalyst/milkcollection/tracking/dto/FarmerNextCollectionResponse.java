package com.zenalyst.milkcollection.tracking.dto;

import com.zenalyst.milkcollection.collection.dto.MilkCollectionSummary;
import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointSummary;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.farmer.dto.FarmerSummary;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The answer to "where is the tanker and when will it reach me".
 *
 * <p>Every estimate carries {@link #estimateBasis()} so the number is never mistaken for a
 * traffic-aware prediction.
 */
public record FarmerNextCollectionResponse(
        FarmerSummary farmer,
        CollectionPointSummary collectionPoint,
        FarmerCollectionState state,
        @Schema(example = "Tanker TNK-01 is 2 stops away, expected around 05:42")
        String message,
        @Schema(description = "The run serving this collection point today, if there is one")
        RunReference run,
        @Schema(description = "Last position reported by the tanker; null if it has not "
                + "reported since the run started")
        TankerLocationResponse tankerLocation,
        Instant estimatedArrivalTime,
        Duration estimatedTimeToArrival,
        @Schema(description = "How many stops the tanker still has to work before this one")
        Integer stopsBeforeYours,
        @Schema(description = "This farmer's collections on this run, once recorded")
        List<MilkCollectionSummary> collections,
        @Schema(example = "Estimated from the tanker's last reported position at an assumed "
                + "average speed; no live traffic or external routing data is used.")
        String estimateBasis) {

    /** Compact run reference - includes the registration number so the farmer can spot it. */
    public record RunReference(
            Long id,
            String runNumber,
            LocalDate runDate,
            Shift shift,
            RunStatus status,
            Instant plannedStartTime,
            Instant actualStartTime,
            String tankerCode,
            String tankerRegistrationNumber,
            String routeCode,
            int routeVersionNumber) {
    }
}
