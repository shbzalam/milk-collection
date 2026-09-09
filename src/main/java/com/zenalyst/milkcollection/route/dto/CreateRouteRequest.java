package com.zenalyst.milkcollection.route.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRouteRequest(
        @Schema(example = "R-001") @NotBlank @Size(max = 32) String routeCode,
        @Schema(example = "Shirur morning loop") @NotBlank @Size(max = 120) String name,
        @Schema(description = "Optional; defaults to ACTIVE") EntityStatus status) {

    public EntityStatus statusOrDefault() {
        return status != null ? status : EntityStatus.ACTIVE;
    }
}
