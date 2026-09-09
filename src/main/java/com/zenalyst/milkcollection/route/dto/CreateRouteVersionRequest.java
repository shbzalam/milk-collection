package com.zenalyst.milkcollection.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRouteVersionRequest(
        @Schema(description = "Optional: copy the stops of an existing version of the same route "
                + "into the new draft. Revising a published route is the common case, and "
                + "retyping every stop would invite mistakes.")
        Long copyFromVersionId) {
}
