package com.zenalyst.milkcollection.tanker.controller;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.tanker.dto.CreateTankerRequest;
import com.zenalyst.milkcollection.tanker.dto.TankerResponse;
import com.zenalyst.milkcollection.tanker.service.TankerService;
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

@Tag(name = "Tankers", description = "Master data: collection vehicles")
@RestController
@RequestMapping("/api/v1/tankers")
@RequiredArgsConstructor
public class TankerController {

    private final TankerService tankerService;

    @PostMapping
    @Operation(summary = "Register a tanker")
    public ResponseEntity<TankerResponse> create(@Valid @RequestBody CreateTankerRequest request) {
        TankerResponse created = tankerService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/tankers/{id}").build(created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List tankers")
    public PageResponse<TankerResponse> list(@PageableDefault(size = 50, sort = "tankerCode") Pageable pageable) {
        return tankerService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one tanker")
    public TankerResponse get(@PathVariable Long id) {
        return tankerService.get(id);
    }
}
