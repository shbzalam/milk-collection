package com.zenalyst.milkcollection.run.controller;

import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.run.dto.CollectionRunResponse;
import com.zenalyst.milkcollection.run.dto.CreateRunRequest;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.service.CollectionRunService;
import com.zenalyst.milkcollection.run.service.RunQueryService;
import com.zenalyst.milkcollection.run.service.RunExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@Tag(name = "Runs", description = "Operations: the twice-a-day execution of a published route")
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class CollectionRunController {

    private final CollectionRunService collectionRunService;
    private final RunQueryService runQueryService;
    private final RunExecutionService runExecutionService;

    @PostMapping
    @Operation(summary = "Plan a run",
            description = """
                    Binds a published route version to a tanker for one date and shift, projects
                    the timetable, and rejects the run if the expected volume exceeds the tanker
                    or the milk could not reach the plant within the holding limit.
                    """)
    public ResponseEntity<CollectionRunResponse> create(@Valid @RequestBody CreateRunRequest request) {
        CollectionRunResponse created = collectionRunService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/runs/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List runs, filtered for a dispatch view")
    public PageResponse<CollectionRunResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runDate,
            @RequestParam(required = false) Shift shift,
            @RequestParam(required = false) RunStatus status,
            @RequestParam(required = false) Long tankerId,
            @PageableDefault(size = 50, sort = {"runDate", "plannedStartTime"},
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return runQueryService.list(runDate, shift, status, tankerId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one run with its stops")
    public CollectionRunResponse get(@PathVariable Long id) {
        return runQueryService.getDetail(id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a run (the tanker leaves the plant)")
    public CollectionRunResponse start(@PathVariable Long id) {
        return runExecutionService.start(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a planned run",
            description = "Only a PLANNED run can be cancelled; once milk is on board the run "
                    + "must be closed out instead. Cancelling frees the tanker's slot.")
    public CollectionRunResponse cancel(@PathVariable Long id) {
        return runExecutionService.cancel(id);
    }

    @PostMapping("/{id}/stops/{stopId}/arrive")
    @Operation(summary = "Record arrival at a stop")
    public CollectionRunResponse arrive(@PathVariable Long id, @PathVariable Long stopId) {
        return runExecutionService.arriveAtStop(id, stopId);
    }

    @PostMapping("/{id}/stops/{stopId}/complete")
    @Operation(summary = "Record departure from a stop")
    public CollectionRunResponse completeStop(@PathVariable Long id, @PathVariable Long stopId) {
        return runExecutionService.completeStop(id, stopId);
    }

    @PostMapping("/{id}/stops/{stopId}/skip")
    @Operation(summary = "Skip a stop that was never reached")
    public CollectionRunResponse skipStop(@PathVariable Long id, @PathVariable Long stopId) {
        return runExecutionService.skipStop(id, stopId);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Close out a run at the chilling plant",
            description = "Every stop must be completed or skipped first.")
    public CollectionRunResponse complete(@PathVariable Long id) {
        return runExecutionService.complete(id);
    }
}
