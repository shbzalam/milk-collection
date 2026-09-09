package com.zenalyst.milkcollection.village.dto;

import com.zenalyst.milkcollection.village.entity.Village;

/** Compact reference used when a village is embedded in another resource. */
public record VillageSummary(Long id, String code, String name) {

    public static VillageSummary from(Village village) {
        return new VillageSummary(village.getId(), village.getCode(), village.getName());
    }
}
