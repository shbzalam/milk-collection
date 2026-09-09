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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Position reporting: append-only history, cheap latest lookup, automatic run attachment. */
@Import(TestClockConfiguration.class)
class TankerTrackingIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";

    @Autowired
    private Clock clock;

    private long tankerId;
    private long plantId;
    private long routeVersionId;

    @BeforeEach
    void setUpFleet() throws Exception {
        ((MutableTestClock) clock).setTo(TestClockConfiguration.SHIFT_START);

        long villageId = api.createVillage("V-001", 18.50, 73.80);
        plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        long point = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        api.createFarmer("F-0001", "9000000001", villageId, point, "50", "40");
        tankerId = api.createTanker("TNK-01", "MH12AA0001", "5000");
        routeVersionId = api.publishedRouteVersion("R-001", point);
    }

    @Test
    void reportsNoLocationBeforeTheFirstPing() throws Exception {
        mockMvc.perform(get("/api/v1/tankers/{id}/location", tankerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("history is appended and the latest position is the one returned")
    void keepsHistoryAndReturnsTheLatestPosition() throws Exception {
        api.reportLocation(tankerId, 18.51, 73.80)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tankerCode").value("TNK-01"))
                .andExpect(jsonPath("$.latitude").value(18.51))
                // No run is on the road, so the ping is stored without one.
                .andExpect(jsonPath("$.collectionRunId").doesNotExist());

        ((MutableTestClock) clock).advanceBy(Duration.ofMinutes(5));
        api.reportLocation(tankerId, 18.53, 73.80).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tankers/{id}/location", tankerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(18.53))
                .andExpect(jsonPath("$.recordedAt").value(
                        TestClockConfiguration.SHIFT_START.plus(Duration.ofMinutes(5)).toString()));

        // Both pings are still on record - the history is what lets a bad load be reconstructed.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from tanker_location where tanker_id = ?", Long.class, tankerId))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("a ping during a run is attached to that run automatically")
    void attachesPingsToTheRunOnTheRoad() throws Exception {
        long runId = api.createRun(routeVersionId, tankerId, plantId, RUN_DATE, "MORNING");

        // Before the run starts there is nothing on the road to attach to.
        api.reportLocation(tankerId, 18.50, 73.80)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionRunId").doesNotExist());

        api.startRun(runId).andExpect(status().isOk());
        api.reportLocation(tankerId, 18.52, 73.80)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionRunId").value((int) runId))
                .andExpect(jsonPath("$.runNumber").isNotEmpty());

        api.skipStop(runId, api.stopIdAt(runId, 0)).andExpect(status().isOk());
        api.completeRun(runId).andExpect(status().isOk());

        // Once the run is closed, later pings are unattached again.
        api.reportLocation(tankerId, 18.50, 73.80)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.collectionRunId").doesNotExist());
    }

    @Test
    void rejectsImpossibleCoordinates() throws Exception {
        api.postJson("/api/v1/tankers/%d/location".formatted(tankerId), """
                        {"latitude":95.0,"longitude":73.80}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("latitude"));
    }

    @Test
    void reportsUnknownTanker() throws Exception {
        api.postJson("/api/v1/tankers/999999/location", """
                        {"latitude":18.5,"longitude":73.8}
                        """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
