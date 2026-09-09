package com.zenalyst.milkcollection.operations;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import com.zenalyst.milkcollection.support.MutableTestClock;
import com.zenalyst.milkcollection.support.TestClockConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One morning, end to end: master data, a versioned plan, a run, milk from two farmers sharing
 * a collection point, capacity enforcement, a farmer's ETA enquiry, and closing out.
 *
 * <p>The narrower integration tests each isolate one rule; this one exists to prove the parts
 * compose - that the sequence a dairy actually performs works through the public API from an
 * empty database.
 */
@Import(TestClockConfiguration.class)
class FullCollectionJourneyIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("a full morning collection, from empty database to a closed run")
    void collectsAMorningRunEndToEnd() throws Exception {
        ((MutableTestClock) clock).setTo(TestClockConfiguration.SHIFT_START);

        // 1. A village.
        long villageId = api.createVillage("V-001", 18.50, 73.80);

        // 2. A chilling plant and two collection points.
        long plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        long sharedPoint = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        long secondPoint = api.createCollectionPoint("CP-002", villageId, 18.60, 73.80);

        // 3. Two farmers at the SAME collection point, plus one at the second.
        long farmerA = api.createFarmer("F-0001", "9820100001", villageId, sharedPoint, "50", "40");
        long farmerB = api.createFarmer("F-0002", "9820100002", villageId, sharedPoint, "70", "55");
        long farmerC = api.createFarmer("F-0003", "9820100003", villageId, secondPoint, "40", "32");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/collection-points/{id}", sharedPoint))
                .andExpect(jsonPath("$.farmerCount").value(2));

        // 4. A tanker. Expected morning volume is 160 L, so 200 L is enough to plan with.
        long tankerId = api.createTanker("TNK-01", "MH12AB1234", "200");

        // 5. A route, and 6. a draft version of it.
        long routeId = api.createRoute("R-001", "Shirur morning loop");
        long versionId = api.createRouteVersion(routeId);

        // 7. Stops on the draft.
        api.addStops(routeId, versionId, sharedPoint, secondPoint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stops.length()").value(2));

        // 8. Publish it: from here the plan is frozen.
        api.publishVersion(routeId, versionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        api.addStops(routeId, versionId, sharedPoint)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_VERSION_IMMUTABLE"));

        // 9. A morning run. Creating it checks capacity and holding time up front.
        long runId = api.createRun(versionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.getRun(runId)
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.routeVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.stops[0].plannedArrivalTime").isNotEmpty());

        // A farmer ringing up before the tanker leaves gets a scheduled answer, not an error.
        api.nextCollection(farmerA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SCHEDULED"))
                .andExpect(jsonPath("$.run.tankerRegistrationNumber").value("MH12AB1234"));

        // 10. Start the run.
        api.startRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));

        // 11. The tanker reports its position, which is attached to the run automatically.
        api.reportLocation(tankerId, 18.53, 73.80)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionRunId").value((int) runId));

        long firstStop = api.stopIdAt(runId, 0);
        long secondStop = api.stopIdAt(runId, 1);

        // 12. Arrive at the shared collection point.
        ((MutableTestClock) clock).advanceBy(Duration.ofMinutes(15));
        api.arriveAtStop(runId, firstStop)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.stops[0].status").value("ARRIVED"));

        // 13 and 14. Both farmers at this one stop.
        api.collect(runId, firstStop, farmerA, "60.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.load.collectedLitres").value(60.00));
        api.collect(runId, firstStop, farmerB, "80.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.load.collectedLitres").value(140.00));

        // 15. Capacity is reported server-side, from the recorded collections.
        api.getRun(runId)
                .andExpect(jsonPath("$.load.collectedLitres").value(140.00))
                .andExpect(jsonPath("$.load.remainingCapacityLitres").value(60.00))
                .andExpect(jsonPath("$.load.capacityUtilisationPercent").value(70.0))
                .andExpect(jsonPath("$.stops[0].collections.length()").value(2));

        // 17. Meanwhile the farmer at the next point asks where the tanker is.
        api.nextCollection(farmerC)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EN_ROUTE"))
                .andExpect(jsonPath("$.stopsBeforeYours").value(0))
                .andExpect(jsonPath("$.tankerLocation.latitude").value(18.53))
                .andExpect(jsonPath("$.estimatedArrivalTime").isNotEmpty())
                .andExpect(jsonPath("$.estimateBasis").value(containsString("No live traffic")));

        api.completeStop(runId, firstStop).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofMinutes(15));
        api.arriveAtStop(runId, secondStop).andExpect(status().isOk());

        // 16. More milk than the tanker can take is refused.
        api.collect(runId, secondStop, farmerC, "80.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TANKER_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(containsString("60.00 L remaining")));

        // What does fit is accepted, filling the tanker exactly.
        api.collect(runId, secondStop, farmerC, "60.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.load.collectedLitres").value(200.00))
                .andExpect(jsonPath("$.load.remainingCapacityLitres").value(0.00));

        // And a farmer already collected from is told so.
        api.nextCollection(farmerA)
                .andExpect(jsonPath("$.state").value("COLLECTED"))
                .andExpect(jsonPath("$.collections[0].quantityLitres").value(60.00));

        // 18. Invalid transitions are refused throughout.
        api.startRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));
        api.cancelRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));
        api.completeRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STOP_STATE"));

        // 19. Close the stop, then the run.
        api.completeStop(runId, secondStop).andExpect(status().isOk());
        ((MutableTestClock) clock).advanceBy(Duration.ofMinutes(20));
        api.completeRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualEndTime").isNotEmpty())
                .andExpect(jsonPath("$.load.collectedLitres").value(200.00));

        // The record that remains: three collections against two stops of one run, with the
        // run still pointing at the exact route version it drove.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from milk_collection", Long.class)).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject("""
                select count(distinct mc.run_stop_id) from milk_collection mc
                """, Long.class)).isEqualTo(2L);
        api.getRun(runId).andExpect(jsonPath("$.routeVersion.id").value((int) versionId));
    }
}
