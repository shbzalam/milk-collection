package com.zenalyst.milkcollection.village.controller;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.village.dto.CreateVillageRequest;
import com.zenalyst.milkcollection.village.dto.VillageResponse;
import com.zenalyst.milkcollection.village.service.VillageService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Villages", description = "Master data: villages the dairy collects from")
@RestController
@RequestMapping("/api/v1/villages")
@RequiredArgsConstructor
public class VillageController {

    private final VillageService villageService;

    @PostMapping
    @Operation(summary = "Register a village")
    public ResponseEntity<VillageResponse> create(@Valid @RequestBody CreateVillageRequest request) {
        VillageResponse created = villageService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/villages/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List villages")
    public PageResponse<VillageResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        return villageService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one village")
    public VillageResponse get(@PathVariable Long id) {
        return villageService.get(id);
    }
}
