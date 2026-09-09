package com.zenalyst.milkcollection.tracking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "There is no recordedAt field: the MVP timestamps a ping on arrival. "
        + "Accepting a device timestamp would mean handling buffered offline positions and "
        + "out-of-order pings, which is out of scope.")
public record TankerLocationRequest(
        @Schema(example = "18.5512") @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @Schema(example = "73.8012") @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
