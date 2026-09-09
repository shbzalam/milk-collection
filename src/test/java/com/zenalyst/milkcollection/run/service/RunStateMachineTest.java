package com.zenalyst.milkcollection.run.service;

import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The transition rules, tested directly rather than through HTTP. */
class RunStateMachineTest {

    private final RunStateMachine stateMachine = new RunStateMachine();

    @ParameterizedTest
    @CsvSource({
            "PLANNED, STARTED",
            "PLANNED, CANCELLED",
            "STARTED, IN_PROGRESS",
            "STARTED, COMPLETED",
            "IN_PROGRESS, COMPLETED"})
    void allowsLegalRunTransitions(RunStatus from, RunStatus to) {
        CollectionRun run = run(from);

        stateMachine.transition(run, to);

        assertThat(run.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @DisplayName("rejects the transitions the brief calls out as invalid")
    @CsvSource({
            "COMPLETED, STARTED",
            "COMPLETED, IN_PROGRESS",
            "COMPLETED, COMPLETED",
            "CANCELLED, IN_PROGRESS",
            "CANCELLED, STARTED",
            "STARTED, PLANNED",
            "STARTED, STARTED",
            "STARTED, CANCELLED",
            "IN_PROGRESS, CANCELLED",
            "IN_PROGRESS, STARTED",
            "PLANNED, IN_PROGRESS",
            "PLANNED, COMPLETED"})
    void rejectsIllegalRunTransitions(RunStatus from, RunStatus to) {
        CollectionRun run = run(from);

        assertThatThrownBy(() -> stateMachine.transition(run, to))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INVALID_RUN_STATE));
        assertThat(run.getStatus()).describedAs("state must not change on a rejected transition")
                .isEqualTo(from);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, ARRIVED",
            "PENDING, SKIPPED",
            "ARRIVED, COLLECTING",
            "ARRIVED, COMPLETED",
            "COLLECTING, COMPLETED"})
    void allowsLegalStopTransitions(RunStopStatus from, RunStopStatus to) {
        RunStop stop = stop(from);

        stateMachine.transition(stop, to);

        assertThat(stop.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @DisplayName("a stop cannot be skipped once the tanker has arrived, or reopened once closed")
    @CsvSource({
            "ARRIVED, SKIPPED",
            "COLLECTING, SKIPPED",
            "COLLECTING, ARRIVED",
            "COMPLETED, ARRIVED",
            "COMPLETED, COMPLETED",
            "SKIPPED, ARRIVED",
            "PENDING, COLLECTING",
            "PENDING, COMPLETED"})
    void rejectsIllegalStopTransitions(RunStopStatus from, RunStopStatus to) {
        RunStop stop = stop(from);

        assertThatThrownBy(() -> stateMachine.transition(stop, to))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INVALID_STOP_STATE));
        assertThat(stop.getStatus()).isEqualTo(from);
    }

    @Test
    @DisplayName("milk is only collectable on the road and at an arrived stop")
    void guardsMilkCollection() {
        stateMachine.requireCollectable(run(RunStatus.IN_PROGRESS), stop(RunStopStatus.ARRIVED));
        stateMachine.requireCollectable(run(RunStatus.STARTED), stop(RunStopStatus.COLLECTING));

        assertThatThrownBy(() -> stateMachine.requireCollectable(
                run(RunStatus.PLANNED), stop(RunStopStatus.ARRIVED)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INVALID_RUN_STATE));

        assertThatThrownBy(() -> stateMachine.requireCollectable(
                run(RunStatus.IN_PROGRESS), stop(RunStopStatus.PENDING)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INVALID_STOP_STATE));

        assertThatThrownBy(() -> stateMachine.requireCollectable(
                run(RunStatus.IN_PROGRESS), stop(RunStopStatus.COMPLETED)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void terminalStatesHaveNoWayOut() {
        assertThat(RunStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(RunStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(RunStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(RunStopStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(RunStopStatus.SKIPPED.isTerminal()).isTrue();
    }

    private static CollectionRun run(RunStatus status) {
        return CollectionRun.builder().runNumber("RUN-20260909-M-00001").status(status).build();
    }

    private static RunStop stop(RunStopStatus status) {
        return RunStop.builder()
                .collectionRun(run(RunStatus.IN_PROGRESS))
                .sequenceNumber(1)
                .status(status)
                .build();
    }
}
