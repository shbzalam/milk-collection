package com.zenalyst.milkcollection.tracking.controller;

import com.zenalyst.milkcollection.tracking.dto.FarmerNextCollectionResponse;
import com.zenalyst.milkcollection.tracking.service.EtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tracking", description = "Operations: tanker positions")
@RestController
@RequestMapping("/api/v1/farmers/{farmerId}")
@RequiredArgsConstructor
public class FarmerCollectionStatusController {

    private final EtaService etaService;

    @GetMapping("/next-collection")
    @Operation(summary = "Where is my tanker?",
            description = """
                    Everything a farmer asks on the phone: their collection point, the tanker and
                    run serving it today, the run's status, the tanker's last reported position,
                    an arrival estimate and how many stops come first.

                    The estimate is geometric, from the last reported position at an assumed
                    average speed - `estimateBasis` says so on every response. Returns
                    `NO_RUN_SCHEDULED` rather than an error when no run covers the point today.
                    """)
    public FarmerNextCollectionResponse nextCollection(@PathVariable Long farmerId) {
        return etaService.nextCollectionFor(farmerId);
    }
}
