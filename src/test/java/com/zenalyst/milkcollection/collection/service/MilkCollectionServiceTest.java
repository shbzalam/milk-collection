package com.zenalyst.milkcollection.collection.service;

import com.zenalyst.milkcollection.chillingplant.entity.ChillingPlant;
import com.zenalyst.milkcollection.collection.dto.MilkCollectionResponse;
import com.zenalyst.milkcollection.collection.dto.RecordMilkCollectionRequest;
import com.zenalyst.milkcollection.collection.entity.MilkCollection;
import com.zenalyst.milkcollection.collection.repository.MilkCollectionRepository;
import com.zenalyst.milkcollection.collectionpoint.entity.CollectionPoint;
import com.zenalyst.milkcollection.common.domain.EntityStatus;
import com.zenalyst.milkcollection.common.domain.Shift;
import com.zenalyst.milkcollection.common.geo.Coordinates;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.farmer.entity.Farmer;
import com.zenalyst.milkcollection.farmer.service.FarmerService;
import com.zenalyst.milkcollection.route.entity.RouteStop;
import com.zenalyst.milkcollection.route.planning.PlanningConstraints;
import com.zenalyst.milkcollection.route.planning.ProjectedSchedule;
import com.zenalyst.milkcollection.run.entity.CollectionRun;
import com.zenalyst.milkcollection.run.entity.RunStatus;
import com.zenalyst.milkcollection.run.entity.RunStop;
import com.zenalyst.milkcollection.run.entity.RunStopStatus;
import com.zenalyst.milkcollection.run.repository.CollectionRunRepository;
import com.zenalyst.milkcollection.run.repository.RunStopRepository;
import com.zenalyst.milkcollection.run.service.RunScheduleService;
import com.zenalyst.milkcollection.run.service.RunStateMachine;
import com.zenalyst.milkcollection.tanker.entity.Tanker;
import com.zenalyst.milkcollection.tanker.entity.TankerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The collection rules at their boundaries, isolated from the database.
 *
 * <p>The integration tests prove the same rules hold against real PostgreSQL; these exist
 * because the interesting cases are the edges - exactly at capacity, one litre over - and
 * setting those up through HTTP would be indirect and slow.
 */
@ExtendWith(MockitoExtension.class)
// Lenient because one shared fixture stubs the whole happy path and each test then exercises a
// subset of it; strict stubs would fail on the collaborators a given rule never reaches.
@MockitoSettings(strictness = Strictness.LENIENT)
class MilkCollectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T01:00:00Z");
    private static final Coordinates PLANT_LOCATION = new Coordinates(18.50, 73.80);

    @Mock
    private MilkCollectionRepository milkCollectionRepository;
    @Mock
    private CollectionRunRepository collectionRunRepository;
    @Mock
    private RunStopRepository runStopRepository;
    @Mock
    private FarmerService farmerService;
    @Mock
    private RunScheduleService runScheduleService;

    private MilkCollectionService service;
    private CollectionRun run;
    private RunStop stop;
    private CollectionPoint collectionPoint;
    private Farmer farmer;

    @BeforeEach
    void setUp() {
        service = new MilkCollectionService(milkCollectionRepository, collectionRunRepository,
                runStopRepository, farmerService, runScheduleService,
                new RunStateMachine(), Clock.fixed(NOW, ZoneOffset.UTC));

        collectionPoint = CollectionPoint.builder().id(1L).code("CP-001")
                .latitude(18.55).longitude(73.80).status(EntityStatus.ACTIVE).build();
        Tanker tanker = Tanker.builder().id(1L).tankerCode("TNK-01")
                .capacityLitres(new BigDecimal("5000.00")).status(TankerStatus.ACTIVE).build();
        ChillingPlant plant = ChillingPlant.builder().id(1L).code("PLANT-01")
                .latitude(18.50).longitude(73.80).status(EntityStatus.ACTIVE).build();

        run = CollectionRun.builder().id(10L).runNumber("RUN-20260909-M-00001")
                .tanker(tanker).chillingPlant(plant).shift(Shift.MORNING)
                .runDate(LocalDate.of(2026, 9, 9))
                .plannedStartTime(Instant.parse("2026-09-08T23:30:00Z"))
                .status(RunStatus.IN_PROGRESS).build();
        stop = RunStop.builder().id(100L).collectionRun(run).sequenceNumber(1)
                .routeStop(RouteStop.builder().id(1000L).collectionPoint(collectionPoint).build())
                .status(RunStopStatus.ARRIVED).build();
        farmer = Farmer.builder().id(7L).farmerCode("F-0001").name("Ramesh Pawar")
                .collectionPoint(collectionPoint).status(EntityStatus.ACTIVE).build();

        when(collectionRunRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(run));
        when(runStopRepository.findWithDetailById(100L)).thenReturn(Optional.of(stop));
        when(farmerService.require(7L)).thenReturn(farmer);
        when(milkCollectionRepository.existsByRunStopIdAndFarmerId(100L, 7L)).thenReturn(false);
        when(milkCollectionRepository.countByRunStopId(100L)).thenReturn(0L);
        when(runScheduleService.outstandingServiceAt(any(), anyLong()))
                .thenReturn(Duration.ZERO);
        when(runScheduleService.constraintsFor(run)).thenReturn(constraints(Duration.ofHours(4)));
        when(runScheduleService.locationOf(stop)).thenReturn(collectionPoint.coordinates());
        when(runScheduleService.projectFrom(any(), any(), any(), anyInt()))
                .thenReturn(scheduleArrivingAt(NOW.plus(Duration.ofMinutes(30))));
        when(milkCollectionRepository.save(any(MilkCollection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("a collection that fits is recorded and reported with the resulting load")
    void recordsACollectionThatFits() {
        givenAlreadyCollected("4900.00");

        MilkCollectionResponse response = service.record(10L, 100L, request("50.00"));

        assertThat(response.quantityLitres()).isEqualByComparingTo("50.00");
        assertThat(response.collectedAt()).isEqualTo(NOW);
        assertThat(response.load().collectedLitres()).isEqualByComparingTo("4950.00");
        assertThat(response.load().remainingCapacityLitres()).isEqualByComparingTo("50.00");
        assertThat(response.projectedPlantArrival()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(response.projectedHoldingDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(response.holdingTimeRemaining()).isEqualTo(Duration.ofMinutes(210));
        // Arriving at a stop becomes collecting on the first collection.
        assertThat(stop.getStatus()).isEqualTo(RunStopStatus.COLLECTING);
    }

    @Test
    @DisplayName("filling the tanker exactly to capacity is allowed")
    void allowsFillingExactlyToCapacity() {
        givenAlreadyCollected("4950.00");

        MilkCollectionResponse response = service.record(10L, 100L, request("50.00"));

        assertThat(response.load().collectedLitres()).isEqualByComparingTo("5000.00");
        assertThat(response.load().remainingCapacityLitres()).isEqualByComparingTo("0.00");
        assertThat(response.load().capacityUtilisationPercent()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("one litre over capacity is refused - the brief's 4,950 + 100 case")
    void refusesACollectionThatWouldOverflowTheTanker() {
        givenAlreadyCollected("4950.00");

        assertThatThrownBy(() -> service.record(10L, 100L, request("100.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.TANKER_CAPACITY_EXCEEDED))
                .hasMessageContaining("4950.00 L of 5000.00 L already on board")
                .hasMessageContaining("50.00 L remaining");

        verify(milkCollectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("capacity is read inside the locked transaction, not taken from the client")
    void readsCapacityUnderTheRunLock() {
        givenAlreadyCollected("0.00");

        service.record(10L, 100L, request("100.00"));

        verify(collectionRunRepository).findByIdForUpdate(10L);
        verify(milkCollectionRepository).totalCollectedLitres(10L);
    }

    @Test
    void refusesASecondCollectionForTheSameFarmerAtTheSameStop() {
        givenAlreadyCollected("50.00");
        when(milkCollectionRepository.existsByRunStopIdAndFarmerId(100L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_COLLECTION));

        verify(milkCollectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a farmer can only deliver at their own collection point")
    void refusesAFarmerFromAnotherCollectionPoint() {
        givenAlreadyCollected("0.00");
        farmer.setCollectionPoint(CollectionPoint.builder().id(2L).code("CP-002")
                .latitude(18.60).longitude(73.80).status(EntityStatus.ACTIVE).build());

        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.FARMER_NOT_ASSIGNED_TO_STOP))
                .hasMessageContaining("CP-002")
                .hasMessageContaining("CP-001");
    }

    @Test
    @DisplayName("milk is refused when the load can no longer reach the plant in time")
    void refusesMilkThatWouldBreachTheHoldingLimit() {
        givenAlreadyCollected("100.00");
        // The tanker will not be at the plant for another five hours.
        when(runScheduleService.projectFrom(any(), any(), any(), anyInt()))
                .thenReturn(scheduleArrivingAt(NOW.plus(Duration.ofHours(5))));

        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.MILK_HOLDING_TIME_EXCEEDED))
                .hasMessageContaining("over the PT4H limit");

        verify(milkCollectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("the holding limit applies to the oldest milk on board, not to the new milk")
    void measuresHoldingFromTheOldestMilkAlreadyOnBoard() {
        givenAlreadyCollected("100.00");
        // The first milk went in three and a half hours ago; the plant is 40 minutes away.
        when(milkCollectionRepository.earliestCollectedAt(10L))
                .thenReturn(NOW.minus(Duration.ofMinutes(210)));
        when(runScheduleService.projectFrom(any(), any(), any(), anyInt()))
                .thenReturn(scheduleArrivingAt(NOW.plus(Duration.ofMinutes(40))));

        // New milk alone would be only 40 minutes old, but the load would be 4h10m.
        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.MILK_HOLDING_TIME_EXCEEDED))
                .hasMessageContaining("PT4H10M");
    }

    @Test
    @DisplayName("exactly at the holding limit is still accepted")
    void acceptsMilkExactlyAtTheHoldingLimit() {
        givenAlreadyCollected("100.00");
        when(milkCollectionRepository.earliestCollectedAt(10L)).thenReturn(NOW);
        when(runScheduleService.projectFrom(any(), any(), any(), anyInt()))
                .thenReturn(scheduleArrivingAt(NOW.plus(Duration.ofHours(4))));

        MilkCollectionResponse response = service.record(10L, 100L, request("50.00"));

        assertThat(response.projectedHoldingDuration()).isEqualTo(Duration.ofHours(4));
        assertThat(response.holdingTimeRemaining()).isZero();
    }

    @Test
    void refusesMilkBeforeTheTankerHasArrived() {
        givenAlreadyCollected("0.00");
        stop.setStatus(RunStopStatus.PENDING);

        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INVALID_STOP_STATE));
    }

    @Test
    void refusesMilkForARunThatIsNotOnTheRoad() {
        givenAlreadyCollected("0.00");
        run.setStatus(RunStatus.PLANNED);

        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INVALID_RUN_STATE));
    }

    @Test
    void refusesAnInactiveFarmer() {
        givenAlreadyCollected("0.00");
        farmer.setStatus(EntityStatus.INACTIVE);

        assertThatThrownBy(() -> service.record(10L, 100L, request("50.00")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).errorCode())
                        .isEqualTo(ErrorCode.INACTIVE_RESOURCE));
    }

    // --- fixtures ----------------------------------------------------------------

    private void givenAlreadyCollected(String litres) {
        when(milkCollectionRepository.totalCollectedLitres(10L)).thenReturn(new BigDecimal(litres));
    }

    private static RecordMilkCollectionRequest request(String litres) {
        return new RecordMilkCollectionRequest(7L, new BigDecimal(litres));
    }

    private static PlanningConstraints constraints(Duration maxHolding) {
        return new PlanningConstraints(PLANT_LOCATION, Shift.MORNING, LocalTime.of(5, 0),
                maxHolding, Duration.ofMinutes(3), Duration.ofMinutes(2));
    }

    private static ProjectedSchedule scheduleArrivingAt(Instant plantArrival) {
        return new ProjectedSchedule(NOW, List.of(), plantArrival,
                Duration.between(NOW, plantArrival), BigDecimal.ZERO);
    }
}
