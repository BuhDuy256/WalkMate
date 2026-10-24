package com.walkmate.matching;

import com.walkmate.application.shared.MatchingTransactionExecutor;
import com.walkmate.application.shared.RetryableConcurrencyException;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.matching.support.PostgresMatchingTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for bounded retry: fresh transaction per attempt, retry only on transient
 * SQLSTATEs / {@link RetryableConcurrencyException}, and never on business validation
 * failures.
 */
class MatchingTransactionExecutorTest extends PostgresMatchingTestBase {

    private static MatchingTransactionExecutor executor;

    @BeforeAll
    static void buildExecutor() {
        DriverManagerDataSource ds = new DriverManagerDataSource(jdbcUrl(), "test", "test");
        ds.setDriverClassName("org.postgresql.Driver");
        TransactionTemplate tt = new TransactionTemplate(new DataSourceTransactionManager(ds));
        JdbcClient jdbcClient = JdbcClient.create(ds);
        executor = new MatchingTransactionExecutor(tt, jdbcClient, 3,
                Duration.ZERO, Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(10));
    }

    @Test
    void retriesRetryableConcurrencyExceptionUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RetryableConcurrencyException("conflict");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryBusinessValidationFailure() {
        AtomicInteger calls = new AtomicInteger();
        DomainException expected = new DomainException(WalkIntentErrorCode.INTENT_OVERLAPPING);
        DomainException thrown = assertThrows(DomainException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw expected;
        }));
        assertSame(expected, thrown);
        assertEquals(1, calls.get());
    }

    @Test
    void retriesDeadlockSqlState() {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 2) {
                throw new IllegalStateException("deadlock", new SQLException("deadlock", "40P01"));
            }
            return "recovered";
        });
        assertEquals("recovered", result);
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryUnrelatedSqlState() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom", new SQLException("boom", "23505")); // unique violation
        }));
        assertNotNull(thrown);
        assertEquals(1, calls.get());
    }

    @Test
    void exhaustionPropagatesLastConflict() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RetryableConcurrencyException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw new RetryableConcurrencyException("still conflicting");
        }));
        assertEquals(3, calls.get(), "bounded at maxAttempts");
    }
}
