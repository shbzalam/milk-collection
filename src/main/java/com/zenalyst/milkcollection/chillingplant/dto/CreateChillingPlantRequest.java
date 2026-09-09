package com.zenalyst.milkcollection.chillingplant.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChillingPlantRequest(
        @Schema(example = "CP-PLANT-01") @NotBlank @Size(max = 32) String code,
        @Schema(example = "Shirur Chilling Centre") @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Schema(description = "Optional; defaults to ACTIVE") EntityStatus status) {

    public EntityStatus statusOrDefault() {
        return status != null ? status : EntityStatus.ACTIVE;
    }
}
