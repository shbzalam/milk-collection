package com.zenalyst.milkcollection.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin driver over the public API for integration tests. Tests set up their fixtures through
 * the same HTTP endpoints a client uses, rather than inserting rows behind the API's back -
 * so setup itself exercises the validation rules.
 */
public class TestApi {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestApi(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public long createVillage(String code, double latitude, double longitude) throws Exception {
        return id(created("/api/v1/villages", """
                {"code":"%s","name":"%s village","latitude":%s,"longitude":%s}
                """.formatted(code, code, latitude, longitude)));
    }

    public long createCollectionPoint(String code, long villageId, double latitude, double longitude)
            throws Exception {
        return createCollectionPoint(code, villageId, latitude, longitude, "ACTIVE");
    }

    public long createCollectionPoint(String code, long villageId, double latitude, double longitude,
                                      String status) throws Exception {
        return id(created("/api/v1/collection-points", """
                {"code":"%s","name":"%s point","villageId":%d,"latitude":%s,"longitude":%s,
                 "status":"%s"}
                """.formatted(code, code, villageId, latitude, longitude, status)));
    }

    public long createFarmer(String code, String phone, long villageId, long collectionPointId,
                             String morningLitres, String eveningLitres) throws Exception {
        return id(created("/api/v1/farmers", """
                {"farmerCode":"%s","name":"Farmer %s","phone":"%s","villageId":%d,
                 "collectionPointId":%d,"expectedMorningQuantityLitres":%s,
                 "expectedEveningQuantityLitres":%s}
                """.formatted(code, code, phone, villageId, collectionPointId,
                morningLitres, eveningLitres)));
    }

    public long createTanker(String code, String registration, String capacityLitres) throws Exception {
        return id(created("/api/v1/tankers", """
                {"tankerCode":"%s","registrationNumber":"%s","capacityLitres":%s}
                """.formatted(code, registration, capacityLitres)));
    }

    public long createChillingPlant(String code, double latitude, double longitude) throws Exception {
        return id(created("/api/v1/chilling-plants", """
                {"code":"%s","name":"%s plant","latitude":%s,"longitude":%s}
                """.formatted(code, code, latitude, longitude)));
    }

    public long createRoute(String code, String name) throws Exception {
        return id(created("/api/v1/routes", """
                {"routeCode":"%s","name":"%s"}
                """.formatted(code, name)));
    }

    public long createRouteVersion(long routeId) throws Exception {
        return id(created("/api/v1/routes/" + routeId + "/versions", "{}"));
    }

    public long createRouteVersionCopying(long routeId, long copyFromVersionId) throws Exception {
        return id(created("/api/v1/routes/" + routeId + "/versions",
                """
                        {"copyFromVersionId":%d}
                        """.formatted(copyFromVersionId)));
    }

    /** Adds stops in the given order, letting the server assign sequence numbers. */
    public ResultActions addStops(long routeId, long versionId, long... collectionPointIds)
            throws Exception {
        String stops = Arrays.stream(collectionPointIds)
                .mapToObj(id -> "{\"collectionPointId\":%d}".formatted(id))
                .collect(Collectors.joining(","));
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/routes/{r}/versions/{v}/stops", routeId, versionId)
                .contentType(APPLICATION_JSON)
                .content("{\"stops\":[" + stops + "]}"));
    }

    public ResultActions publishVersion(long routeId, long versionId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/routes/{r}/versions/{v}/publish", routeId, versionId));
    }

    /** Creates a route with one published version covering the given collection points. */
    public long publishedRouteVersion(String routeCode, long... collectionPointIds) throws Exception {
        long routeId = createRoute(routeCode, routeCode + " loop");
        long versionId = createRouteVersion(routeId);
        addStops(routeId, versionId, collectionPointIds).andExpect(status().isOk());
        publishVersion(routeId, versionId).andExpect(status().isOk());
        return versionId;
    }

    public ResultActions createRunRaw(long routeVersionId, long tankerId, long chillingPlantId,
                                      String runDate, String shift) throws Exception {
        return postJson("/api/v1/runs", """
                {"routeVersionId":%d,"tankerId":%d,"chillingPlantId":%d,"runDate":"%s","shift":"%s"}
                """.formatted(routeVersionId, tankerId, chillingPlantId, runDate, shift));
    }

    public long createRun(long routeVersionId, long tankerId, long chillingPlantId, String runDate,
                          String shift) throws Exception {
        return id(createRunRaw(routeVersionId, tankerId, chillingPlantId, runDate, shift)
                .andExpect(status().isCreated()));
    }

    public ResultActions startRun(long runId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/runs/{id}/start", runId));
    }

    public ResultActions cancelRun(long runId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/runs/{id}/cancel", runId));
    }

    public ResultActions completeRun(long runId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/runs/{id}/complete", runId));
    }

    public ResultActions arriveAtStop(long runId, long stopId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/runs/{id}/stops/{stopId}/arrive", runId, stopId));
    }

    public ResultActions completeStop(long runId, long stopId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/runs/{id}/stops/{stopId}/complete", runId, stopId));
    }

    public ResultActions skipStop(long runId, long stopId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                .post("/api/v1/runs/{id}/stops/{stopId}/skip", runId, stopId));
    }

    public ResultActions collect(long runId, long stopId, long farmerId, String litres)
            throws Exception {
        return postJson("/api/v1/runs/%d/stops/%d/collections".formatted(runId, stopId), """
                {"farmerId":%d,"quantityLitres":%s}
                """.formatted(farmerId, litres));
    }

    public ResultActions getRun(long runId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/runs/{id}", runId));
    }

    /** Id of the run stop at the given zero-based position in the run's stop order. */
    public long stopIdAt(long runId, int index) throws Exception {
        return json(getRun(runId).andExpect(status().isOk())).get("stops").get(index).get("id").asLong();
    }

    public ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(APPLICATION_JSON).content(body));
    }

    public ResultActions created(String path, String body) throws Exception {
        return postJson(path, body).andExpect(status().isCreated());
    }

    public long id(ResultActions actions) throws Exception {
        return json(actions).get("id").asLong();
    }

    public JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
