package com.zenalyst.milkcollection.run.service;

import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.config.AppProperties;
import com.zenalyst.milkcollection.config.RoutingProperties;
import com.zenalyst.milkcollection.farmer.repository.FarmerRepository;
import com.zenalyst.milkcollection.route.planning.PlannedStop;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.PlanningConstraintsFactory;
import com.zenalyst.milkcollection.route.planning.PlanningDataLoader;
import com.zenalyst.milkcollection.route.planning.ProjectedSchedule;
import com.zenalyst.milkcollection.route.planning.ScheduleProjector;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers "what is left of this run, and when will it get there".
 *
 * <p>Shared by the holding-time decision taken when milk is offered and by the ETA quoted to a
 * farmer, so both are based on the same view of the run's progress. Stops that are already
 * completed or skipped are excluded: a projection over stops the tanker will never visit again
 * would overstate both the ETA and the holding time.
 */
@Service
@RequiredArgsConstructor
public class RunScheduleService {

    private final RunStopRepository runStopRepository;
    private final FarmerRepository farmerRepository;
    private final PlanningDataLoader planningDataLoader;
    private final PlanningConstraintsFactory constraintsFactory;
    private final ScheduleProjector scheduleProjector;
    private final RoutingProperties routingProperties;
    private final AppProperties appProperties;

    /** Stops of the run still to be worked, after the given sequence number, in driving order. */
    private List<PlannedStop> remainingStops(CollectionRun run, int afterSequenceNumber) {
        Set<Long> outstandingRouteStopIds =
                runStopRepository.findByRunIdWithCollectionPoint(run.getId()).stream()
                        .filter(stop -> stop.getSequenceNumber() > afterSequenceNumber)
                        .filter(stop -> !stop.getStatus().isTerminal())
                        .map(stop -> stop.getRouteStop().getId())
                        .collect(Collectors.toSet());

        return planningDataLoader
                .loadRouteVersionStops(run.getRouteVersion().getId(), run.getShift()).stream()
                .filter(stop -> outstandingRouteStopIds.contains(stop.routeStopId()))
                .toList();
    }

    /**
     * Projects the rest of the run from a known position and moment through to the plant.
     *
     * @param afterSequenceNumber stops at or before this sequence are treated as already handled
     */
    @Transactional(readOnly = true)
    public ProjectedSchedule projectFrom(CollectionRun run, Coordinates origin, Instant from,
                                         int afterSequenceNumber) {
        return scheduleProjector.project(origin, from, remainingStops(run, afterSequenceNumber),
                constraintsFor(run));
    }

    /**
     * The constraints in force for a run. The shift start is taken from the run's own planned
     * start rather than from configuration, so a run that was scheduled unusually early is
     * assessed against its own plan.
     */
    public PlanningConstraints constraintsFor(CollectionRun run) {
        return constraintsFactory.create(
                run.getChillingPlant().coordinates(),
                run.getShift(),
                run.getPlannedStartTime().atZone(appProperties.timeZone()).toLocalTime());
    }

    /** Location of a stop's collection point. */
    public Coordinates locationOf(RunStop stop) {
        return stop.getRouteStop().getCollectionPoint().coordinates();
    }

    /**
     * Service time still owed at a stop the tanker is standing at, for the farmers who have not
     * been recorded yet. Two farmers at one collection point take longer than one, and that
     * time counts against both the holding limit and anyone waiting further down the route.
     */
    public Duration outstandingServiceAt(RunStop stop, long farmersAlreadyServed) {
        long farmersAtStop = farmerRepository.countByCollectionPointIdAndStatus(
                stop.getRouteStop().getCollectionPoint().getId(), EntityStatus.ACTIVE);
        long stillWaiting = Math.max(0, farmersAtStop - farmersAlreadyServed);
        return routingProperties.perFarmerServiceDuration().multipliedBy(stillWaiting);
    }
}
