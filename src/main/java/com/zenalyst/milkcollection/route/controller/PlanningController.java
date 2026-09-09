package com.zenalyst.milkcollection.route.controller;

import com.zenalyst.milkcollection.route.dto.OptimizePlanResponse;
import com.zenalyst.milkcollection.route.dto.OptimizeRoutesRequest;
import com.zenalyst.milkcollection.route.service.PlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Planning", description = "Planning: propose routes for a fleet and a shift")
@RestController
@RequestMapping("/api/v1/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;

    @PostMapping("/optimize")
    @Operation(summary = "Propose routes for a shift",
            description = """
                    Runs the greedy optimizer over the requested collection points and tankers.
                    The response is a proposal and persists nothing; anything the fleet cannot
                    cover is returned in `unassigned` with the reason.
                    """)
    public OptimizePlanResponse optimize(@Valid @RequestBody OptimizeRoutesRequest request) {
        return planningService.optimize(request);
    }
}
