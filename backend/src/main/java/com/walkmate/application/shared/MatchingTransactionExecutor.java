package com.walkmate.application.shared;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Executes a matching-flow operation with a fresh transaction per attempt and bounded
 * retries for transient concurrency failures.
 *
 * <p>Retry is triggered only for:</p>
 * <ul>
 *   <li>PostgreSQL SQLSTATE {@code 40P01} (deadlock detected)</li>
 *   <li>PostgreSQL SQLSTATE {@code 55P03} (lock_not_available / lock timeout)</li>
 *   <li>PostgreSQL SQLSTATE {@code 40001} (serialization failure)</li>
 *   <li>{@link RetryableConcurrencyException} (state changed mid-attempt — re-evaluate)</li>
 * </ul>
 *
 * <p>Business validation failures ({@link DomainException}) are never retried and always
 * propagate immediately. Each attempt runs inside {@link TransactionTemplate#execute},
 * which provides a brand-new transaction; a failed attempt is fully rolled back before the
 * next attempt begins.</p>
 */
@Slf4j
public class MatchingTransactionExecutor {

    private static final Set<String> RETRYABLE_SQL_STATES = Set.of("40P01", "55P03", "40001");

    private final TransactionTemplate transactionTemplate;
    private final JdbcClient jdbcClient;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final Duration lockTimeout;
    private final Duration statementTimeout;
    private final Random random = new Random();

    public MatchingTransactionExecutor(TransactionTemplate transactionTemplate,
                                       JdbcClient jdbcClient,
                                       int maxAttempts,
                                       Duration baseBackoff,
                                       Duration maxBackoff,
                                       Duration lockTimeout,
                                       Duration statementTimeout) {
        this.transactionTemplate = transactionTemplate;
        this.jdbcClient = jdbcClient;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
        this.lockTimeout = lockTimeout;
        this.statementTimeout = statementTimeout;
    }

    public <T> T execute(Supplier<T> action) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return transactionTemplate.execute(status -> {
                    applyTransactionTimeouts();
                    return action.get();
                });
            } catch (Exception ex) {
                if (attempt < maxAttempts && isRetryable(ex)) {
                    log.warn("Matching operation transient conflict (attempt {}/{}): {}",
                            attempt, maxAttempts, rootMessage(ex));
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw ex instanceof RuntimeException rte ? rte : new IllegalStateException(ex);
            }
        }
    }

    /** Runs without retries — used for operations that must not repeat side effects. */
    public <T> T executeNoRetry(Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            applyTransactionTimeouts();
            return action.get();
        });
    }

    private void applyTransactionTimeouts() {
        // SET does not accept bind parameters in PostgreSQL, so inline the numeric value
        // (a config-provided long — no injection risk).
        if (lockTimeout != null && !lockTimeout.isNegative()) {
            jdbcClient.sql("SET LOCAL lock_timeout = " + Math.max(1, lockTimeout.toMillis()))
                    .update();
        }
        if (statementTimeout != null && !statementTimeout.isNegative()) {
            jdbcClient.sql("SET LOCAL statement_timeout = " + Math.max(1, statementTimeout.toMillis()))
                    .update();
        }
    }

    private boolean isRetryable(Throwable ex) {
        if (ex instanceof RetryableConcurrencyException) {
            return true;
        }
        String sqlState = extractSqlState(ex);
        return sqlState != null && RETRYABLE_SQL_STATES.contains(sqlState);
    }

    private String extractSqlState(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlEx && sqlEx.getSQLState() != null) {
                return sqlEx.getSQLState();
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private void sleepBeforeRetry(int attempt) {
        long backoff = baseBackoff.toMillis() * (1L << Math.min(attempt - 1, 10));
        backoff = Math.min(backoff, maxBackoff.toMillis());
        long jittered = backoff > 0 ? backoff / 2 + random.nextLong(backoff / 2 + 1) : 0;
        if (jittered > 0) {
            try {
                Thread.sleep(jittered);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during matching retry backoff", ie);
            }
        }
    }

    private String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String msg = cursor.getMessage();
        return msg == null ? cursor.getClass().getSimpleName() : msg;
    }
}
