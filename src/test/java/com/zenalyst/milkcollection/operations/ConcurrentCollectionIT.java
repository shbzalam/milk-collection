package com.zenalyst.milkcollection.operations;

import com.zenalyst.milkcollection.collection.dto.RecordMilkCollectionRequest;
import com.zenalyst.milkcollection.collection.service.MilkCollectionService;
import com.zenalyst.milkcollection.exception.BusinessRuleException;
import com.zenalyst.milkcollection.exception.ErrorCode;
import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two field devices posting milk for the same tanker at the same instant.
 *
 * <p>The service is called directly from two threads rather than through MockMvc, because the
 * point of the test is the transaction and the database lock, and each thread must run its own
 * transaction on its own connection. The test class is deliberately not {@code @Transactional}:
 * a test-managed rollback would hide the very interleaving being tested.
 *
 * <p>Expected outcome: the pessimistic write lock on the run row serialises the two
 * transactions, so the second one sees the first one's litres and is rejected. Both succeeding
 * would mean an overfilled tanker and rejected milk at the plant.
 */
class ConcurrentCollectionIT extends IntegrationTestBase {

    private static final String RUN_DATE = "2026-09-09";

    @Autowired
    private MilkCollectionService milkCollectionService;

    private long farmerA;
    private long farmerB;
    private long runId;
    private long stopId;

    @BeforeEach
    void setUpArrivedStop() throws Exception {
        long villageId = api.createVillage("V-001", 18.50, 73.80);
        long plantId = api.createChillingPlant("PLANT-01", 18.50, 73.80);
        long sharedPoint = api.createCollectionPoint("CP-001", villageId, 18.55, 73.80);

        // Two farmers at one collection point, each expected to bring 40 L.
        farmerA = api.createFarmer("F-0001", "9000000001", villageId, sharedPoint, "40", "40");
        farmerB = api.createFarmer("F-0002", "9000000002", villageId, sharedPoint, "40", "40");

        // 100 L tanker: 80 L expected, so the run is feasible, but 80 + 60 actual is not.
        long tankerId = api.createTanker("TNK-01", "MH12AA0001", "100");
        long routeVersionId = api.publishedRouteVersion("R-001", sharedPoint);

        runId = api.createRun(routeVersionId, tankerId, plantId, RUN_DATE, "MORNING");
        api.startRun(runId).andExpect(status().isOk());
        stopId = api.stopIdAt(runId, 0);
        api.arriveAtStop(runId, stopId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("80 L and 60 L into a 100 L tanker: exactly one succeeds")
    void serialisesConcurrentCollectionsAgainstTankerCapacity() throws Exception {
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = executor.submit(
                    () -> attemptCollection(startLine, farmerA, "80.00"));
            Future<Outcome> second = executor.submit(
                    () -> attemptCollection(startLine, farmerB, "60.00"));

            startLine.countDown();
            List<Outcome> outcomes = List.of(first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(Outcome::succeeded)
                    .describedAs("exactly one of the two collections must be accepted")
                    .hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                    .singleElement()
                    .satisfies(rejected -> assertThat(rejected.errorCode())
                            .isEqualTo(ErrorCode.TANKER_CAPACITY_EXCEEDED));
        } finally {
            executor.shutdownNow();
        }

        // Whichever won, the tanker is within capacity and only one row was written.
        BigDecimal collected = jdbcTemplate.queryForObject("""
                select coalesce(sum(mc.quantity_litres), 0)
                  from milk_collection mc
                  join run_stop rs on rs.id = mc.run_stop_id
                 where rs.collection_run_id = ?
                """, BigDecimal.class, runId);
        assertThat(collected).isLessThanOrEqualTo(new BigDecimal("100.00"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from milk_collection", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("two collections that both fit are both accepted")
    void acceptsConcurrentCollectionsThatBothFit() throws Exception {
        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = executor.submit(
                    () -> attemptCollection(startLine, farmerA, "40.00"));
            Future<Outcome> second = executor.submit(
                    () -> attemptCollection(startLine, farmerB, "40.00"));

            startLine.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .allMatch(Outcome::succeeded);
        } finally {
            executor.shutdownNow();
        }

        api.getRun(runId).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.load.collectedLitres").value(80.00));
    }

    private Outcome attemptCollection(CountDownLatch startLine, long farmerId, String litres)
            throws InterruptedException {
        startLine.await(10, TimeUnit.SECONDS);
        try {
            milkCollectionService.record(runId, stopId,
                    new RecordMilkCollectionRequest(farmerId, new BigDecimal(litres)));
            return new Outcome(true, null);
        } catch (BusinessRuleException e) {
            return new Outcome(false, e.errorCode());
        }
    }

    private record Outcome(boolean succeeded, ErrorCode errorCode) {
    }
}
