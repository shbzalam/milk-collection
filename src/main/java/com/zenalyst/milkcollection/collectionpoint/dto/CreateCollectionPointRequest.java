package com.zenalyst.milkcollection.collectionpoint.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCollectionPointRequest(
        @Schema(example = "CP-001") @NotBlank @Size(max = 32) String code,
        @Schema(example = "Shirur Chowk") @NotBlank @Size(max = 120) String name,
        @NotNull Long villageId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Schema(description = "Optional; defaults to ACTIVE") EntityStatus status) {

    public EntityStatus statusOrDefault() {
        return status != null ? status : EntityStatus.ACTIVE;
    }
}
