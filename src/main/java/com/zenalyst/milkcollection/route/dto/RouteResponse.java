package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.route.entity.Route;

import java.time.Instant;

public record RouteResponse(
        Long id,
        String routeCode,
        String name,
        EntityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static RouteResponse from(Route route) {
        return new RouteResponse(route.getId(), route.getRouteCode(), route.getName(),
                route.getStatus(), route.getCreatedAt(), route.getUpdatedAt());
    }
}
