package com.zenalyst.milkcollection.collection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "There is deliberately no collectedAt field: the collection time is "
        + "assigned server-side, because a client able to choose it could backdate milk past "
        + "the holding-time check.")
public record RecordMilkCollectionRequest(
        @Schema(description = "Must be a farmer assigned to this stop's collection point")
        @NotNull Long farmerId,
        @Schema(example = "50.00")
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal quantityLitres) {
}
