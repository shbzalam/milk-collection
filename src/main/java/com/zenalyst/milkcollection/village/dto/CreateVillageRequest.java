package com.zenalyst.milkcollection.village.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVillageRequest(
        @Schema(example = "V-001") @NotBlank @Size(max = 32) String code,
        @Schema(example = "Shirur") @NotBlank @Size(max = 120) String name,
        @Schema(example = "18.8237") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(example = "74.3732") @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Schema(description = "Optional; defaults to ACTIVE") EntityStatus status) {

    public EntityStatus statusOrDefault() {
        return status != null ? status : EntityStatus.ACTIVE;
    }
}
