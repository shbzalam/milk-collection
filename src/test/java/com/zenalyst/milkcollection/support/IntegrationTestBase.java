package com.zenalyst.milkcollection.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * Base for integration tests: real Spring context, real PostgreSQL, real Flyway migrations.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. Capacity accumulation, duplicate-collection
 * protection and the concurrency test all depend on data actually being committed, which a
 * test-managed rollback would hide. Isolation comes from truncating the schema before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                 where table_schema = 'public'
                   and table_type = 'BASE TABLE'
                   and table_name <> 'flyway_schema_history'
                """, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("truncate table "
                    + String.join(", ", tables) + " restart identity cascade");
        }
    }
}
