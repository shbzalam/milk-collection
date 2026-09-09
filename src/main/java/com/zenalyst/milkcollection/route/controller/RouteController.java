package com.zenalyst.milkcollection.route.controller;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.route.dto.AddRouteStopsRequest;
import com.zenalyst.milkcollection.route.dto.CreateRouteRequest;
import com.zenalyst.milkcollection.route.dto.CreateRouteVersionRequest;
import com.zenalyst.milkcollection.route.dto.RouteResponse;
import com.zenalyst.milkcollection.route.dto.RouteVersionResponse;
import com.zenalyst.milkcollection.route.service.RouteService;
import com.zenalyst.milkcollection.route.service.RouteVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Tag(name = "Routes", description = "Planning: routes and their immutable versions")
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteVersionService routeVersionService;

    @PostMapping
    @Operation(summary = "Create a route",
            description = "Creates the logical route only. Stops live on a route version.")
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody CreateRouteRequest request) {
        RouteResponse created = routeService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/routes/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List routes")
    public PageResponse<RouteResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        return routeService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one route")
    public RouteResponse get(@PathVariable Long id) {
        return routeService.get(id);
    }

    @PostMapping("/{routeId}/versions")
    @Operation(summary = "Start a new draft version",
            description = "Optionally copies the stops of an existing version. Published versions "
                    + "are never edited; revising a route always means a new version.")
    public ResponseEntity<RouteVersionResponse> createVersion(
            @PathVariable Long routeId,
            @RequestBody(required = false) CreateRouteVersionRequest request) {
        RouteVersionResponse created = routeVersionService.createVersion(routeId, request);
        return ResponseEntity.created(UriComponentsBuilder
                        .fromPath("/api/v1/routes/{routeId}/versions/{versionId}")
                        .build(routeId, created.id()))
                .body(created);
    }

    @GetMapping("/{routeId}/versions")
    @Operation(summary = "List versions of a route, newest first")
    public List<RouteVersionResponse> listVersions(@PathVariable Long routeId) {
        return routeVersionService.listVersions(routeId);
    }

    @GetMapping("/{routeId}/versions/{versionId}")
    @Operation(summary = "Get one route version including its stops")
    public RouteVersionResponse getVersion(@PathVariable Long routeId, @PathVariable Long versionId) {
        return routeVersionService.getVersion(routeId, versionId);
    }

    @PostMapping("/{routeId}/versions/{versionId}/stops")
    @Operation(summary = "Add stops to a draft version",
            description = "Rejected with ROUTE_VERSION_IMMUTABLE once the version is published.")
    public RouteVersionResponse addStops(@PathVariable Long routeId, @PathVariable Long versionId,
                                         @Valid @RequestBody AddRouteStopsRequest request) {
        return routeVersionService.addStops(routeId, versionId, request);
    }

    @PostMapping("/{routeId}/versions/{versionId}/publish")
    @Operation(summary = "Publish a draft version",
            description = "Validates the stop list and makes this version the plan of record, "
                    + "archiving the previously published version.")
    public RouteVersionResponse publish(@PathVariable Long routeId, @PathVariable Long versionId) {
        return routeVersionService.publish(routeId, versionId);
    }
}
