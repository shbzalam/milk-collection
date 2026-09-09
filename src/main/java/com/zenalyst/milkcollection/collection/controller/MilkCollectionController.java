package com.zenalyst.milkcollection.collection.controller;

import com.zenalyst.milkcollection.collection.dto.MilkCollectionResponse;
import com.zenalyst.milkcollection.collection.dto.MilkCollectionSummary;
import com.zenalyst.milkcollection.collection.dto.RecordMilkCollectionRequest;
import com.zenalyst.milkcollection.collection.service.MilkCollectionService;
import com.zenalyst.milkcollection.run.service.RunQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Tag(name = "Milk collection", description = "Operations: recording milk taken from farmers")
@RestController
@RequestMapping("/api/v1/runs/{runId}")
@RequiredArgsConstructor
public class MilkCollectionController {

    private final MilkCollectionService milkCollectionService;
    private final RunQueryService runQueryService;

    @PostMapping("/stops/{stopId}/collections")
    @Operation(summary = "Record milk from one farmer at one stop",
            description = """
                    Rejected with:
                    * `INVALID_RUN_STATE` / `INVALID_STOP_STATE` - the tanker is not standing here
                    * `FARMER_NOT_ASSIGNED_TO_STOP` - the farmer belongs to another collection point
                    * `DUPLICATE_COLLECTION` - this farmer was already recorded at this stop
                    * `TANKER_CAPACITY_EXCEEDED` - the load would not fit
                    * `MILK_HOLDING_TIME_EXCEEDED` - the load could no longer reach the plant in time

                    A collection point serving two farmers takes two of these calls against the
                    same stop.
                    """)
    public ResponseEntity<MilkCollectionResponse> record(
            @PathVariable Long runId, @PathVariable Long stopId,
            @Valid @RequestBody RecordMilkCollectionRequest request) {
        MilkCollectionResponse created = milkCollectionService.record(runId, stopId, request);
        return ResponseEntity.created(UriComponentsBuilder
                        .fromPath("/api/v1/runs/{runId}/stops/{stopId}/collections/{id}")
                        .build(runId, stopId, created.id()))
                .body(created);
    }

    @GetMapping("/stops/{stopId}/collections")
    @Operation(summary = "List the collections recorded at one stop")
    public List<MilkCollectionSummary> listForStop(@PathVariable Long runId,
                                                   @PathVariable Long stopId) {
        return milkCollectionService.listForStop(runId, stopId);
    }

    @GetMapping("/collections")
    @Operation(summary = "List every collection recorded on a run")
    public List<MilkCollectionSummary> listForRun(@PathVariable Long runId) {
        return runQueryService.listCollections(runId);
    }
}
