package com.zenalyst.milkcollection.run.service;

import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import org.springframework.stereotype.Component;

/**
 * Guards every state change in operations.
 *
 * <p>The transition tables live on {@link RunStatus} and {@link RunStopStatus} as plain data;
 * this component is the single place that turns an illegal transition into an API error, so no
 * service can accidentally invent its own rule. Both methods change the entity only after the
 * transition has been accepted.
 */
@Component
public class RunStateMachine {

    public void transition(CollectionRun run, RunStatus target) {
        if (!run.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException(ErrorCode.INVALID_RUN_STATE,
                    "Run %s cannot move from %s to %s (allowed: %s)".formatted(
                            run.getRunNumber(), run.getStatus(), target,
                            run.getStatus().allowedTransitions()));
        }
        run.setStatus(target);
    }

    public void transition(RunStop stop, RunStopStatus target) {
        if (!stop.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STOP_STATE,
                    "Stop %d of run %s cannot move from %s to %s (allowed: %s)".formatted(
                            stop.getSequenceNumber(), stop.getCollectionRun().getRunNumber(),
                            stop.getStatus(), target, stop.getStatus().allowedTransitions()));
        }
        stop.setStatus(target);
    }

    /** Milk may only be recorded while the tanker is out and standing at the stop. */
    public void requireCollectable(CollectionRun run, RunStop stop) {
        if (!run.getStatus().isOnTheRoad()) {
            throw new BusinessRuleException(ErrorCode.INVALID_RUN_STATE,
                    "Run %s is %s; milk can only be collected while a run is STARTED or IN_PROGRESS"
                            .formatted(run.getRunNumber(), run.getStatus()));
        }
        if (!stop.getStatus().acceptsCollections()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STOP_STATE,
                    "Stop %d of run %s is %s; the tanker must arrive before milk is recorded"
                            .formatted(stop.getSequenceNumber(), run.getRunNumber(), stop.getStatus()));
        }
    }
}
