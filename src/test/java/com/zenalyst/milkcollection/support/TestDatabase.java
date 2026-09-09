package com.zenalyst.milkcollection.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides the PostgreSQL instance used by integration tests.
 *
 * <p>One container is started per JVM and reused by every {@code *IT} class - starting a
 * container per test class would dominate the suite runtime for no isolation benefit, because
 * {@link IntegrationTestBase} truncates the schema before each test.
 *
 * <p>Integration tests run against the same major version as production. Requires a running
 * Docker daemon; see the README.
 */
public final class TestDatabase {

    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("milk_collection")
                    .withUsername("milk")
                    .withPassword("milk");

    private TestDatabase() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
    }
}
