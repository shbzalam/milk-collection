package com.zenalyst.milkcollection.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Stops are added in a batch: a route is designed as a whole, and one "
        + "request per stop would leave a draft half-built if the caller stopped early.")
public record AddRouteStopsRequest(@NotEmpty @Valid List<RouteStopRequest> stops) {
}
