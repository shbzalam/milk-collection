package com.zenalyst.milkcollection.farmer.dto;

import com.zenalyst.milkcollection.farmer.entity.Farmer;

/** Compact reference used when a farmer is embedded in another resource. */
public record FarmerSummary(Long id, String farmerCode, String name) {

    public static FarmerSummary from(Farmer farmer) {
        return new FarmerSummary(farmer.getId(), farmer.getFarmerCode(), farmer.getName());
    }
}
