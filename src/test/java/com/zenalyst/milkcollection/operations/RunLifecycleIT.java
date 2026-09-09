package com.zenalyst.milkcollection.operations;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The run lifecycle end to end, including the state transitions the brief requires to be
 * rejected.
 */
class RunLifecycleIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";

    private long villageId;
    private long plantId;
    private long nearPoint;
    private long farPoint;
    private long tankerId;
    private long publishedVersionId;

    @BeforeEach
    void setUpPlan() throws Exception {
        villageId = api.createVillage("V-001", 18.50, 73.80);
        plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        nearPoint = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        farPoint = api.createCollectionPoint("CP-002", villageId, 18.60, 73.80);
        api.createFarmer("F-0001", "9000000001", villageId, nearPoint, "50", "40");
        api.createFarmer("F-0002", "9000000002", villageId, farPoint, "60", "45");
        tankerId = api.createTanker("TNK-01", "MH12AA0001", "5000");
        publishedVersionId = api.publishedRouteVersion("R-001", nearPoint, farPoint);
    }

    @Test
    @DisplayName("a run is planned with a projected timetable, then driven to completion")
    void drivesARunFromPlannedToCompleted() throws Exception {
        long runId = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");

        api.getRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.runNumber").value(
                        org.hamcrest.Matchers.matchesPattern("RUN-20260909-M-\\d{5}")))
                .andExpect(jsonPath("$.shift").value("MORNING"))
                .andExpect(jsonPath("$.routeVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.routeVersion.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.tanker.tankerCode").value("TNK-01"))
                .andExpect(jsonPath("$.stops.length()").value(2))
                .andExpect(jsonPath("$.stops[0].status").value("PENDING"))
                // The timetable is projected, not copied from the route template.
                .andExpect(jsonPath("$.stops[0].plannedArrivalTime").isNotEmpty())
                .andExpect(jsonPath("$.actualStartTime").doesNotExist());

        api.startRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.actualStartTime").isNotEmpty());

        long firstStop = api.stopIdAt(runId, 0);
        long secondStop = api.stopIdAt(runId, 1);

        // The first arrival is what moves the run to IN_PROGRESS.
        api.arriveAtStop(runId, firstStop)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.stops[0].status").value("ARRIVED"))
                .andExpect(jsonPath("$.stops[0].actualArrivalTime").isNotEmpty());

        api.completeStop(runId, firstStop)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stops[0].actualDepartureTime").isNotEmpty());

        // A run cannot be closed while a stop is still pending.
        api.completeRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STOP_STATE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not finished")));

        api.arriveAtStop(runId, secondStop).andExpect(status().isOk());
        api.completeStop(runId, secondStop).andExpect(status().isOk());

        api.completeRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualEndTime").isNotEmpty());
    }

    @Test
    @DisplayName("a stop nobody was waiting at can be skipped, and the run still closes")
    void skipsAStopAndStillCompletes() throws Exception {
        long runId = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.startRun(runId).andExpect(status().isOk());
        long firstStop = api.stopIdAt(runId, 0);
        long secondStop = api.stopIdAt(runId, 1);

        api.skipStop(runId, firstStop)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].status").value("SKIPPED"));

        // Skipping after arrival is refused - it would hide milk that was already collected.
        api.arriveAtStop(runId, secondStop).andExpect(status().isOk());
        api.skipStop(runId, secondStop)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STOP_STATE"));

        api.completeStop(runId, secondStop).andExpect(status().isOk());
        api.completeRun(runId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("invalid run transitions are rejected")
    void rejectsInvalidRunTransitions() throws Exception {
        long runId = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");

        // Cannot work stops before the run starts.
        api.arriveAtStop(runId, api.stopIdAt(runId, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));

        api.startRun(runId).andExpect(status().isOk());
        api.startRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));

        // Milk is on board: cancelling is no longer an option.
        api.cancelRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));

        api.skipStop(runId, api.stopIdAt(runId, 0)).andExpect(status().isOk());
        api.skipStop(runId, api.stopIdAt(runId, 1)).andExpect(status().isOk());
        api.completeRun(runId).andExpect(status().isOk());

        // Terminal means terminal.
        api.completeRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));
        api.startRun(runId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RUN_STATE"));
    }

    @Test
    @DisplayName("a cancelled run releases the tanker's slot")
    void cancellingFreesTheTankerSlot() throws Exception {
        long runId = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");

        api.createRunRaw(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TANKER_ALREADY_ASSIGNED"));

        api.cancelRun(runId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        api.createRunRaw(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the same tanker can run morning and evening, but not both at once")
    void doesNotAllowTwoRunsOnTheRoadForOneTanker() throws Exception {
        long morning = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        long evening = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "EVENING");

        api.startRun(morning).andExpect(status().isOk());
        api.startRun(evening)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TANKER_ALREADY_ASSIGNED"));
    }

    @Test
    void refusesToRunADraftRouteVersion() throws Exception {
        long routeId = api.createRoute("R-DRAFT", "Unfinished loop");
        long draftVersion = api.createRouteVersion(routeId);
        api.addStops(routeId, draftVersion, nearPoint).andExpect(status().isOk());

        api.createRunRaw(draftVersion, tankerId, plantId, RUN_DATE, "MORNING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_VERSION_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("a run is refused when the expected volume does not fit the tanker")
    void refusesAnInfeasibleRunOnCapacity() throws Exception {
        long smallTanker = api.createTanker("TNK-TINY", "MH12AA0099", "80");

        api.createRunRaw(publishedVersionId, smallTanker, plantId, RUN_DATE, "MORNING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TANKER_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("110.00 L")));
    }

    @Test
    @DisplayName("a run is refused when the milk could not reach the plant in time")
    void refusesAnInfeasibleRunOnHoldingTime() throws Exception {
        // ~220 km from the plant: over 9 hours of driving at the configured 30 km/h.
        long remotePoint = api.createCollectionPoint("CP-REMOTE", villageId, 20.50, 73.80);
        api.createFarmer("F-9999", "9000009999", villageId, remotePoint, "20", "20");
        long remoteVersion = api.publishedRouteVersion("R-REMOTE", remotePoint);

        api.createRunRaw(remoteVersion, tankerId, plantId, RUN_DATE, "MORNING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MILK_HOLDING_TIME_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("over the PT4H limit")));
    }

    @Test
    @DisplayName("historical runs keep pointing at the version they drove")
    void keepsHistoricalRunsOnTheirOwnRouteVersion() throws Exception {
        long runId = api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");

        // Revise the route: a new version with only one stop becomes the plan of record.
        long routeId = (long) api.json(api.getRun(runId)).get("routeVersion").get("routeId").asLong();
        long secondVersion = api.createRouteVersion(routeId);
        api.addStops(routeId, secondVersion, nearPoint).andExpect(status().isOk());
        api.publishVersion(routeId, secondVersion).andExpect(status().isOk());

        api.getRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeVersion.id").value((int) publishedVersionId))
                .andExpect(jsonPath("$.routeVersion.versionNumber").value(1))
                .andExpect(jsonPath("$.routeVersion.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.stops.length()").value(2));
    }

    @Test
    void listsRunsForADispatchView() throws Exception {
        api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.createRun(publishedVersionId, tankerId, plantId, RUN_DATE, "EVENING");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/runs")
                        .param("runDate", RUN_DATE).param("shift", "MORNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].shift").value("MORNING"))
                .andExpect(jsonPath("$.content[0].stops").doesNotExist());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/runs").param("runDate", RUN_DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
