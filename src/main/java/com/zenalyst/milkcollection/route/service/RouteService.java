package com.zenalyst.milkcollection.route.service;

import com.zenalyst.milkcollection.common.dto.PageResponse;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.route.dto.CreateRouteRequest;
import com.zenalyst.milkcollection.route.dto.RouteResponse;
import com.zenalyst.milkcollection.route.entity.Route;
import com.zenalyst.milkcollection.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepository;

    @Transactional
    public RouteResponse create(CreateRouteRequest request) {
        if (routeRepository.existsByRouteCode(request.routeCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_RESOURCE,
                    "Route code " + request.routeCode() + " already exists");
        }
        Route route = routeRepository.save(Route.builder()
                .routeCode(request.routeCode())
                .name(request.name())
                .status(request.statusOrDefault())
                .build());
        log.info("Created route {} ({})", route.getRouteCode(), route.getId());
        return RouteResponse.from(route);
    }

    @Transactional(readOnly = true)
    public PageResponse<RouteResponse> list(Pageable pageable) {
        return PageResponse.from(routeRepository.findAll(pageable), RouteResponse::from);
    }

    @Transactional(readOnly = true)
    public RouteResponse get(Long id) {
        return RouteResponse.from(require(id));
    }

    @Transactional(readOnly = true)
    public Route require(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Route", id));
    }
}
