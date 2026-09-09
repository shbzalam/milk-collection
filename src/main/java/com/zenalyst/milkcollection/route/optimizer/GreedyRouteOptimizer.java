package com.zenalyst.milkcollection.route.optimizer;

import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.common.travel.TravelTimeProvider;
import com.zenalyst.milkcollection.route.planning.CollectionPointDemand;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.TankerCapacity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Constructive greedy heuristic, one tanker at a time.
 *
 * <p>For each tanker, largest capacity first:
 * <ol>
 *   <li><b>Seed</b> with the unassigned collection point <i>farthest</i> from the chilling
 *       plant. This is deliberate: holding time is measured from the first collection, so a
 *       route should pick up the remote milk first and finish next to the plant. Seeding with
 *       the nearest point would do the opposite and waste holding time on the final leg.</li>
 *   <li><b>Extend</b> by repeatedly appending the nearest remaining point that keeps both the
 *       load within tanker capacity and the projected holding time within the limit.</li>
 *   <li><b>Close</b> the route when no point can be appended, and move to the next tanker.</li>
 * </ol>
 *
 * <p>Complexity is O(tankers x points^2) travel-time lookups - trivial at this scale
 * (60 collection points, 22 tankers) and completely deterministic, which is what makes it
 * testable. Ties are broken by collection point id so the same input always yields the
 * same plan.
 *
 * <p>This is a heuristic, not an optimum. It respects the hard constraints (capacity, holding
 * time) and produces sensible, short routes; it does not prove minimality. See the README for
 * why a real VRP solver is out of MVP scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreedyRouteOptimizer implements RouteOptimizer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TravelTimeProvider travelTimeProvider;

    @Override
    public OptimizedRoute optimize(List<CollectionPointDemand> collectionPoints,
                                   List<TankerCapacity> tankers,
                                   PlanningConstraints constraints) {
        List<CollectionPointDemand> remaining = new ArrayList<>(collectionPoints);
        List<TankerCapacity> fleet = tankers.stream()
                .sorted(Comparator.comparing(TankerCapacity::capacityLitres).reversed()
                        .thenComparing(TankerCapacity::tankerId))
                .toList();

        List<OptimizedRoute.ProposedRoute> routes = new ArrayList<>();
        for (TankerCapacity tanker : fleet) {
            if (remaining.isEmpty()) {
                break;
            }
            List<Leg> legs = buildItinerary(tanker, remaining, constraints);
            if (legs.isEmpty()) {
                continue;
            }
            routes.add(toProposedRoute(tanker, legs, constraints));
            Set<Long> assigned = legs.stream().map(leg -> leg.point().collectionPointId())
                    .collect(java.util.stream.Collectors.toSet());
            remaining.removeIf(point -> assigned.contains(point.collectionPointId()));
        }

        List<OptimizedRoute.UnassignedCollectionPoint> unassigned = remaining.stream()
                .map(point -> new OptimizedRoute.UnassignedCollectionPoint(
                        point.collectionPointId(), point.collectionPointCode(),
                        explainUnassigned(point, fleet, constraints)))
                .toList();

        OptimizedRoute result = new OptimizedRoute(routes, unassigned,
                summarise(collectionPoints, fleet, routes, unassigned));
        log.info("Optimised {} collection points into {} routes using {} of {} tankers; {} unassigned",
                collectionPoints.size(), routes.size(), routes.size(), fleet.size(), unassigned.size());
        return result;
    }

    /**
     * Greedily fills one tanker. All time arithmetic is done as {@link Duration} offsets from
     * the moment the tanker leaves the plant, so nothing can wrap around midnight; wall-clock
     * times are derived only for presentation.
     */
    private List<Leg> buildItinerary(TankerCapacity tanker, List<CollectionPointDemand> available,
                                     PlanningConstraints constraints) {
        List<Leg> legs = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        Coordinates position = constraints.chillingPlantLocation();
        Duration elapsed = Duration.ZERO;
        Duration firstArrival = null;
        BigDecimal load = BigDecimal.ZERO;

        while (true) {
            Leg chosen = null;
            for (CollectionPointDemand candidate : available) {
                if (taken.contains(candidate.collectionPointId())) {
                    continue;
                }
                if (load.add(candidate.expectedLitres()).compareTo(tanker.capacityLitres()) > 0) {
                    continue;
                }
                Leg leg = evaluate(candidate, position, elapsed, firstArrival, constraints);
                if (leg.holdingDuration().compareTo(constraints.maxHoldingDuration()) > 0) {
                    continue;
                }
                chosen = better(chosen, leg, legs.isEmpty());
            }
            if (chosen == null) {
                return legs;
            }
            legs.add(chosen);
            taken.add(chosen.point().collectionPointId());
            position = chosen.point().location();
            elapsed = chosen.departure();
            firstArrival = firstArrival == null ? chosen.arrival() : firstArrival;
            load = load.add(chosen.point().expectedLitres());
        }
    }

    private Leg evaluate(CollectionPointDemand candidate, Coordinates position, Duration elapsed,
                         Duration firstArrival, PlanningConstraints constraints) {
        Duration travel = travelTimeProvider.estimateTravelTime(position, candidate.location());
        Duration arrival = elapsed.plus(travel);
        Duration departure = arrival.plus(constraints.serviceDurationFor(candidate.farmerCount()));
        Duration returnLeg = travelTimeProvider.estimateTravelTime(
                candidate.location(), constraints.chillingPlantLocation());
        Duration plantArrival = departure.plus(returnLeg);
        Duration holding = plantArrival.minus(firstArrival == null ? arrival : firstArrival);
        return new Leg(candidate, travel, arrival, departure, plantArrival, holding);
    }

    /**
     * Selection rule. The first stop of a route is seeded with the point that is farthest from
     * the plant; every later stop is the nearest feasible one. Collection point id breaks ties
     * so the plan is reproducible.
     */
    private Leg better(Leg incumbent, Leg candidate, boolean seeding) {
        if (incumbent == null) {
            return candidate;
        }
        int comparison = candidate.travelFromPrevious().compareTo(incumbent.travelFromPrevious());
        if (comparison == 0) {
            return candidate.point().collectionPointId() < incumbent.point().collectionPointId()
                    ? candidate : incumbent;
        }
        boolean candidateWins = seeding ? comparison > 0 : comparison < 0;
        return candidateWins ? candidate : incumbent;
    }

    private OptimizedRoute.ProposedRoute toProposedRoute(TankerCapacity tanker, List<Leg> legs,
                                                         PlanningConstraints constraints) {
        LocalTime start = constraints.shiftStartTime();
        List<OptimizedRoute.ProposedStop> stops = new ArrayList<>(legs.size());
        BigDecimal totalLitres = BigDecimal.ZERO;
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            stops.add(new OptimizedRoute.ProposedStop(
                    i + 1,
                    leg.point().collectionPointId(),
                    leg.point().collectionPointCode(),
                    start.plus(leg.arrival()),
                    start.plus(leg.departure()),
                    leg.point().farmerCount(),
                    leg.point().expectedLitres()));
            totalLitres = totalLitres.add(leg.point().expectedLitres());
        }
        Leg last = legs.get(legs.size() - 1);
        return new OptimizedRoute.ProposedRoute(
                tanker.tankerId(),
                tanker.tankerCode(),
                tanker.capacityLitres(),
                totalLitres,
                totalLitres.multiply(HUNDRED)
                        .divide(tanker.capacityLitres(), 1, RoundingMode.HALF_UP),
                start,
                start.plus(last.plantArrival()),
                last.plantArrival(),
                last.plantArrival().minus(legs.get(0).arrival()),
                stops);
    }

    /**
     * Explains why a collection point ended up outside the plan. The distinction matters
     * operationally: an oversized point needs a bigger tanker or a split, whereas a point that
     * simply ran out of fleet needs another vehicle.
     */
    private String explainUnassigned(CollectionPointDemand point, List<TankerCapacity> fleet,
                                     PlanningConstraints constraints) {
        BigDecimal largestCapacity = fleet.stream().map(TankerCapacity::capacityLitres)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (fleet.isEmpty()) {
            return "No tankers were available for planning";
        }
        if (point.expectedLitres().compareTo(largestCapacity) > 0) {
            return "Expected %s L exceeds the largest tanker capacity of %s L"
                    .formatted(point.expectedLitres(), largestCapacity);
        }
        Duration soloHolding = constraints.serviceDurationFor(point.farmerCount())
                .plus(travelTimeProvider.estimateTravelTime(
                        point.location(), constraints.chillingPlantLocation()));
        if (soloHolding.compareTo(constraints.maxHoldingDuration()) > 0) {
            return "Even as the only stop, milk would be %s old on arrival, over the %s limit"
                    .formatted(soloHolding, constraints.maxHoldingDuration());
        }
        return "No tanker capacity remained after the other collection points were assigned";
    }

    private OptimizedRoute.OptimizationSummary summarise(
            List<CollectionPointDemand> considered, List<TankerCapacity> fleet,
            List<OptimizedRoute.ProposedRoute> routes,
            List<OptimizedRoute.UnassignedCollectionPoint> unassigned) {
        long farmers = routes.stream().flatMap(route -> route.stops().stream())
                .mapToLong(OptimizedRoute.ProposedStop::farmerCount).sum();
        BigDecimal litres = routes.stream().map(OptimizedRoute.ProposedRoute::totalExpectedLitres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int assigned = routes.stream().mapToInt(route -> route.stops().size()).sum();
        return new OptimizedRoute.OptimizationSummary(considered.size(), assigned,
                unassigned.size(), fleet.size(), routes.size(), farmers, litres);
    }

    /** One evaluated candidate step: all offsets are relative to leaving the plant. */
    private record Leg(CollectionPointDemand point,
                       Duration travelFromPrevious,
                       Duration arrival,
                       Duration departure,
                       Duration plantArrival,
                       Duration holdingDuration) {
    }
}
