package com.zenalyst.milkcollection.operations;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the heaviest read path against N+1 regressions.
 *
 * <p>{@code GET /runs/{id}} embeds a route version, a route, a tanker, a plant, every stop with
 * its collection point, and every collection with its farmer. Done naively that is one query per
 * stop and per collection. It is instead a fixed number of queries, and this test pins that down
 * with Hibernate's own statistics so a future change that drops a fetch join fails here rather
 * than degrading quietly in production.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class RunDetailQueryCountIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long runId;
    private int stopCount;

    @BeforeEach
    void setUpRunWithSeveralStopsAndCollections() throws Exception {
        long villageId = api.createVillage("V-001", 18.50, 73.80);
        long plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        long tankerId = api.createTanker("TNK-01", "MH12AA0001", "5000");

        // Five stops, two farmers at each: ten collections across five stops.
        long[] points = new long[5];
        long[][] farmers = new long[5][2];
        for (int i = 0; i < 5; i++) {
            points[i] = api.createCollectionPoint("CP-00" + (i + 1), villageId, 18.52 + i * 0.01, 73.80);
            for (int j = 0; j < 2; j++) {
                farmers[i][j] = api.createFarmer("F-%d%d".formatted(i, j),
                        "90000000%d%d".formatted(i, j), villageId, points[i], "20", "20");
            }
        }
        stopCount = points.length;

        long versionId = api.publishedRouteVersion("R-001", points);
        runId = api.createRun(versionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.startRun(runId).andExpect(status().isOk());
        for (int i = 0; i < stopCount; i++) {
            long stopId = api.stopIdAt(runId, i);
            api.arriveAtStop(runId, stopId).andExpect(status().isOk());
            for (int j = 0; j < 2; j++) {
                api.collect(runId, stopId, farmers[i][j], "20.00").andExpect(status().isCreated());
            }
            api.completeStop(runId, stopId).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a run detail costs a fixed number of queries regardless of its size")
    void runDetailDoesNotScaleWithStopsOrCollections() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        api.getRun(runId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops.length()").value(stopCount))
                .andExpect(jsonPath("$.stops[0].collections.length()").value(2))
                .andExpect(jsonPath("$.load.collectedLitres").value(200.00));

        long queries = statistics.getPrepareStatementCount();

        // Four: the run with its four references, the stops with their collection points, the
        // collections with their farmers, and the capacity sum. With 5 stops and 10 collections,
        // an N+1 would show up here as 15+ extra statements.
        assertThat(queries)
                .describedAs("queries for a run with %d stops and 10 collections", stopCount)
                .isLessThanOrEqualTo(5);
    }
}
