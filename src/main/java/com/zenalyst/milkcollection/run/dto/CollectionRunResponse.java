package com.zenalyst.milkcollection.run.dto;

import com.zenalyst.milkcollection.chillingplant.dto.ChillingPlantResponse;
import com.zenalyst.milkcollection.collection.dto.RunLoadSummary;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.route.dto.RouteVersionSummary;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.tanker.dto.TankerResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CollectionRunResponse(
        Long id,
        String runNumber,
        @Schema(description = "The exact plan being driven; historical runs keep pointing at "
                + "the version they used even after the route is revised")
        RouteVersionSummary routeVersion,
        TankerResponse tanker,
        ChillingPlantResponse chillingPlant,
        LocalDate runDate,
        Shift shift,
        Instant plannedStartTime,
        Instant actualStartTime,
        Instant actualEndTime,
        RunStatus status,
        @Schema(description = "Optimistic-locking version of the run record")
        Long version,
        @Schema(description = "Litres on board versus tanker capacity")
        RunLoadSummary load,
        @Schema(description = "Omitted from listings")
        List<RunStopResponse> stops) {

    public static CollectionRunResponse withoutStops(CollectionRun run) {
        return build(run, null, null);
    }

    public static CollectionRunResponse withStops(CollectionRun run, RunLoadSummary load,
                                                  List<RunStopResponse> stops) {
        return build(run, load, stops);
    }

    private static CollectionRunResponse build(CollectionRun run, RunLoadSummary load,
                                               List<RunStopResponse> stops) {
        return new CollectionRunResponse(run.getId(), run.getRunNumber(),
                RouteVersionSummary.from(run.getRouteVersion()),
                TankerResponse.from(run.getTanker()),
                ChillingPlantResponse.from(run.getChillingPlant()),
                run.getRunDate(), run.getShift(), run.getPlannedStartTime(),
                run.getActualStartTime(), run.getActualEndTime(), run.getStatus(),
                run.getVersion(), load, stops);
    }
}
