package com.zenalyst.milkcollection.tracking.controller;

import com.zenalyst.milkcollection.tracking.dto.TankerLocationRequest;
import com.zenalyst.milkcollection.tracking.dto.TankerLocationResponse;
import com.zenalyst.milkcollection.tracking.service.TankerTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tracking", description = "Operations: tanker positions")
@RestController
@RequestMapping("/api/v1/tankers/{tankerId}/location")
@RequiredArgsConstructor
public class TankerTrackingController {

    private final TankerTrackingService tankerTrackingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a tanker position",
            description = "Appended to the tanker's history and attached automatically to the "
                    + "run it is currently working, if any.")
    public TankerLocationResponse record(@PathVariable Long tankerId,
                                         @Valid @RequestBody TankerLocationRequest request) {
        return tankerTrackingService.recordLocation(tankerId, request);
    }

    @GetMapping
    @Operation(summary = "Latest reported position of a tanker",
            description = "Returns RESOURCE_NOT_FOUND if the tanker has never reported.")
    public TankerLocationResponse latest(@PathVariable Long tankerId) {
        return tankerTrackingService.latestLocation(tankerId);
    }
}
