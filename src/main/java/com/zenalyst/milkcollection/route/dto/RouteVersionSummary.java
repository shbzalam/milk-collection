package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.route.entity.RouteVersion;
import com.zenalyst.milkcollection.route.entity.RouteVersionStatus;

/** Compact reference used when a run reports the exact plan it is executing. */
public record RouteVersionSummary(
        Long id,
        Long routeId,
        String routeCode,
        String routeName,
        int versionNumber,
        RouteVersionStatus status) {

    public static RouteVersionSummary from(RouteVersion version) {
        return new RouteVersionSummary(version.getId(), version.getRoute().getId(),
                version.getRoute().getRouteCode(), version.getRoute().getName(),
                version.getVersionNumber(), version.getStatus());
    }
}
