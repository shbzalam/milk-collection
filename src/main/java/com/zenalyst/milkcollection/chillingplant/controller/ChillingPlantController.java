package com.zenalyst.milkcollection.chillingplant.controller;

import com.zenalyst.milkcollection.chillingplant.dto.ChillingPlantResponse;
import com.zenalyst.milkcollection.chillingplant.dto.CreateChillingPlantRequest;
import com.zenalyst.milkcollection.chillingplant.service.ChillingPlantService;
import com.zenalyst.milkcollection.common.dto.PageResponse;
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

@Tag(name = "Chilling plants", description = "Master data: where milk is delivered and chilled")
@RestController
@RequestMapping("/api/v1/chilling-plants")
@RequiredArgsConstructor
public class ChillingPlantController {

    private final ChillingPlantService chillingPlantService;

    @PostMapping
    @Operation(summary = "Register a chilling plant")
    public ResponseEntity<ChillingPlantResponse> create(
            @Valid @RequestBody CreateChillingPlantRequest request) {
        ChillingPlantResponse created = chillingPlantService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/chilling-plants/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List chilling plants")
    public PageResponse<ChillingPlantResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        return chillingPlantService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one chilling plant")
    public ChillingPlantResponse get(@PathVariable Long id) {
        return chillingPlantService.get(id);
    }
}
