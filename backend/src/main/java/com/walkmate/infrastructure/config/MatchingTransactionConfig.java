package com.walkmate.infrastructure.config;

import com.walkmate.application.shared.MatchingTransactionExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * Wires the matching-flow retry executor with configurable concurrency timeouts.
 *
 * <p>Values are placeholders tuned for correctness, not measured contention. They can be
 * overridden per environment via the {@code walkmate.matching.*} properties.</p>
 */
@Configuration
public class MatchingTransactionConfig {

    @Bean
    public MatchingTransactionExecutor matchingTransactionExecutor(
            TransactionTemplate transactionTemplate,
            JdbcClient jdbcClient,
            @Value("${walkmate.matching.max-attempts:3}") int maxAttempts,
            @Value("${walkmate.matching.base-backoff-ms:100}") long baseBackoffMs,
            @Value("${walkmate.matching.max-backoff-ms:1000}") long maxBackoffMs,
            @Value("${walkmate.matching.lock-timeout-ms:2000}") long lockTimeoutMs,
            @Value("${walkmate.matching.statement-timeout-ms:5000}") long statementTimeoutMs) {
        return new MatchingTransactionExecutor(
                transactionTemplate,
                jdbcClient,
                maxAttempts,
                Duration.ofMillis(baseBackoffMs),
                Duration.ofMillis(maxBackoffMs),
                Duration.ofMillis(lockTimeoutMs),
                Duration.ofMillis(statementTimeoutMs));
    }
}
