package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.route.entity.RouteVersion;
import com.zenalyst.milkcollection.route.entity.RouteVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record RouteVersionResponse(
        Long id,
        Long routeId,
        String routeCode,
        int versionNumber,
        RouteVersionStatus status,
        Instant createdAt,
        Instant publishedAt,
        @Schema(description = "Omitted from listings, where loading every version's stops would "
                + "be an N+1 query")
        List<RouteStopResponse> stops) {

    public static RouteVersionResponse withoutStops(RouteVersion version) {
        return build(version, null);
    }

    public static RouteVersionResponse withStops(RouteVersion version,
                                                 List<RouteStopResponse> stops) {
        return build(version, stops);
    }

    private static RouteVersionResponse build(RouteVersion version, List<RouteStopResponse> stops) {
        return new RouteVersionResponse(version.getId(), version.getRoute().getId(),
                version.getRoute().getRouteCode(), version.getVersionNumber(), version.getStatus(),
                version.getCreatedAt(), version.getPublishedAt(), stops);
    }
}
