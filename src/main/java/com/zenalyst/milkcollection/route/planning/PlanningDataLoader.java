package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.collectionpoint.repository.CollectionPointRepository;
import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.farmer.repository.CollectionPointDemandRow;
import com.zenalyst.milkcollection.farmer.repository.FarmerRepository;
import com.zenalyst.milkcollection.route.entity.RouteStop;
import com.zenalyst.milkcollection.route.repository.RouteStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the planning read model: turns persisted collection points, route stops and farmers
 * into the value objects the optimizer and the feasibility service consume.
 *
 * <p>Farmer demand is fetched as a single grouped aggregate per request, never by iterating
 * over collection points.
 */
@Service
@RequiredArgsConstructor
public class PlanningDataLoader {

    private final CollectionPointRepository collectionPointRepository;
    private final RouteStopRepository routeStopRepository;
    private final FarmerRepository farmerRepository;

    /**
     * Demand for a set of collection points, or for every active point when none are given.
     *
     * <p>Explicitly requested points are validated here: planning silently skipping an id the
     * caller asked for would produce a plan that looks complete but is not.
     */
    @Transactional(readOnly = true)
    public List<CollectionPointDemand> loadDemand(List<Long> collectionPointIds, Shift shift) {
        List<CollectionPoint> points = (collectionPointIds == null || collectionPointIds.isEmpty())
                ? collectionPointRepository.findByStatusOrderByIdAsc(EntityStatus.ACTIVE)
                : requireActive(collectionPointIds);
        Map<Long, CollectionPointDemandRow> demand = demandByCollectionPoint(
                points.stream().map(CollectionPoint::getId).toList());

        return points.stream()
                .map(point -> new CollectionPointDemand(
                        point.getId(),
                        point.getCode(),
                        point.coordinates(),
                        farmerCount(demand, point.getId()),
                        expectedLitres(demand, point.getId(), shift)))
                .toList();
    }

    /** The stops of a route version, in sequence, enriched with farmer counts and demand. */
    @Transactional(readOnly = true)
    public List<PlannedStop> loadRouteVersionStops(Long routeVersionId, Shift shift) {
        List<RouteStop> stops = routeStopRepository.findByRouteVersionIdWithCollectionPoint(routeVersionId);
        Map<Long, CollectionPointDemandRow> demand = demandByCollectionPoint(
                stops.stream().map(stop -> stop.getCollectionPoint().getId()).toList());

        return stops.stream()
                .map(stop -> {
                    CollectionPoint point = stop.getCollectionPoint();
                    return new PlannedStop(
                            stop.getId(),
                            point.getId(),
                            point.getCode(),
                            stop.getSequenceNumber(),
                            point.coordinates(),
                            farmerCount(demand, point.getId()),
                            expectedLitres(demand, point.getId(), shift));
                })
                .toList();
    }

    private List<CollectionPoint> requireActive(List<Long> requestedIds) {
        List<Long> distinctIds = requestedIds.stream().distinct().toList();
        List<CollectionPoint> found = collectionPointRepository.findByIdIn(distinctIds);
        if (found.size() != distinctIds.size()) {
            Set<Long> foundIds = found.stream().map(CollectionPoint::getId).collect(Collectors.toSet());
            List<Long> missing = distinctIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ResourceNotFoundException("Unknown collection point ids: " + missing);
        }
        List<String> inactive = found.stream().filter(point -> !point.isActive())
                .map(CollectionPoint::getCode).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Cannot plan for inactive collection points: " + inactive);
        }
        return found;
    }

    private Map<Long, CollectionPointDemandRow> demandByCollectionPoint(List<Long> collectionPointIds) {
        if (collectionPointIds.isEmpty()) {
            return Map.of();
        }
        return farmerRepository
                .aggregateDemandByCollectionPoint(collectionPointIds, EntityStatus.ACTIVE).stream()
                .collect(Collectors.toMap(CollectionPointDemandRow::getCollectionPointId,
                        Function.identity()));
    }

    private long farmerCount(Map<Long, CollectionPointDemandRow> demand, Long collectionPointId) {
        CollectionPointDemandRow row = demand.get(collectionPointId);
        return row == null ? 0L : row.getFarmerCount();
    }

    /**
     * A collection point with no active farmers contributes zero litres rather than being
     * treated as missing data - an empty point is a real (if pointless) stop.
     */
    private BigDecimal expectedLitres(Map<Long, CollectionPointDemandRow> demand,
                                      Long collectionPointId, Shift shift) {
        CollectionPointDemandRow row = demand.get(collectionPointId);
        if (row == null) {
            return BigDecimal.ZERO;
        }
        return shift == Shift.MORNING ? row.getExpectedMorningLitres() : row.getExpectedEveningLitres();
    }
}
