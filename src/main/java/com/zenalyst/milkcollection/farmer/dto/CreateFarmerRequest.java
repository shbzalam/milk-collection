package com.zenalyst.milkcollection.farmer.dto;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateFarmerRequest(
        @Schema(example = "F-0001") @NotBlank @Size(max = 32) String farmerCode,
        @Schema(example = "Ramesh Pawar") @NotBlank @Size(max = 120) String name,
        @Schema(example = "9876543210") @NotBlank @Size(max = 20) String phone,
        @Schema(description = "Where the farmer lives") @NotNull Long villageId,
        @Schema(description = "Where the farmer's milk is picked up; may be shared with other farmers")
        @NotNull Long collectionPointId,
        @Schema(example = "50.00", description = "Planning expectation for the morning shift")
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal expectedMorningQuantityLitres,
        @Schema(example = "40.00", description = "Planning expectation for the evening shift")
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal expectedEveningQuantityLitres,
        @Schema(description = "Optional; defaults to ACTIVE") EntityStatus status) {

    public EntityStatus statusOrDefault() {
        return status != null ? status : EntityStatus.ACTIVE;
    }
}
