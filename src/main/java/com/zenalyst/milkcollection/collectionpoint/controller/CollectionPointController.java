package com.zenalyst.milkcollection.collectionpoint.controller;

import com.zenalyst.milkcollection.collectionpoint.dto.CollectionPointResponse;
import com.zenalyst.milkcollection.collectionpoint.dto.CreateCollectionPointRequest;
import com.zenalyst.milkcollection.collectionpoint.service.CollectionPointService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Collection points", description = "Master data: physical places where tankers stop")
@RestController
@RequestMapping("/api/v1/collection-points")
@RequiredArgsConstructor
public class CollectionPointController {

    private final CollectionPointService collectionPointService;

    @PostMapping
    @Operation(summary = "Register a collection point")
    public ResponseEntity<CollectionPointResponse> create(
            @Valid @RequestBody CreateCollectionPointRequest request) {
        CollectionPointResponse created = collectionPointService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/collection-points/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List collection points, optionally filtered by village")
    public PageResponse<CollectionPointResponse> list(
            @RequestParam(required = false) Long villageId,
            @PageableDefault(size = 50, sort = "code") Pageable pageable) {
        return collectionPointService.list(villageId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one collection point, including how many farmers it serves")
    public CollectionPointResponse get(@PathVariable Long id) {
        return collectionPointService.get(id);
    }
}
