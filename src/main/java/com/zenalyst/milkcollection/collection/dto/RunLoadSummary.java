package com.zenalyst.milkcollection.collection.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Schema(description = "How full the tanker is on this run, computed server-side from the "
        + "recorded collections - never trusted from a client")
public record RunLoadSummary(
        BigDecimal collectedLitres,
        BigDecimal tankerCapacityLitres,
        BigDecimal remainingCapacityLitres,
        BigDecimal capacityUtilisationPercent) {

    public static RunLoadSummary of(BigDecimal collectedLitres, BigDecimal tankerCapacityLitres) {
        return new RunLoadSummary(
                collectedLitres,
                tankerCapacityLitres,
                tankerCapacityLitres.subtract(collectedLitres),
                collectedLitres.multiply(BigDecimal.valueOf(100))
                        .divide(tankerCapacityLitres, 1, RoundingMode.HALF_UP));
    }
}
