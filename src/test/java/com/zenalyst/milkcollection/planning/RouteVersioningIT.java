package com.zenalyst.milkcollection.planning;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route versioning: the guarantee that a published plan is never rewritten, which is what
 * makes a historical run reproducible.
 */
class RouteVersioningIT extends IntegrationTestBase {

    private long villageId;
    private long pointA;
    private long pointB;
    private long routeId;

    @BeforeEach
    void setUpNetwork() throws Exception {
        villageId = api.createVillage("V-001", 18.50, 73.80);
        pointA = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);
        pointB = api.createCollectionPoint("CP-002", villageId, 18.60, 73.80);
        routeId = api.createRoute("R-001", "Shirur loop");
    }

    @Test
    @DisplayName("a draft is built up, published, and then frozen")
    void publishesADraftAndFreezesIt() throws Exception {
        long versionId = api.createRouteVersion(routeId);

        api.addStops(routeId, versionId, pointA, pointB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stops.length()").value(2))
                .andExpect(jsonPath("$.stops[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$.stops[0].collectionPoint.code").value("CP-001"))
                .andExpect(jsonPath("$.stops[1].sequenceNumber").value(2));

        api.publishVersion(routeId, versionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // Published means immutable: no more stops, and no second publish.
        api.addStops(routeId, versionId, pointA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_VERSION_IMMUTABLE"));
        api.publishVersion(routeId, versionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_VERSION_IMMUTABLE"));
    }

    @Test
    @DisplayName("publishing a revision archives the previous plan instead of overwriting it")
    void publishingARevisionArchivesThePreviousVersion() throws Exception {
        long firstVersion = api.createRouteVersion(routeId);
        api.addStops(routeId, firstVersion, pointA, pointB).andExpect(status().isOk());
        api.publishVersion(routeId, firstVersion).andExpect(status().isOk());

        // Revise: copy the stops, drop one, publish.
        long secondVersion = api.createRouteVersionCopying(routeId, firstVersion);
        mockMvc.perform(get("/api/v1/routes/{r}/versions/{v}", routeId, secondVersion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stops.length()").value(2));
        api.publishVersion(routeId, secondVersion).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/routes/{r}/versions", routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[1].versionNumber").value(1))
                .andExpect(jsonPath("$[1].status").value("ARCHIVED"));

        // The archived version still has its own stops, untouched.
        mockMvc.perform(get("/api/v1/routes/{r}/versions/{v}", routeId, firstVersion))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.stops.length()").value(2));
    }

    @Test
    void rejectsTheSameCollectionPointTwiceInOneVersion() throws Exception {
        long versionId = api.createRouteVersion(routeId);
        api.addStops(routeId, versionId, pointA).andExpect(status().isOk());

        api.addStops(routeId, versionId, pointA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"));

        // Also caught when the duplicate is inside a single request.
        api.addStops(routeId, versionId, pointB, pointB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"));
    }

    @Test
    void rejectsADuplicateSequenceNumber() throws Exception {
        long versionId = api.createRouteVersion(routeId);
        api.postJson("/api/v1/routes/%d/versions/%d/stops".formatted(routeId, versionId), """
                        {"stops":[{"collectionPointId":%d,"sequenceNumber":1}]}
                        """.formatted(pointA))
                .andExpect(status().isOk());

        api.postJson("/api/v1/routes/%d/versions/%d/stops".formatted(routeId, versionId), """
                        {"stops":[{"collectionPointId":%d,"sequenceNumber":1}]}
                        """.formatted(pointB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"));
    }

    @Test
    @DisplayName("a published stop order may not have gaps")
    void rejectsPublishingNonConsecutiveSequences() throws Exception {
        long versionId = api.createRouteVersion(routeId);
        api.postJson("/api/v1/routes/%d/versions/%d/stops".formatted(routeId, versionId), """
                        {"stops":[{"collectionPointId":%d,"sequenceNumber":1},
                                  {"collectionPointId":%d,"sequenceNumber":3}]}
                        """.formatted(pointA, pointB))
                .andExpect(status().isOk());

        api.publishVersion(routeId, versionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("consecutive")));
    }

    @Test
    void rejectsPublishingAVersionWithNoStops() throws Exception {
        long versionId = api.createRouteVersion(routeId);

        api.publishVersion(routeId, versionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no stops")));
    }

    @Test
    void rejectsRoutingAnInactiveCollectionPoint() throws Exception {
        long closedPoint = api.createCollectionPoint("CP-CLOSED", villageId, 18.62, 73.82, "INACTIVE");
        long versionId = api.createRouteVersion(routeId);

        api.addStops(routeId, versionId, closedPoint)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INACTIVE_RESOURCE"));
    }

    @Test
    @DisplayName("a version id from another route is not reachable through this route's path")
    void doesNotLeakVersionsAcrossRoutes() throws Exception {
        long otherRoute = api.createRoute("R-002", "Kendur loop");
        long otherVersion = api.createRouteVersion(otherRoute);

        mockMvc.perform(get("/api/v1/routes/{r}/versions/{v}", routeId, otherVersion))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
