package com.zenalyst.milkcollection.masterdata;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the demo dataset is coherent and usable, so that {@code docker compose up} gives a
 * reviewer a system they can drive immediately.
 *
 * <p>The seed script is executed here directly rather than through the demo profile, because
 * the base class truncates the schema before every test. It is the same file Flyway applies,
 * and running it twice also asserts that it really is idempotent.
 */
class SeedDataIT extends IntegrationTestBase {

    @BeforeEach
    void loadSeedData() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/seed/R__seed_data.sql"));
        populator.execute(jdbcTemplate.getDataSource());
    }

    @Test
    @DisplayName("the dataset loads with the expected shape")
    void loadsACoherentNetwork() {
        assertThat(count("village")).isEqualTo(5);
        assertThat(count("collection_point")).isEqualTo(8);
        assertThat(count("farmer")).isEqualTo(16);
        assertThat(count("tanker")).isEqualTo(3);
        assertThat(count("chilling_plant")).isEqualTo(1);
        assertThat(count("route")).isEqualTo(2);
        assertThat(count("route_version")).isEqualTo(2);
        assertThat(count("route_stop")).isEqualTo(8);
    }

    @Test
    @DisplayName("re-running the seed changes nothing")
    void isIdempotent() {
        loadSeedData();
        loadSeedData();

        assertThat(count("farmer")).isEqualTo(16);
        assertThat(count("route_stop")).isEqualTo(8);
    }

    @Test
    @DisplayName("the dataset includes collection points shared by several farmers")
    void includesSharedCollectionPoints() throws Exception {
        Long sharedPoints = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select collection_point_id from farmer
                     group by collection_point_id having count(*) > 1) shared
                """, Long.class);
        assertThat(sharedPoints).isGreaterThanOrEqualTo(2L);

        Long busiestPointId = jdbcTemplate.queryForObject(
                "select id from collection_point where code = 'CP-001'", Long.class);
        mockMvc.perform(get("/api/v1/collection-points/{id}", busiestPointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmerCount").value(3));
    }

    @Test
    @DisplayName("both seeded routes are published and can be run without hitting a constraint")
    void seededRoutesArePublishedAndFeasible() throws Exception {
        mockMvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        long plantId = idOf("select id from chilling_plant where code = 'PLANT-SHIRUR'");
        long tankerId = idOf("select id from tanker where tanker_code = 'TNK-01'");

        // Creating a run applies the capacity and holding-time feasibility checks, so a run
        // created successfully against the seeded plan proves the plan is actually drivable.
        long morningVersion = idOf("""
                select rv.id from route_version rv
                  join route r on r.id = rv.route_id
                 where r.route_code = 'R-M01' and rv.status = 'PUBLISHED'
                """);
        api.createRunRaw(morningVersion, tankerId, plantId, "2026-09-09", "MORNING")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stops.length()").value(5))
                .andExpect(jsonPath("$.stops[0].collectionPoint.code").value("CP-005"))
                .andExpect(jsonPath("$.load.collectedLitres").value(0.00));

        long eveningVersion = idOf("""
                select rv.id from route_version rv
                  join route r on r.id = rv.route_id
                 where r.route_code = 'R-E01' and rv.status = 'PUBLISHED'
                """);
        long secondTanker = idOf("select id from tanker where tanker_code = 'TNK-02'");
        api.createRunRaw(eveningVersion, secondTanker, plantId, "2026-09-09", "EVENING")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stops.length()").value(3));
    }

    @Test
    @DisplayName("a tanker under maintenance cannot be sent out")
    void refusesTheTankerUnderMaintenance() throws Exception {
        long plantId = idOf("select id from chilling_plant where code = 'PLANT-SHIRUR'");
        long inMaintenance = idOf("select id from tanker where tanker_code = 'TNK-03'");
        long version = idOf("""
                select rv.id from route_version rv
                  join route r on r.id = rv.route_id
                 where r.route_code = 'R-M01'
                """);

        api.createRunRaw(version, inMaintenance, plantId, "2026-09-09", "MORNING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INACTIVE_RESOURCE"));
    }

    @Test
    @DisplayName("the optimizer can plan the whole seeded network")
    void optimisesTheSeededNetwork() throws Exception {
        long plantId = idOf("select id from chilling_plant where code = 'PLANT-SHIRUR'");

        api.postJson("/api/v1/planning/optimize", """
                        {"chillingPlantId":%d,"shift":"MORNING"}
                        """.formatted(plantId))
                .andExpect(status().isOk())
                // Two active tankers, eight collection points, all of them coverable.
                .andExpect(jsonPath("$.plan.summary.collectionPointsConsidered").value(8))
                .andExpect(jsonPath("$.plan.summary.collectionPointsUnassigned").value(0))
                .andExpect(jsonPath("$.plan.summary.farmersCovered").value(16))
                .andExpect(jsonPath("$.plan.summary.tankersAvailable").value(2))
                .andExpect(jsonPath("$.plan.unassigned.length()").value(0));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private long idOf(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
