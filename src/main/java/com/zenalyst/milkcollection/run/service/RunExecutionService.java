package com.zenalyst.milkcollection.run.service;

import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.exception.ResourceNotFoundException;
import com.zenalyst.milkcollection.run.dto.CollectionRunResponse;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Driving a run: start, arrive, complete or skip stops, close out.
 *
 * <p>Every transition goes through {@link RunStateMachine}, so the illegal moves the brief
 * lists - completing a run twice, restarting a completed run, recording milk before arrival -
 * are rejected in one place with one error shape. Timestamps come from the injected
 * {@link Clock}, never {@code Instant.now()}, so the whole lifecycle is testable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunExecutionService {

    private static final Set<RunStopStatus> UNFINISHED_STOPS =
            EnumSet.of(RunStopStatus.PENDING, RunStopStatus.ARRIVED, RunStopStatus.COLLECTING);

    private final CollectionRunRepository collectionRunRepository;
    private final RunStopRepository runStopRepository;
    private final CollectionRunService collectionRunService;
    private final RunStateMachine runStateMachine;
    private final Clock clock;

    /** The tanker leaves the plant. */
    @Transactional
    public CollectionRunResponse start(Long runId) {
        CollectionRun run = collectionRunService.require(runId);
        requireNoOtherRunOnTheRoad(run);

        runStateMachine.transition(run, RunStatus.STARTED);
        run.setActualStartTime(clock.instant());
        collectionRunRepository.save(run);

        log.info("Run {} started with tanker {} at {}", run.getRunNumber(),
                run.getTanker().getTankerCode(), run.getActualStartTime());
        return collectionRunService.getDetail(runId);
    }

    @Transactional
    public CollectionRunResponse cancel(Long runId) {
        CollectionRun run = collectionRunService.require(runId);
        runStateMachine.transition(run, RunStatus.CANCELLED);
        collectionRunRepository.save(run);

        log.info("Run {} cancelled", run.getRunNumber());
        return collectionRunService.getDetail(runId);
    }

    /** The tanker reaches a collection point; from here milk can be recorded. */
    @Transactional
    public CollectionRunResponse arriveAtStop(Long runId, Long stopId) {
        RunStop stop = requireStopOfRun(runId, stopId);
        CollectionRun run = stop.getCollectionRun();
        requireRunOnTheRoad(run);

        runStateMachine.transition(stop, RunStopStatus.ARRIVED);
        stop.setActualArrivalTime(clock.instant());
        runStopRepository.save(stop);

        // The first arrival is what turns a departed run into one that is actually collecting.
        if (run.getStatus() == RunStatus.STARTED) {
            runStateMachine.transition(run, RunStatus.IN_PROGRESS);
            collectionRunRepository.save(run);
        }

        log.info("Run {} arrived at stop {} ({}) at {}", run.getRunNumber(), stop.getSequenceNumber(),
                stop.getRouteStop().getCollectionPoint().getCode(), stop.getActualArrivalTime());
        return collectionRunService.getDetail(runId);
    }

    /** The tanker leaves a collection point. */
    @Transactional
    public CollectionRunResponse completeStop(Long runId, Long stopId) {
        RunStop stop = requireStopOfRun(runId, stopId);
        requireRunOnTheRoad(stop.getCollectionRun());

        runStateMachine.transition(stop, RunStopStatus.COMPLETED);
        stop.setActualDepartureTime(clock.instant());
        runStopRepository.save(stop);

        log.info("Run {} completed stop {} ({})", stop.getCollectionRun().getRunNumber(),
                stop.getSequenceNumber(), stop.getRouteStop().getCollectionPoint().getCode());
        return collectionRunService.getDetail(runId);
    }

    /**
     * Nobody had milk, or the point could not be reached. A stop can only be skipped before
     * arrival, so skipping can never hide milk that was already collected.
     */
    @Transactional
    public CollectionRunResponse skipStop(Long runId, Long stopId) {
        RunStop stop = requireStopOfRun(runId, stopId);
        requireRunOnTheRoad(stop.getCollectionRun());

        runStateMachine.transition(stop, RunStopStatus.SKIPPED);
        runStopRepository.save(stop);

        log.info("Run {} skipped stop {} ({})", stop.getCollectionRun().getRunNumber(),
                stop.getSequenceNumber(), stop.getRouteStop().getCollectionPoint().getCode());
        return collectionRunService.getDetail(runId);
    }

    /**
     * The tanker has delivered to the chilling plant.
     *
     * <p>Every stop must be finished first: leaving stops in a pending state would quietly
     * lose the farmers waiting at them, which is the failure mode the dairy already has.
     */
    @Transactional
    public CollectionRunResponse complete(Long runId) {
        CollectionRun run = collectionRunService.require(runId);
        long unfinished = runStopRepository.countByCollectionRunIdAndStatusIn(runId, UNFINISHED_STOPS);
        if (unfinished > 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_STOP_STATE,
                    "%d stop(s) of run %s are not finished; complete or skip them before closing the run"
                            .formatted(unfinished, run.getRunNumber()));
        }

        runStateMachine.transition(run, RunStatus.COMPLETED);
        run.setActualEndTime(clock.instant());
        collectionRunRepository.save(run);

        log.info("Run {} completed at {}", run.getRunNumber(), run.getActualEndTime());
        return collectionRunService.getDetail(runId);
    }

    private void requireRunOnTheRoad(CollectionRun run) {
        if (!run.getStatus().isOnTheRoad()) {
            throw new BusinessRuleException(ErrorCode.INVALID_RUN_STATE,
                    "Run %s is %s; stops can only be worked while a run is STARTED or IN_PROGRESS"
                            .formatted(run.getRunNumber(), run.getStatus()));
        }
    }

    /**
     * A tanker can only be in one place at a time, so it may not have two runs on the road -
     * for instance an evening run started while the morning run is still out.
     */
    private void requireNoOtherRunOnTheRoad(CollectionRun run) {
        List<CollectionRun> onTheRoad = collectionRunRepository.findByTankerIdAndStatusIn(
                run.getTanker().getId(), EnumSet.of(RunStatus.STARTED, RunStatus.IN_PROGRESS));
        onTheRoad.stream()
                .filter(other -> !other.getId().equals(run.getId()))
                .findFirst()
                .ifPresent(other -> {
                    throw new BusinessRuleException(ErrorCode.TANKER_ALREADY_ASSIGNED,
                            "Tanker %s is already on run %s".formatted(
                                    run.getTanker().getTankerCode(), other.getRunNumber()));
                });
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
