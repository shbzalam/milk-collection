package com.zenalyst.milkcollection.tanker.dto;

import com.zenalyst.milkcollection.tanker.entity.TankerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTankerRequest(
        @Schema(example = "TNK-01") @NotBlank @Size(max = 32) String tankerCode,
        @Schema(example = "MH12AB1234") @NotBlank @Size(max = 32) String registrationNumber,
        @Schema(example = "5000.00", description = "Hard capacity limit enforced on every collection")
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal capacityLitres,
        @Schema(description = "Optional; defaults to ACTIVE") TankerStatus status) {

    public TankerStatus statusOrDefault() {
        return status != null ? status : TankerStatus.ACTIVE;
    }
}
