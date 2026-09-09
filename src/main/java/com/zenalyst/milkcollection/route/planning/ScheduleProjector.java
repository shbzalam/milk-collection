package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.common.travel.TravelTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns "where the tanker is, when, and which stops are left" into a timetable.
 *
 * <p>One projection engine for the whole system, so a run's approved schedule, the holding-time
 * decision taken at intake and the ETA quoted to a farmer can never be based on different
 * arithmetic. Stops are visited in the order given - this never reorders a route.
 *
 * <p>All arithmetic is on {@link Instant}s derived from a single origin instant: no local-time
 * or midnight-wrap arithmetic is involved anywhere in a projection.
 */
@Component
@RequiredArgsConstructor
public class ScheduleProjector {

    private final TravelTimeProvider travelTimeProvider;

    public ProjectedSchedule projectFromPlant(List<PlannedStop> stops, PlanningConstraints constraints,
                                              Instant departure) {
        return project(constraints.chillingPlantLocation(), departure, stops, constraints);
    }

    public ProjectedSchedule project(Coordinates origin, Instant departureFromOrigin,
                                     List<PlannedStop> stops, PlanningConstraints constraints) {
        List<ProjectedSchedule.ProjectedStop> projected = new ArrayList<>(stops.size());
        Coordinates position = origin;
        Instant cursor = departureFromOrigin;
        BigDecimal totalLitres = BigDecimal.ZERO;

        for (PlannedStop stop : stops) {
            Instant arrival = cursor.plus(travelTimeProvider.estimateTravelTime(position, stop.location()));
            Instant departure = arrival.plus(constraints.serviceDurationFor(stop.farmerCount()));
            projected.add(new ProjectedSchedule.ProjectedStop(
                    stop.routeStopId(), stop.collectionPointId(), stop.collectionPointCode(),
                    stop.sequenceNumber(), arrival, departure, stop.farmerCount(), stop.expectedLitres()));
            totalLitres = totalLitres.add(stop.expectedLitres());
            position = stop.location();
            cursor = departure;
        }

        Instant plantArrival = cursor.plus(
                travelTimeProvider.estimateTravelTime(position, constraints.chillingPlantLocation()));

        return new ProjectedSchedule(departureFromOrigin, List.copyOf(projected), plantArrival,
                java.time.Duration.between(departureFromOrigin, plantArrival), totalLitres);
    }
}
