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
