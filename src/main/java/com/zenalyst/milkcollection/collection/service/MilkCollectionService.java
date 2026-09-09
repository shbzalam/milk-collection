package com.zenalyst.milkcollection.collection.service;

import com.zenalyst.milkcollection.collection.dto.MilkCollectionResponse;
import com.zenalyst.milkcollection.collection.dto.MilkCollectionSummary;
import com.zenalyst.milkcollection.collection.dto.RecordMilkCollectionRequest;
import com.zenalyst.milkcollection.collection.dto.RunLoadSummary;
import com.zenalyst.milkcollection.collection.entity.MilkCollection;
import com.zenalyst.milkcollection.collection.entity.MilkCollectionStatus;
import com.zenalyst.milkcollection.collection.repository.MilkCollectionRepository;
import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import com.zenalyst.milkcollection.farmer.repository.FarmerRepository;
import com.zenalyst.milkcollection.farmer.service.FarmerService;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.ProjectedSchedule;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import com.zenalyst.milkcollection.run.service.RunScheduleService;
import com.zenalyst.milkcollection.run.service.RunStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Recording milk, and the four rules that guard it.
 *
 * <p>Everything happens in one transaction that begins by taking a <b>pessimistic write lock on
 * the run row</b>. That is what makes tanker capacity correct under concurrency: two field
 * devices posting at the same instant are serialised by the database, so the second one sees
 * the first one's litres before deciding whether there is room. Java-level synchronisation
 * would not survive a second instance of this service, and optimistic locking would need a
 * retry loop for a case that is genuinely serial.
 *
 * <p>The rules, in the order they are applied:
 * <ol>
 *   <li>the run must be on the road and the tanker standing at this stop;</li>
 *   <li>the farmer must be assigned to this stop's collection point;</li>
 *   <li>the farmer must not already have a collection at this stop;</li>
 *   <li>the load must stay within the tanker's capacity;</li>
 *   <li>the milk already on board must still reach the plant within the holding limit.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilkCollectionService {

    private final MilkCollectionRepository milkCollectionRepository;
    private final CollectionRunRepository collectionRunRepository;
    private final RunStopRepository runStopRepository;
    private final FarmerRepository farmerRepository;
    private final FarmerService farmerService;
    private final RunScheduleService runScheduleService;
    private final RunStateMachine runStateMachine;
    private final Clock clock;

    @Transactional
    public MilkCollectionResponse record(Long runId, Long stopId,
                                         RecordMilkCollectionRequest request) {
        // Lock first, before reading anything that the decision depends on.
        CollectionRun run = collectionRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> ResourceNotFoundException.of("CollectionRun", runId));
        RunStop stop = requireStopOfRun(runId, stopId);
        runStateMachine.requireCollectable(run, stop);

        Farmer farmer = requireFarmerAssignedTo(stop, request.farmerId());
        requireNoExistingCollection(stop, farmer);

        Instant collectedAt = clock.instant();
        BigDecimal newTotal = requireCapacityFor(run, request.quantityLitres());
        ProjectedSchedule remainder = requireHoldingTimeFor(run, stop, collectedAt);

        MilkCollection collection = milkCollectionRepository.save(MilkCollection.builder()
                .runStop(stop)
                .farmer(farmer)
                .quantityLitres(request.quantityLitres())
                .collectedAt(collectedAt)
                .status(MilkCollectionStatus.ACCEPTED)
                .build());

        // The first collection at a stop is what turns arrival into collecting.
        if (stop.getStatus() == RunStopStatus.ARRIVED) {
            runStateMachine.transition(stop, RunStopStatus.COLLECTING);
            runStopRepository.save(stop);
        }

        Duration holding = holdingDuration(runId, remainder, collectedAt);
        PlanningConstraints constraints = runScheduleService.constraintsFor(run);
        log.info("Recorded {} L from farmer {} at {} on run {}; load now {}/{} L; "
                        + "projected plant arrival {} (oldest milk {} old, limit {})",
                request.quantityLitres(), farmer.getFarmerCode(),
                stop.getRouteStop().getCollectionPoint().getCode(), run.getRunNumber(),
                newTotal, run.getTanker().getCapacityLitres(), remainder.plantArrival(),
                holding, constraints.maxHoldingDuration());

        return MilkCollectionResponse.of(collection,
                RunLoadSummary.of(newTotal, run.getTanker().getCapacityLitres()),
                remainder.plantArrival(), holding,
                constraints.maxHoldingDuration().minus(holding));
    }

    @Transactional(readOnly = true)
    public List<MilkCollectionSummary> listForStop(Long runId, Long stopId) {
        requireStopOfRun(runId, stopId);
        return milkCollectionRepository.findByRunStopIdWithFarmer(stopId).stream()
                .map(MilkCollectionSummary::from)
                .toList();
    }

    /**
     * A farmer is only collected at their own collection point. Anything else means either the
     * wrong farmer was selected on the device or the farmer walked to another point - both
     * cases that would corrupt the payment record if accepted silently.
     */
    private Farmer requireFarmerAssignedTo(RunStop stop, Long farmerId) {
        Farmer farmer = farmerService.require(farmerId);
        if (!farmer.isActive()) {
            throw new BusinessRuleException(ErrorCode.INACTIVE_RESOURCE,
                    "Farmer " + farmer.getFarmerCode() + " is not active");
        }
        Long stopCollectionPointId = stop.getRouteStop().getCollectionPoint().getId();
        if (!farmer.getCollectionPoint().getId().equals(stopCollectionPointId)) {
            throw new BusinessRuleException(ErrorCode.FARMER_NOT_ASSIGNED_TO_STOP,
                    "Farmer %s is assigned to collection point %s, not %s".formatted(
                            farmer.getFarmerCode(), farmer.getCollectionPoint().getCode(),
                            stop.getRouteStop().getCollectionPoint().getCode()));
        }
        return farmer;
    }

    /**
     * One collection per farmer per stop. Checked here for a clear message; the unique
     * constraint on {@code (run_stop_id, farmer_id)} is what makes a genuine race impossible.
     */
    private void requireNoExistingCollection(RunStop stop, Farmer farmer) {
        if (milkCollectionRepository.existsByRunStopIdAndFarmerId(stop.getId(), farmer.getId())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_COLLECTION,
                    "Farmer %s already has a collection recorded at stop %d of run %s".formatted(
                            farmer.getFarmerCode(), stop.getSequenceNumber(),
                            stop.getCollectionRun().getRunNumber()));
        }
    }

    /** @return the new total litres on board, if it fits */
    private BigDecimal requireCapacityFor(CollectionRun run, BigDecimal quantityLitres) {
        BigDecimal capacity = run.getTanker().getCapacityLitres();
        BigDecimal alreadyCollected = milkCollectionRepository.totalCollectedLitres(run.getId());
        BigDecimal newTotal = alreadyCollected.add(quantityLitres);
        if (newTotal.compareTo(capacity) > 0) {
            throw new BusinessRuleException(ErrorCode.TANKER_CAPACITY_EXCEEDED,
                    ("Collecting %s L would exceed tanker %s: %s L of %s L already on board, "
                            + "%s L remaining").formatted(quantityLitres,
                            run.getTanker().getTankerCode(), alreadyCollected, capacity,
                            capacity.subtract(alreadyCollected)));
        }
        return newTotal;
    }

    /**
     * Refuses milk that the tanker can no longer deliver in time.
     *
     * <p>The limit applies to the <b>oldest</b> milk on board, because that is what determines
     * whether the load is rejected at the plant. For the first collection of a run the oldest
     * milk is the milk being offered, so the same formula asks "can this milk make it".
     *
     * <p>Rejecting at intake keeps the milk with the farmer, who can still do something with
     * it, instead of adding it to a load that is already lost. A run should not reach this
     * point at all - {@code RouteFeasibilityService} validates the plan before the run is
     * created - so this is the safety net for a run that has fallen behind schedule.
     */
    private ProjectedSchedule requireHoldingTimeFor(CollectionRun run, RunStop stop,
                                                    Instant collectedAt) {
        ProjectedSchedule remainder = projectRemainder(run, stop, collectedAt);
        PlanningConstraints constraints = runScheduleService.constraintsFor(run);
        Duration holding = holdingDuration(run.getId(), remainder, collectedAt);

        if (holding.compareTo(constraints.maxHoldingDuration()) > 0) {
            throw new BusinessRuleException(ErrorCode.MILK_HOLDING_TIME_EXCEEDED,
                    ("Milk cannot be accepted: on the current projection the tanker reaches %s at "
                            + "%s, by which time the oldest milk on board would be %s old, over "
                            + "the %s limit").formatted(
                            run.getChillingPlant().getCode(), remainder.plantArrival(), holding,
                            constraints.maxHoldingDuration()));
        }
        return remainder;
    }

    private Duration holdingDuration(Long runId, ProjectedSchedule remainder, Instant collectedAt) {
        Instant earliest = milkCollectionRepository.earliestCollectedAt(runId);
        return remainder.holdingDurationFrom(earliest != null ? earliest : collectedAt);
    }

    /**
     * Projects the rest of the run from this stop. The tanker is still standing here, so the
     * projection starts after the farmers who have not been served yet at this stop - two
     * farmers at one point take longer than one, and that time counts against the holding limit.
     */
    private ProjectedSchedule projectRemainder(CollectionRun run, RunStop stop, Instant collectedAt) {
        PlanningConstraints constraints = runScheduleService.constraintsFor(run);
        long farmersAtStop = farmerRepository.countByCollectionPointIdAndStatus(
                stop.getRouteStop().getCollectionPoint().getId(), EntityStatus.ACTIVE);
        long served = milkCollectionRepository.countByRunStopId(stop.getId()) + 1;
        long stillWaiting = Math.max(0, farmersAtStop - served);

        Instant departureFromThisStop = collectedAt.plus(
                constraints.perFarmerServiceDuration().multipliedBy(stillWaiting));
        return runScheduleService.projectFrom(run, runScheduleService.locationOf(stop),
                departureFromThisStop, stop.getSequenceNumber());
    }

    private RunStop requireStopOfRun(Long runId, Long stopId) {
        RunStop stop = runStopRepository.findWithDetailById(stopId)
                .orElseThrow(() -> ResourceNotFoundException.of("RunStop", stopId));
        if (!stop.getCollectionRun().getId().equals(runId)) {
            throw ResourceNotFoundException.of("RunStop " + stopId + " on run", runId);
        }
        return stop;
    }
}
