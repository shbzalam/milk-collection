package com.zenalyst.milkcollection.operations;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import com.zenalyst.milkcollection.support.MutableTestClock;
import com.zenalyst.milkcollection.support.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The holding-time rule at run time.
 *
 * <p>A run only reaches this check if it has fallen behind the schedule that was approved when
 * it was created, so the test drives the clock forward to reproduce a late tanker. The limit is
 * the configured {@code milk.max-holding-duration} - 4 hours in this MVP, which is a documented
 * assumption and not a real dairy regulation.
 *
 * <p>Geometry: the plant is at 18.50N, CP-001 is ~5.6 km away (14 min at the configured 30 km/h
 * with the 1.3 winding factor) and CP-002 is ~27.8 km away (72 min from the plant).
 */
@Import(TestClockConfiguration.class)
class MilkHoldingTimeIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";
    // Durations below are exact consequences of the configured model (30 km/h, 1.3 winding
    // factor, 3 min per stop plus 2 min per farmer) and are asserted precisely on purpose:
    // they are the specification of the holding-time calculation, not incidental values.

    @Autowired
    private Clock clock;

    private long farmerA;
    private long farmerB;
    private long runId;
    private long firstStopId;
    private long secondStopId;

    @BeforeEach
    void setUpLateRun() throws Exception {
        ((MutableTestClock) clock).setTo(TestClockConfiguration.SHIFT_START);

        long villageId = api.createVillage("V-001", 18.50, 73.80);
        long plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        long nearPoint = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        long farPoint = api.createCollectionPoint("CP-002", villageId, 18.75, 73.80);
        farmerA = api.createFarmer("F-0001", "9000000001", villageId, nearPoint, "50", "40");
        farmerB = api.createFarmer("F-0002", "9000000002", villageId, farPoint, "60", "45");
        long tankerId = api.createTanker("TNK-01", "MH12AA0001", "5000");

        // Planned holding time is about 2h20m, comfortably inside the 4h limit, so the run is
        // approved at creation.
        long routeVersionId = api.publishedRouteVersion("R-001", nearPoint, farPoint);
        runId = api.createRun(routeVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.startRun(runId).andExpect(status().isOk());
        firstStopId = api.stopIdAt(runId, 0);
        secondStopId = api.stopIdAt(runId, 1);
    }

    @Test
    @DisplayName("a run that keeps to schedule is never troubled by the holding rule")
    void acceptsMilkWhileTheRunIsOnSchedule() throws Exception {
        api.arriveAtStop(runId, firstStopId).andExpect(status().isOk());
        api.collect(runId, firstStopId, farmerA, "50.00")
                .andExpect(status().isCreated())
                // Remaining route from CP-001 is about 2h15m, well inside the limit.
                .andExpect(jsonPath("$.projectedHoldingDuration").value("PT2H15M6S"))
                .andExpect(jsonPath("$.holdingTimeRemaining").value("PT1H44M54S"));

        api.completeStop(runId, firstStopId).andExpect(status().isOk());
        api.arriveAtStop(runId, secondStopId).andExpect(status().isOk());
        api.collect(runId, secondStopId, farmerB, "60.00")
                .andExpect(status().isCreated())
                // Only the 72-minute run home is left, and the clock has not moved in this
                // test, so the oldest milk is exactly that far from the plant.
                .andExpect(jsonPath("$.projectedHoldingDuration").value("PT1H12M17S"));
    }

    @Test
    @DisplayName("milk is refused once the load can no longer reach the plant in time")
    void refusesMilkWhenTheRunHasFallenTooFarBehind() throws Exception {
        api.arriveAtStop(runId, firstStopId).andExpect(status().isOk());
        api.collect(runId, firstStopId, farmerA, "50.00").andExpect(status().isCreated());
        api.completeStop(runId, firstStopId).andExpect(status().isOk());

        // The tanker is stuck for three hours. The plant is still 72 minutes away, so the milk
        // collected at CP-001 would be 4h12m old on arrival - past the limit.
        ((MutableTestClock) clock).advanceBy(Duration.ofHours(3));

        api.arriveAtStop(runId, secondStopId).andExpect(status().isOk());
        api.collect(runId, secondStopId, farmerB, "60.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MILK_HOLDING_TIME_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("over the PT4H limit")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("PT4H12M17S")));

        // Nothing was persisted, and the earlier collection is untouched.
        api.getRun(runId)
                .andExpect(jsonPath("$.load.collectedLitres").value(50.00))
                .andExpect(jsonPath("$.stops[1].collections.length()").value(0));
    }

    @Test
    @DisplayName("the limit applies to the oldest milk on board, not to the milk being offered")
    void measuresHoldingFromTheOldestMilkOnBoard() throws Exception {
        // Nothing on board yet: three hours late, the tanker is still 72 minutes from the
        // plant, and milk loaded now would only be 1h12m old on arrival - so it is accepted.
        ((MutableTestClock) clock).advanceBy(Duration.ofHours(3));
        api.arriveAtStop(runId, firstStopId).andExpect(status().isOk());
        api.skipStop(runId, secondStopId).andExpect(status().isOk());

        api.collect(runId, firstStopId, farmerA, "50.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectedHoldingDuration").value("PT14M27S"));
    }
}
