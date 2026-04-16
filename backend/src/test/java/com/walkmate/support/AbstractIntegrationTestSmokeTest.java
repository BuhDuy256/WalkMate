package com.walkmate.support;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AbstractIntegrationTest} boots successfully:
 * containers start, Flyway migrations run, and the database is reachable.
 *
 * This test is the P0-2 acceptance criterion and will be removed once
 * a real Phase-1 test class inherits from AbstractIntegrationTest.
 */
class AbstractIntegrationTestSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads_andDatabaseIsReachable() {
        // Flyway ran all migrations — user_account table must exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.user_account", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void truncate_leavesFlywayHistoryIntact() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(migrationCount).isPositive();
    }
}
