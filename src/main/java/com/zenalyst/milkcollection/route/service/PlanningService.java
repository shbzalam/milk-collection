package com.zenalyst.milkcollection.route.service;

import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import com.zenalyst.milkcollection.chillingplant.service.ChillingPlantService;
import com.zenalyst.milkcollection.config.MilkProperties;
import com.zenalyst.milkcollection.config.RoutingProperties;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.route.dto.OptimizePlanResponse;
import com.zenalyst.milkcollection.route.dto.OptimizeRoutesRequest;
import com.zenalyst.milkcollection.route.optimizer.OptimizedRoute;
import com.zenalyst.milkcollection.route.optimizer.RouteOptimizer;
import com.zenalyst.milkcollection.route.planning.CollectionPointDemand;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.PlanningConstraintsFactory;
import com.zenalyst.milkcollection.route.planning.PlanningDataLoader;
import com.zenalyst.milkcollection.route.planning.TankerCapacity;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.entity.TankerStatus;
import com.zenalyst.milkcollection.tanker.repository.TankerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point for the planning side: assembles the read model and the constraints, runs the
 * optimizer, and returns the proposal together with the assumptions it was built on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final ChillingPlantService chillingPlantService;
    private final TankerRepository tankerRepository;
    private final PlanningDataLoader planningDataLoader;
    private final PlanningConstraintsFactory constraintsFactory;
    private final RouteOptimizer routeOptimizer;
    private final MilkProperties milkProperties;
    private final RoutingProperties routingProperties;

    @Transactional(readOnly = true)
    public OptimizePlanResponse optimize(OptimizeRoutesRequest request) {
        ChillingPlant plant = chillingPlantService.require(request.chillingPlantId());
        if (!plant.isActive()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Chilling plant " + plant.getCode() + " is not active");
        }

        List<CollectionPointDemand> demand =
                planningDataLoader.loadDemand(request.collectionPointIds(), request.shift());
        List<TankerCapacity> fleet = resolveFleet(request.tankerIds());
        PlanningConstraints constraints =
                constraintsFactory.create(plant.coordinates(), request.shift(), request.shiftStartTime());

        OptimizedRoute plan = routeOptimizer.optimize(demand, fleet, constraints);

        return new OptimizePlanResponse(plant.getId(), plant.getCode(), request.shift(),
                constraints.shiftStartTime(),
                new OptimizePlanResponse.PlanningAssumptions(
                        milkProperties.maxHoldingDuration(),
                        routingProperties.averageSpeedKmph(),
                        routingProperties.roadWindingFactor(),
                        routingProperties.stopBaseServiceDuration(),
                        routingProperties.perFarmerServiceDuration(),
                        "Straight-line distance x winding factor / average speed; "
                                + "no live traffic and no external routing service"),
                plan);
    }

    private List<TankerCapacity> resolveFleet(List<Long> tankerIds) {
        List<Tanker> tankers;
        if (tankerIds == null || tankerIds.isEmpty()) {
            tankers = tankerRepository.findByStatusOrderByCapacityLitresDesc(TankerStatus.ACTIVE);
        } else {
            List<Long> distinctIds = tankerIds.stream().distinct().toList();
            tankers = tankerRepository.findByIdIn(distinctIds);
            if (tankers.size() != distinctIds.size()) {
                Set<Long> found = tankers.stream().map(Tanker::getId).collect(Collectors.toSet());
                throw new ResourceNotFoundException("Unknown tanker ids: "
                        + distinctIds.stream().filter(id -> !found.contains(id)).toList());
            }
            List<String> unavailable = tankers.stream().filter(tanker -> !tanker.isActive())
                    .map(Tanker::getTankerCode).toList();
            if (!unavailable.isEmpty()) {
                throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                        "Cannot plan with unavailable tankers: " + unavailable);
            }
        }
        if (tankers.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.NO_TANKERS_AVAILABLE,
                    "No active tankers are available to plan with");
        }
        return tankers.stream()
                .map(tanker -> new TankerCapacity(tanker.getId(), tanker.getTankerCode(),
                        tanker.getCapacityLitres()))
                .toList();
    }
}
