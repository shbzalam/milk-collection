package com.zenalyst.milkcollection.route.service;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.collectionpoint.service.CollectionPointService;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.route.dto.AddRouteStopsRequest;
import com.zenalyst.milkcollection.route.dto.CreateRouteVersionRequest;
import com.zenalyst.milkcollection.route.dto.RouteStopRequest;
import com.zenalyst.milkcollection.route.dto.RouteStopResponse;
import com.zenalyst.milkcollection.route.dto.RouteVersionResponse;
import com.zenalyst.milkcollection.route.entity.Route;
import com.zenalyst.milkcollection.route.entity.RouteStop;
import com.zenalyst.milkcollection.route.entity.RouteVersion;
import com.zenalyst.milkcollection.route.entity.RouteVersionStatus;
import com.zenalyst.milkcollection.route.repository.RouteStopRepository;
import com.zenalyst.milkcollection.route.repository.RouteVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the route-version lifecycle and, with it, the immutability guarantee.
 *
 * <p>Stops can only be written while a version is a DRAFT. Publishing archives the previous
 * published version so a route always has exactly one plan of record, and the published rows
 * are never touched again - which is what makes a run from three months ago still explainable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteVersionService {

    private final RouteVersionRepository routeVersionRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteService routeService;
    private final CollectionPointService collectionPointService;
    private final Clock clock;

    @Transactional
    public RouteVersionResponse createVersion(Long routeId, CreateRouteVersionRequest request) {
        Route route = routeService.require(routeId);
        RouteVersion version = routeVersionRepository.save(RouteVersion.builder()
                .route(route)
                .versionNumber(routeVersionRepository.highestVersionNumber(routeId) + 1)
                .status(RouteVersionStatus.DRAFT)
                .build());

        List<RouteStopResponse> stops = List.of();
        if (request != null && request.copyFromVersionId() != null) {
            stops = copyStopsFrom(request.copyFromVersionId(), routeId, version);
        }
        log.info("Created draft version {} of route {}", version.getVersionNumber(), route.getRouteCode());
        return RouteVersionResponse.withStops(version, stops);
    }

    /**
     * Appends stops to a draft.
     *
     * <p>Rules enforced here and backed by unique constraints in the schema: the version must be
     * a draft, the collection point must be active, the same point may not appear twice in one
     * version, and sequence numbers must be unique.
     */
    @Transactional
    public RouteVersionResponse addStops(Long routeId, Long versionId, AddRouteStopsRequest request) {
        RouteVersion version = requireVersionOfRoute(routeId, versionId);
        requireEditable(version);

        int nextSequence = routeStopRepository.highestSequenceNumber(versionId);
        Set<Integer> usedSequences = new HashSet<>();
        Set<Long> usedCollectionPoints = new HashSet<>();
        List<RouteStop> newStops = new ArrayList<>(request.stops().size());

        for (RouteStopRequest stopRequest : request.stops()) {
            CollectionPoint point = collectionPointService.require(stopRequest.collectionPointId());
            if (!point.isActive()) {
                throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                        "Collection point " + point.getCode() + " is not active and cannot be routed");
            }
            if (!usedCollectionPoints.add(point.getId())
                    || routeStopRepository.existsByRouteVersionIdAndCollectionPointId(versionId, point.getId())) {
                throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                        "Collection point " + point.getCode() + " already appears in this route version");
            }

            int sequence = stopRequest.sequenceNumber() != null
                    ? stopRequest.sequenceNumber()
                    : ++nextSequence;
            if (!usedSequences.add(sequence)
                    || routeStopRepository.existsByRouteVersionIdAndSequenceNumber(versionId, sequence)) {
                throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                        "Sequence number " + sequence + " is already used in this route version");
            }

            newStops.add(RouteStop.builder()
                    .routeVersion(version)
                    .collectionPoint(point)
                    .sequenceNumber(sequence)
                    .plannedArrivalTime(stopRequest.plannedArrivalTime())
                    .plannedDepartureTime(stopRequest.plannedDepartureTime())
                    .build());
        }

        routeStopRepository.saveAll(newStops);
        log.info("Added {} stop(s) to draft version {} of route {}", newStops.size(),
                version.getVersionNumber(), version.getRoute().getRouteCode());
        return getVersion(routeId, versionId);
    }

    /**
     * Validates a draft and makes it the plan of record.
     *
     * <p>Feasibility (capacity and holding time) is deliberately <b>not</b> checked here: both
     * depend on which tanker drives the route and which plant it delivers to, and those are
     * facts about a run, not about a route. They are enforced when a run is created.
     */
    @Transactional
    public RouteVersionResponse publish(Long routeId, Long versionId) {
        RouteVersion version = requireVersionOfRoute(routeId, versionId);
        requireEditable(version);
        if (!version.getRoute().isActive()) {
            throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                    "Route " + version.getRoute().getRouteCode() + " is not active");
        }

        List<RouteStop> stops = routeStopRepository.findByRouteVersionIdWithCollectionPoint(versionId);
        validateForPublication(version, stops);

        // The previous plan of record must be archived before this one is published: a partial
        // unique index allows only one PUBLISHED version per route, so the order of these two
        // updates matters and the archive is flushed explicitly.
        routeVersionRepository.findByRouteIdAndStatus(routeId, RouteVersionStatus.PUBLISHED)
                .ifPresent(current -> {
                    current.setStatus(RouteVersionStatus.ARCHIVED);
                    routeVersionRepository.saveAndFlush(current);
                    log.info("Archived version {} of route {}", current.getVersionNumber(),
                            version.getRoute().getRouteCode());
                });

        version.setStatus(RouteVersionStatus.PUBLISHED);
        version.setPublishedAt(clock.instant());
        routeVersionRepository.save(version);
        log.info("Published version {} of route {} with {} stops", version.getVersionNumber(),
                version.getRoute().getRouteCode(), stops.size());

        return RouteVersionResponse.withStops(version, stops.stream().map(RouteStopResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public List<RouteVersionResponse> listVersions(Long routeId) {
        routeService.require(routeId);
        return routeVersionRepository.findByRouteIdOrderByVersionNumberDesc(routeId).stream()
                .map(RouteVersionResponse::withoutStops)
                .toList();
    }

    @Transactional(readOnly = true)
    public RouteVersionResponse getVersion(Long routeId, Long versionId) {
        RouteVersion version = requireVersionOfRoute(routeId, versionId);
        List<RouteStopResponse> stops = routeStopRepository
                .findByRouteVersionIdWithCollectionPoint(versionId).stream()
                .map(RouteStopResponse::from)
                .toList();
        return RouteVersionResponse.withStops(version, stops);
    }

    /** Loads a version and asserts it really belongs to the route in the path. */
    private RouteVersion requireVersionOfRoute(Long routeId, Long versionId) {
        RouteVersion version = routeVersionRepository.findWithRouteById(versionId)
                .orElseThrow(() -> ResourceNotFoundException.of("RouteVersion", versionId));
        if (!version.getRoute().getId().equals(routeId)) {
            throw ResourceNotFoundException.of("RouteVersion " + versionId + " on route", routeId);
        }
        return version;
    }

    @Transactional(readOnly = true)
    public RouteVersion require(Long versionId) {
        return routeVersionRepository.findWithRouteById(versionId)
                .orElseThrow(() -> ResourceNotFoundException.of("RouteVersion", versionId));
    }

    private List<RouteStopResponse> copyStopsFrom(Long sourceVersionId, Long routeId,
                                                  RouteVersion target) {
        RouteVersion source = requireVersionOfRoute(routeId, sourceVersionId);
        List<RouteStop> copies = routeStopRepository
                .findByRouteVersionIdWithCollectionPoint(source.getId()).stream()
                .map(stop -> RouteStop.builder()
                        .routeVersion(target)
                        .collectionPoint(stop.getCollectionPoint())
                        .sequenceNumber(stop.getSequenceNumber())
                        .plannedArrivalTime(stop.getPlannedArrivalTime())
                        .plannedDepartureTime(stop.getPlannedDepartureTime())
                        .build())
                .toList();
        routeStopRepository.saveAll(copies);
        log.info("Copied {} stop(s) from version {} into new version {}", copies.size(),
                source.getVersionNumber(), target.getVersionNumber());
        return copies.stream().map(RouteStopResponse::from).toList();
    }

    private void validateForPublication(RouteVersion version, List<RouteStop> stops) {
        if (stops.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                    "Route version " + version.getVersionNumber() + " has no stops");
        }
        Optional<RouteStop> inactive = stops.stream()
                .filter(stop -> !stop.getCollectionPoint().isActive())
                .findFirst();
        if (inactive.isPresent()) {
            throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                    "Collection point " + inactive.get().getCollectionPoint().getCode()
                            + " is not active and cannot be part of a published route");
        }
        // Sequences must be exactly 1..N. A published version is a driving order, so gaps or
        // duplicates would leave the stop order open to interpretation in the field.
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getSequenceNumber() != i + 1) {
                throw new BusinessRuleException(ErrorCode.INVALID_ROUTE,
                        "Stop sequence numbers must be consecutive starting at 1, found "
                                + stops.stream().map(RouteStop::getSequenceNumber).toList());
            }
        }
    }

    private void requireEditable(RouteVersion version) {
        if (!version.isEditable()) {
            throw new BusinessRuleException(ErrorCode.ROUTE_VERSION_IMMUTABLE,
                    "Route version " + version.getVersionNumber() + " is " + version.getStatus()
                            + " and cannot be modified; create a new version instead");
        }
    }
}
