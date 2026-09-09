package com.zenalyst.milkcollection.route.planning;

import java.math.BigDecimal;

/** The only thing planning needs from a tanker. */
public record TankerCapacity(Long tankerId, String tankerCode, BigDecimal capacityLitres) {
}
