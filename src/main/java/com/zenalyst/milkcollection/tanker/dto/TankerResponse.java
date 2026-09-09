package com.zenalyst.milkcollection.tanker.dto;

import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.entity.TankerStatus;

import java.math.BigDecimal;

public record TankerResponse(
        Long id,
        String tankerCode,
        String registrationNumber,
        BigDecimal capacityLitres,
        TankerStatus status) {

    public static TankerResponse from(Tanker tanker) {
        return new TankerResponse(tanker.getId(), tanker.getTankerCode(),
                tanker.getRegistrationNumber(), tanker.getCapacityLitres(), tanker.getStatus());
    }
}
