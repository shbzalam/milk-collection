package com.zenalyst.milkcollection.farmer.controller;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.farmer.dto.CreateFarmerRequest;
import com.zenalyst.milkcollection.farmer.dto.FarmerResponse;
import com.zenalyst.milkcollection.farmer.service.FarmerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Farmers", description = "Master data: milk producers and their collection point")
@RestController
@RequestMapping("/api/v1/farmers")
@RequiredArgsConstructor
public class FarmerController {

    private final FarmerService farmerService;

    @PostMapping
    @Operation(summary = "Register a farmer")
    public ResponseEntity<FarmerResponse> create(@Valid @RequestBody CreateFarmerRequest request) {
        FarmerResponse created = farmerService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/farmers/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List farmers",
            description = "Filter by village, by collection point, or by phone number - "
                    + "the last one supports the call-centre case of an incoming farmer call.")
    public PageResponse<FarmerResponse> list(
            @RequestParam(required = false) Long villageId,
            @RequestParam(required = false) Long collectionPointId,
            @RequestParam(required = false) String phone,
            @PageableDefault(size = 50) Pageable pageable) {
        return farmerService.list(villageId, collectionPointId, phone, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one farmer")
    public FarmerResponse get(@PathVariable Long id) {
        return farmerService.get(id);
    }
}
