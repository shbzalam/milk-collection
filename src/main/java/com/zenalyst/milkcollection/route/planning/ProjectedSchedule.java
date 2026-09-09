package com.zenalyst.milkcollection.route.planning;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A timetable projected forward from a position and a moment: when the tanker reaches each
 * remaining stop, and when it reaches the chilling plant.
 *
 * <p>The same projection serves three purposes - approving a run before it is created,
 * deciding at intake whether the load can still reach the plant in time, and answering a
 * farmer's "where is the tanker". Holding time is deliberately <b>not</b> a field: it depends
 * on which milk you are asking about, so callers state that explicitly via
 * {@link #holdingDurationFrom(Instant)}.
 */
public record ProjectedSchedule(
        Instant departureFromOrigin,
        List<ProjectedStop> stops,
        Instant plantArrival,
        Duration totalDuration,
        BigDecimal totalExpectedLitres) {

    public record ProjectedStop(
            Long routeStopId,
            Long collectionPointId,
            String collectionPointCode,
            int sequenceNumber,
            Instant plannedArrival,
            Instant plannedDeparture,
            long farmerCount,
            BigDecimal expectedLitres) {
    }

    /** How old milk loaded at {@code milkLoadedAt} will be when the tanker reaches the plant. */
    public Duration holdingDurationFrom(Instant milkLoadedAt) {
        return Duration.between(milkLoadedAt, plantArrival);
    }

    public Optional<Instant> firstStopArrival() {
        return stops.isEmpty() ? Optional.empty() : Optional.of(stops.get(0).plannedArrival());
    }

    /**
     * Holding time for a whole route projected from the plant: the age of the first-collected
     * milk on arrival back at the plant. Zero for a projection with no stops.
     */
    public Duration milkHoldingDuration() {
        return firstStopArrival().map(this::holdingDurationFrom).orElse(Duration.ZERO);
    }

    public Optional<ProjectedStop> stopFor(Long collectionPointId) {
        return stops.stream()
                .filter(stop -> stop.collectionPointId().equals(collectionPointId))
                .findFirst();
    }

    /** Number of stops the tanker still has to work before reaching the given one. */
    public int stopsBefore(Long collectionPointId) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).collectionPointId().equals(collectionPointId)) {
                return i;
            }
        }
        return stops.size();
    }
}
