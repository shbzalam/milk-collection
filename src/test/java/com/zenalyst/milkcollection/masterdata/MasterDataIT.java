package com.zenalyst.milkcollection.masterdata;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Master data over the real HTTP layer and a real PostgreSQL schema. Also proves that
 * {@code spring.jpa.hibernate.ddl-auto=validate} accepts the Flyway migrations - the context
 * would not start otherwise.
 */
class MasterDataIT extends IntegrationTestBase {

    @Test
    @DisplayName("two farmers can share one collection point")
    void twoFarmersShareOneCollectionPoint() throws Exception {
        long villageId = createVillage("V-001", "Shirur");
        long pointId = createCollectionPoint("CP-001", villageId);

        createFarmer("F-0001", "Ramesh Pawar", "9876543210", villageId, pointId);
        createFarmer("F-0002", "Sunita Jadhav", "9876500000", villageId, pointId);

        mockMvc.perform(get("/api/v1/collection-points/{id}", pointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CP-001"))
                .andExpect(jsonPath("$.village.code").value("V-001"))
                .andExpect(jsonPath("$.farmerCount").value(2));

        mockMvc.perform(get("/api/v1/farmers").param("collectionPointId", String.valueOf(pointId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].collectionPoint.code").value("CP-001"));
    }

    @Test
    @DisplayName("farmers are searchable by phone number for incoming calls")
    void findsFarmerByPhone() throws Exception {
        long villageId = createVillage("V-002", "Kendur");
        long pointId = createCollectionPoint("CP-002", villageId);
        createFarmer("F-0003", "Ganesh More", "9998887770", villageId, pointId);
        createFarmer("F-0004", "Anita Kale", "9998887771", villageId, pointId);

        mockMvc.perform(get("/api/v1/farmers").param("phone", "9998887771"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].farmerCode").value("F-0004"));
    }

    @Test
    @DisplayName("unfiltered listing still works (all optional filters null)")
    void listsWithoutFilters() throws Exception {
        long villageId = createVillage("V-003", "Pabal");
        long pointId = createCollectionPoint("CP-003", villageId);
        createFarmer("F-0005", "Vijay Shinde", "9111111111", villageId, pointId);

        mockMvc.perform(get("/api/v1/farmers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void rejectsDuplicateVillageCode() throws Exception {
        createVillage("V-010", "Talegaon");

        mockMvc.perform(post("/api/v1/villages").contentType(APPLICATION_JSON).content("""
                        {"code":"V-010","name":"Talegaon again","latitude":18.8,"longitude":74.3}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.path").value("/api/v1/villages"));
    }

    @Test
    void rejectsDuplicateTankerRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/tankers").contentType(APPLICATION_JSON).content("""
                        {"tankerCode":"TNK-01","registrationNumber":"MH12AB1234","capacityLitres":5000}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/tankers").contentType(APPLICATION_JSON).content("""
                        {"tankerCode":"TNK-02","registrationNumber":"MH12AB1234","capacityLitres":4000}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("a farmer cannot be attached to an inactive collection point")
    void rejectsInactiveCollectionPoint() throws Exception {
        long villageId = createVillage("V-011", "Nimgaon");
        long pointId = asId(mockMvc.perform(post("/api/v1/collection-points")
                        .contentType(APPLICATION_JSON).content("""
                                {"code":"CP-011","name":"Closed point","villageId":%d,
                                 "latitude":18.81,"longitude":74.31,"status":"INACTIVE"}
                                """.formatted(villageId)))
                .andExpect(status().isCreated()));

        mockMvc.perform(post("/api/v1/farmers").contentType(APPLICATION_JSON).content("""
                        {"farmerCode":"F-0099","name":"Late Joiner","phone":"9000000000",
                         "villageId":%d,"collectionPointId":%d,
                         "expectedMorningQuantityLitres":10,"expectedEveningQuantityLitres":10}
                        """.formatted(villageId, pointId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INACTIVE_RESOURCE"));
    }

    @Test
    @DisplayName("bean validation failures return field-level detail")
    void reportsFieldValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/villages").contentType(APPLICATION_JSON).content("""
                        {"code":"","name":"No coordinates","latitude":200,"longitude":74.3}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("latitude"));
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/farmers/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // --- helpers -----------------------------------------------------------------

    private long createVillage(String code, String name) throws Exception {
        return asId(mockMvc.perform(post("/api/v1/villages").contentType(APPLICATION_JSON).content("""
                        {"code":"%s","name":"%s","latitude":18.8237,"longitude":74.3732}
                        """.formatted(code, name)))
                .andExpect(status().isCreated()));
    }

    private long createCollectionPoint(String code, long villageId) throws Exception {
        return asId(mockMvc.perform(post("/api/v1/collection-points")
                        .contentType(APPLICATION_JSON).content("""
                                {"code":"%s","name":"%s point","villageId":%d,
                                 "latitude":18.8240,"longitude":74.3740}
                                """.formatted(code, code, villageId)))
                .andExpect(status().isCreated()));
    }

    private void createFarmer(String code, String name, String phone, long villageId, long pointId)
            throws Exception {
        mockMvc.perform(post("/api/v1/farmers").contentType(APPLICATION_JSON).content("""
                        {"farmerCode":"%s","name":"%s","phone":"%s","villageId":%d,
                         "collectionPointId":%d,
                         "expectedMorningQuantityLitres":50.00,"expectedEveningQuantityLitres":40.00}
                        """.formatted(code, name, phone, villageId, pointId)))
                .andExpect(status().isCreated());
    }

    private long asId(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }
}
