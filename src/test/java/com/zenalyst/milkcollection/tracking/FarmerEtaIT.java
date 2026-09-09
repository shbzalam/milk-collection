package com.zenalyst.milkcollection.tracking;

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
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Where is the tanker?" - the question the dairy currently cannot answer.
 *
 * <p>Geometry: the plant is at 18.50N and the three collection points sit due north at 18.55,
 * 18.60 and 18.65 - each leg is 0.05 degrees, which is 5.56 km and therefore 867 seconds
 * (14m27s) at the configured 30 km/h with the 1.3 winding factor. Service time is 3 minutes per
 * stop plus 2 minutes per farmer. Every expected instant below follows from those numbers, so
 * the arithmetic of the ETA is asserted, not merely its shape.
 *
 * <p>The route runs CP-001 -> CP-002 -> CP-003, leaving the plant at 05:00 local
 * (2026-09-08T23:30:00Z).
 */
@Import(TestClockConfiguration.class)
class FarmerEtaIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";
    private static final Instant DEPARTURE = TestClockConfiguration.SHIFT_START;
    private static final int LEG_SECONDS = 867;

    @Autowired
    private Clock clock;

    private long farmerAtFirst;
    private long farmerAtSecondOne;
    private long farmerAtSecondTwo;
    private long farmerAtThird;
    private long farmerOffRoute;
    private long runId;
    private long tankerId;
    private long firstStop;
    private long secondStop;
    private long thirdStop;

    @BeforeEach
    void setUpRun() throws Exception {
        ((MutableTestClock) clock).setTo(DEPARTURE);

        long villageId = api.createVillage("V-001", 18.50, 73.80);
        long plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        long first = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        long second = api.createCollectionPoint("CP-002", villageId, 18.60, 73.80);
        long third = api.createCollectionPoint("CP-003", villageId, 18.65, 73.80);
        long unrouted = api.createCollectionPoint("CP-999", villageId, 18.70, 73.80);

        farmerAtFirst = api.createFarmer("F-0001", "9000000001", villageId, first, "50", "40");
        // CP-002 serves two farmers, so it takes four minutes of handling, not two.
        farmerAtSecondOne = api.createFarmer("F-0002", "9000000002", villageId, second, "50", "40");
        farmerAtSecondTwo = api.createFarmer("F-0003", "9000000003", villageId, second, "50", "40");
        farmerAtThird = api.createFarmer("F-0004", "9000000004", villageId, third, "50", "40");
        farmerOffRoute = api.createFarmer("F-0009", "9000000009", villageId, unrouted, "50", "40");

        tankerId = api.createTanker("TNK-01", "MH12AA0001", "5000");
        long routeVersionId = api.publishedRouteVersion("R-001", first, second, third);
        runId = api.createRun(routeVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        firstStop = api.stopIdAt(runId, 0);
        secondStop = api.stopIdAt(runId, 1);
        thirdStop = api.stopIdAt(runId, 2);
    }

    @Test
    @DisplayName("a farmer with no run covering their point is told so, not given an error")
    void reportsWhenNoRunIsScheduled() throws Exception {
        api.nextCollection(farmerOffRoute)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NO_RUN_SCHEDULED"))
                .andExpect(jsonPath("$.collectionPoint.code").value("CP-999"))
                .andExpect(jsonPath("$.run").doesNotExist())
                .andExpect(jsonPath("$.estimatedArrivalTime").doesNotExist())
                .andExpect(jsonPath("$.message").value(containsString("No collection run")));
    }

    @Test
    @DisplayName("before departure the estimate is projected from the plant")
    void estimatesFromThePlantBeforeTheRunStarts() throws Exception {
        // CP-003 is the third stop: 3 legs of travel plus handling at CP-001 (5 min) and
        // CP-002 (7 min, two farmers) = 3*867 + 300 + 420 = 3321s after departure.
        api.nextCollection(farmerAtThird)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SCHEDULED"))
                .andExpect(jsonPath("$.run.runNumber").isNotEmpty())
                .andExpect(jsonPath("$.run.status").value("PLANNED"))
                .andExpect(jsonPath("$.run.tankerCode").value("TNK-01"))
                .andExpect(jsonPath("$.run.tankerRegistrationNumber").value("MH12AA0001"))
                .andExpect(jsonPath("$.stopsBeforeYours").value(2))
                .andExpect(jsonPath("$.tankerLocation").doesNotExist())
                .andExpect(jsonPath("$.estimatedArrivalTime").value(at(3321).toString()))
                .andExpect(jsonPath("$.estimatedTimeToArrival").value("PT55M21S"))
                .andExpect(jsonPath("$.estimateBasis").value(containsString("No live traffic")));

        api.nextCollection(farmerAtFirst)
                .andExpect(jsonPath("$.stopsBeforeYours").value(0))
                .andExpect(jsonPath("$.estimatedArrivalTime").value(at(LEG_SECONDS).toString()));
    }

    @Test
    @DisplayName("once on the road the estimate starts from the last reported position")
    void estimatesFromTheLastReportedPosition() throws Exception {
        api.startRun(runId).andExpect(status().isOk());
        // The tanker has covered 0.03 degrees (3.34 km, 520 s) of the first leg.
        api.reportLocation(tankerId, 18.53, 73.80).andExpect(status().isCreated());

        // 0.02 degrees remain to CP-001: 2.224 km -> 347 s.
        api.nextCollection(farmerAtFirst)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EN_ROUTE"))
                .andExpect(jsonPath("$.run.status").value("STARTED"))
                .andExpect(jsonPath("$.tankerLocation.latitude").value(18.53))
                .andExpect(jsonPath("$.tankerLocation.recordedAt").value(DEPARTURE.toString()))
                .andExpect(jsonPath("$.stopsBeforeYours").value(0))
                .andExpect(jsonPath("$.estimatedArrivalTime").value(at(347).toString()))
                .andExpect(jsonPath("$.message").value(containsString("on the road")));
    }

    @Test
    @DisplayName("a farmer at the stop the tanker is working is told it has arrived")
    void reportsWhenTheTankerIsAtTheCollectionPoint() throws Exception {
        api.startRun(runId).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofSeconds(LEG_SECONDS));
        api.arriveAtStop(runId, firstStop).andExpect(status().isOk());

        api.nextCollection(farmerAtFirst)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AT_YOUR_COLLECTION_POINT"))
                .andExpect(jsonPath("$.run.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.stopsBeforeYours").value(0))
                .andExpect(jsonPath("$.estimatedArrivalTime").value(at(LEG_SECONDS).toString()))
                .andExpect(jsonPath("$.message").value(containsString("is at CP-001 now")));
    }

    @Test
    @DisplayName("farmers still waiting at the current stop delay everyone behind them")
    void countsOutstandingFarmersAtTheCurrentStopIntoTheEstimate() throws Exception {
        api.startRun(runId).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofSeconds(LEG_SECONDS));
        api.arriveAtStop(runId, firstStop).andExpect(status().isOk());
        api.collect(runId, firstStop, farmerAtFirst, "50.00").andExpect(status().isCreated());
        api.completeStop(runId, firstStop).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofSeconds(LEG_SECONDS));
        api.arriveAtStop(runId, secondStop).andExpect(status().isOk());

        // CP-002 has two farmers and neither has been recorded, so 2 x 2 min of handling
        // remains before the tanker can move on: 240 + 867 = 1107 s to CP-003.
        long now = 2L * LEG_SECONDS;
        api.nextCollection(farmerAtThird)
                .andExpect(jsonPath("$.state").value("EN_ROUTE"))
                .andExpect(jsonPath("$.stopsBeforeYours").value(0))
                .andExpect(jsonPath("$.estimatedArrivalTime").value(at(now + 240 + LEG_SECONDS).toString()));

        // After the first of the two is recorded, only one farmer's handling time is left.
        api.collect(runId, secondStop, farmerAtSecondOne, "50.00").andExpect(status().isCreated());
        api.nextCollection(farmerAtThird)
                .andExpect(jsonPath("$.estimatedArrivalTime").value(at(now + 120 + LEG_SECONDS).toString()));

        // And the second farmer at this stop is told the tanker is already here.
        api.nextCollection(farmerAtSecondTwo)
                .andExpect(jsonPath("$.state").value("AT_YOUR_COLLECTION_POINT"));
    }

    @Test
    @DisplayName("after collection the farmer is told what was recorded")
    void reportsACompletedCollection() throws Exception {
        api.startRun(runId).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofSeconds(LEG_SECONDS));
        api.arriveAtStop(runId, firstStop).andExpect(status().isOk());
        api.collect(runId, firstStop, farmerAtFirst, "48.50").andExpect(status().isCreated());
        api.completeStop(runId, firstStop).andExpect(status().isOk());

        api.nextCollection(farmerAtFirst)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTED"))
                .andExpect(jsonPath("$.collections.length()").value(1))
                .andExpect(jsonPath("$.collections[0].quantityLitres").value(48.50))
                .andExpect(jsonPath("$.message").value(containsString("48.50 L was recorded")));
    }

    @Test
    void reportsASkippedStop() throws Exception {
        api.startRun(runId).andExpect(status().isOk());
        api.skipStop(runId, thirdStop).andExpect(status().isOk());

        api.nextCollection(farmerAtThird)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SKIPPED"))
                .andExpect(jsonPath("$.estimatedArrivalTime").doesNotExist())
                .andExpect(jsonPath("$.message").value(containsString("not stopping at CP-003")));
    }

    @Test
    @DisplayName("a stop skipped ahead of the tanker does not count as progress")
    void ignoresOutOfOrderSkipsWhenLocatingTheTanker() throws Exception {
        api.startRun(runId).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofSeconds(LEG_SECONDS));
        api.arriveAtStop(runId, firstStop).andExpect(status().isOk());
        api.completeStop(runId, firstStop).andExpect(status().isOk());
        // CP-002 is skipped while the tanker is still at CP-001.
        api.skipStop(runId, secondStop).andExpect(status().isOk());

        // The tanker drives from CP-001 straight to CP-003: one 0.10-degree hop of 11.12 km,
        // which is 1735 s. Note that this is not 2 x 867 s - travel time is rounded once per
        // leg, so dropping a stop also drops a rounding step.
        api.nextCollection(farmerAtThird)
                .andExpect(jsonPath("$.state").value("EN_ROUTE"))
                .andExpect(jsonPath("$.stopsBeforeYours").value(0))
                .andExpect(jsonPath("$.estimatedArrivalTime").value(
                        at(LEG_SECONDS + 1735).toString()));
    }

    private static Instant at(long secondsAfterDeparture) {
        return DEPARTURE.plusSeconds(secondsAfterDeparture);
    }
}
