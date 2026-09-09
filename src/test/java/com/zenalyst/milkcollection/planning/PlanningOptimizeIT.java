package com.zenalyst.milkcollection.planning;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The optimisation endpoint over the real stack: farmer demand is aggregated from the database,
 * and the response carries the assumptions the plan was built on.
 */
class PlanningOptimizeIT extends IntegrationTestBase {

    private long plantId;
    private long nearPoint;
    private long middlePoint;
    private long farPoint;

    @BeforeEach
    void setUpNetwork() throws Exception {
        long villageId = api.createVillage("V-001", 18.50, 73.80);
        plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);

        nearPoint = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        middlePoint = api.createCollectionPoint("CP-002", villageId, 18.60, 73.80);
        farPoint = api.createCollectionPoint("CP-003", villageId, 18.65, 73.80);

        // Two farmers share CP-001 - the case the brief calls out explicitly.
        api.createFarmer("F-0001", "9000000001", villageId, nearPoint, "50", "40");
        api.createFarmer("F-0002", "9000000002", villageId, nearPoint, "50", "40");
        api.createFarmer("F-0003", "9000000003", villageId, middlePoint, "50", "40");
        api.createFarmer("F-0004", "9000000004", villageId, farPoint, "50", "40");
    }

    @Test
    @DisplayName("aggregates farmer demand per collection point and plans farthest-first")
    void proposesOneRouteForAmpleCapacity() throws Exception {
        api.createTanker("TNK-01", "MH12AA0001", "5000");

        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"MORNING"}
                        """.formatted(plantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shift").value("MORNING"))
                .andExpect(jsonPath("$.shiftStartTime").value("05:00:00"))
                .andExpect(jsonPath("$.assumptions.maxMilkHoldingDuration").value("PT4H"))
                .andExpect(jsonPath("$.assumptions.averageSpeedKmph").value(30.0))
                .andExpect(jsonPath("$.plan.routes.length()").value(1))
                .andExpect(jsonPath("$.plan.routes[0].tankerCode").value("TNK-01"))
                .andExpect(jsonPath("$.plan.routes[0].stops.length()").value(3))
                // Farthest point first so the route finishes next to the plant.
                .andExpect(jsonPath("$.plan.routes[0].stops[0].collectionPointCode").value("CP-003"))
                .andExpect(jsonPath("$.plan.routes[0].stops[1].collectionPointCode").value("CP-002"))
                .andExpect(jsonPath("$.plan.routes[0].stops[2].collectionPointCode").value("CP-001"))
                // CP-001 carries two farmers, so 100 L and a longer service time.
                .andExpect(jsonPath("$.plan.routes[0].stops[2].farmerCount").value(2))
                .andExpect(jsonPath("$.plan.routes[0].stops[2].expectedLitres").value(100.00))
                .andExpect(jsonPath("$.plan.routes[0].totalExpectedLitres").value(200.00))
                .andExpect(jsonPath("$.plan.summary.farmersCovered").value(4))
                .andExpect(jsonPath("$.plan.unassigned.length()").value(0));
    }

    @Test
    @DisplayName("the evening shift plans against evening expectations")
    void usesTheExpectedQuantityOfTheRequestedShift() throws Exception {
        api.createTanker("TNK-01", "MH12AA0001", "5000");

        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"EVENING"}
                        """.formatted(plantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftStartTime").value("16:00:00"))
                .andExpect(jsonPath("$.plan.routes[0].totalExpectedLitres").value(160.00));
    }

    @Test
    @DisplayName("what the fleet cannot carry is reported with a reason")
    void reportsUnassignedCollectionPoints() throws Exception {
        api.createTanker("TNK-01", "MH12AA0001", "100");

        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"MORNING"}
                        """.formatted(plantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.routes.length()").value(1))
                .andExpect(jsonPath("$.plan.routes[0].totalExpectedLitres").value(100.00))
                .andExpect(jsonPath("$.plan.routes[0].capacityUtilisationPercent").value(100.0))
                .andExpect(jsonPath("$.plan.unassigned.length()").value(1))
                .andExpect(jsonPath("$.plan.unassigned[0].collectionPointCode").value("CP-001"))
                .andExpect(jsonPath("$.plan.unassigned[0].reason").value(
                        "No tanker capacity remained after the other collection points were assigned"));
    }

    @Test
    void planningARestrictedSetOfPointsOnly() throws Exception {
        api.createTanker("TNK-01", "MH12AA0001", "5000");

        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"MORNING","collectionPointIds":[%d,%d]}
                        """.formatted(plantId, nearPoint, farPoint))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.summary.collectionPointsConsidered").value(2))
                .andExpect(jsonPath("$.plan.routes[0].stops.length()").value(2))
                .andExpect(jsonPath("$.plan.routes[0].stops[0].collectionPointCode").value("CP-003"));
    }

    @Test
    void rejectsPlanningWithoutAnyActiveTanker() throws Exception {
        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"MORNING"}
                        """.formatted(plantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_TANKERS_AVAILABLE"));
    }

    @Test
    void rejectsUnknownCollectionPointIds() throws Exception {
        api.createTanker("TNK-01", "MH12AA0001", "5000");

        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"MORNING","collectionPointIds":[%d,987654]}
                        """.formatted(plantId, middlePoint))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("987654")));
    }
}
