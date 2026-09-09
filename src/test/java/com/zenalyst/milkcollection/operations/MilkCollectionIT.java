package com.zenalyst.milkcollection.operations;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recording milk, including the case the brief singles out: one collection point serving two
 * farmers stays a single physical stop with two collection records.
 */
class MilkCollectionIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";

    private long plantId;
    private long sharedPoint;
    private long otherPoint;
    private long farmerA;
    private long farmerB;
    private long farmerC;
    private long tankerId;
    private long routeVersionId;
    private long runId;
    private long sharedStopId;
    private long otherStopId;

    @BeforeEach
    void setUpRunInProgress() throws Exception {
        long villageId = api.createVillage("V-001", 18.50, 73.80);
        plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        sharedPoint = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        otherPoint = api.createCollectionPoint("CP-002", villageId, 18.58, 73.80);

        // CP-001 serves two farmers; CP-002 serves one.
        farmerA = api.createFarmer("F-0001", "9000000001", villageId, sharedPoint, "50", "40");
        farmerB = api.createFarmer("F-0002", "9000000002", villageId, sharedPoint, "70", "55");
        farmerC = api.createFarmer("F-0003", "9000000003", villageId, otherPoint, "40", "30");

        // Expected morning volume is 160 L, so a 200 L tanker passes the feasibility check.
        tankerId = api.createTanker("TNK-01", "MH12AA0001", "200");
        routeVersionId = api.publishedRouteVersion("R-001", sharedPoint, otherPoint);

        runId = api.createRun(routeVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.startRun(runId).andExpect(status().isOk());
        sharedStopId = api.stopIdAt(runId, 0);
        otherStopId = api.stopIdAt(runId, 1);
        api.arriveAtStop(runId, sharedStopId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("two farmers at one collection point produce two collections against one stop")
    void recordsTwoFarmersAtTheSameStop() throws Exception {
        api.collect(runId, sharedStopId, farmerA, "50.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmer.farmerCode").value("F-0001"))
                .andExpect(jsonPath("$.quantityLitres").value(50.00))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.collectionPoint.code").value("CP-001"))
                .andExpect(jsonPath("$.load.collectedLitres").value(50.00))
                .andExpect(jsonPath("$.load.remainingCapacityLitres").value(150.00))
                .andExpect(jsonPath("$.projectedPlantArrival").isNotEmpty())
                .andExpect(jsonPath("$.holdingTimeRemaining").isNotEmpty());

        api.collect(runId, sharedStopId, farmerB, "70.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.load.collectedLitres").value(120.00))
                .andExpect(jsonPath("$.load.capacityUtilisationPercent").value(60.0));

        // One stop, two collections - the physical route is unchanged.
        api.getRun(runId)
                .andExpect(jsonPath("$.stops.length()").value(2))
                .andExpect(jsonPath("$.stops[0].status").value("COLLECTING"))
                .andExpect(jsonPath("$.stops[0].collectedLitres").value(120.00))
                .andExpect(jsonPath("$.stops[0].collections.length()").value(2))
                .andExpect(jsonPath("$.stops[0].collections[0].farmer.farmerCode").value("F-0001"))
                .andExpect(jsonPath("$.stops[0].collections[1].farmer.farmerCode").value("F-0002"))
                .andExpect(jsonPath("$.load.collectedLitres").value(120.00));

        mockMvc.perform(get("/api/v1/runs/{id}/collections", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsASecondCollectionForTheSameFarmerAtTheSameStop() throws Exception {
        api.collect(runId, sharedStopId, farmerA, "50.00").andExpect(status().isCreated());

        api.collect(runId, sharedStopId, farmerA, "10.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_COLLECTION"));

        api.getRun(runId).andExpect(jsonPath("$.load.collectedLitres").value(50.00));
    }

    @Test
    @DisplayName("a farmer cannot deliver at somebody else's collection point")
    void rejectsAFarmerFromAnotherCollectionPoint() throws Exception {
        api.collect(runId, sharedStopId, farmerC, "40.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FARMER_NOT_ASSIGNED_TO_STOP"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("CP-002")));
    }

    @Test
    void rejectsMilkAtAStopTheTankerHasNotReached() throws Exception {
        api.collect(runId, otherStopId, farmerC, "40.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STOP_STATE"));
    }

    @Test
    @DisplayName("the tanker cannot be overfilled even when farmers yield more than expected")
    void rejectsACollectionThatWouldOverflowTheTanker() throws Exception {
        // Both farmers yield well above their expectations.
        api.collect(runId, sharedStopId, farmerA, "100.00").andExpect(status().isCreated());
        api.collect(runId, sharedStopId, farmerB, "90.00").andExpect(status().isCreated());
        api.completeStop(runId, sharedStopId).andExpect(status().isOk());
        api.arriveAtStop(runId, otherStopId).andExpect(status().isOk());

        api.collect(runId, otherStopId, farmerC, "20.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TANKER_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("190.00 L of 200.00 L already on board")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("10.00 L remaining")));

        // Exactly filling the remaining space is fine.
        api.collect(runId, otherStopId, farmerC, "10.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.load.collectedLitres").value(200.00))
                .andExpect(jsonPath("$.load.remainingCapacityLitres").value(0.00));
    }

    @Test
    void rejectsNonPositiveQuantities() throws Exception {
        api.postJson("/api/v1/runs/%d/stops/%d/collections".formatted(runId, sharedStopId), """
                        {"farmerId":%d,"quantityLitres":0}
                        """.formatted(farmerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantityLitres"));
    }

    @Test
    void rejectsMilkOnceTheRunIsFinished() throws Exception {
        api.collect(runId, sharedStopId, farmerA, "50.00").andExpect(status().isCreated());
        api.completeStop(runId, sharedStopId).andExpect(status().isOk());
        api.skipStop(runId, otherStopId).andExpect(status().isOk());
        api.completeRun(runId).andExpect(status().isOk());

        api.collect(runId, sharedStopId, farmerB, "70.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));
    }

    @Test
    void reportsCollectionsPerStop() throws Exception {
        api.collect(runId, sharedStopId, farmerA, "50.00").andExpect(status().isCreated());
        api.collect(runId, sharedStopId, farmerB, "70.00").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/runs/{id}/stops/{stopId}/collections", runId, sharedStopId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].quantityLitres").value(50.00))
                .andExpect(jsonPath("$[1].quantityLitres").value(70.00));
    }
}
