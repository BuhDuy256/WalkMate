package com.walkmate.matching.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared Testcontainers PostgreSQL with the real Flyway migrations applied, plus concurrency
 * helpers. One container is shared across all tests in a class; two {@link MatchingTestInstance}
 * contexts can be started against it to simulate two deployed application instances.
 */
public abstract class PostgresMatchingTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("walkmate")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    protected static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static JdbcClient rawClient() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return JdbcClient.create(ds);
    }

    /** A captured concurrent outcome: either a value or the exception it threw. */
    public record Outcome<T>(T value, Throwable error) {
        public boolean succeeded() {
            return error == null;
        }
    }

    /**
     * Runs {@code task} {@code times} times concurrently, releasing all threads at the same
     * instant via a barrier. Returns one {@link Outcome} per thread.
     */
    protected static <T> List<Outcome<T>> runConcurrently(int times, Supplier<T> task) throws Exception {
        List<Supplier<T>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    /** Runs each supplied task on its own thread, releasing all at once via a barrier. */
    @SafeVarargs
    protected static <T> List<Outcome<T>> runConcurrently(Supplier<T>... tasks) throws Exception {
        return runConcurrently(List.of(tasks));
    }

    /** Runs each supplied task on its own thread, releasing all at once via a barrier. */
    protected static <T> List<Outcome<T>> runConcurrently(List<Supplier<T>> tasks) throws Exception {
        int times = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(times);
        CountDownLatch ready = new CountDownLatch(times);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Outcome<T>>> callables = new ArrayList<>();
        for (Supplier<T> task : tasks) {
            callables.add(() -> {
                ready.countDown();
                start.await();
                try {
                    return new Outcome<>(task.get(), null);
                } catch (Throwable t) {
                    return new Outcome<>(null, t);
                }
            });
        }

        List<Future<Outcome<T>>> futures = new ArrayList<>();
        for (Callable<Outcome<T>> c : callables) {
            futures.add(pool.submit(c));
        }

        if (!ready.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Threads did not reach the barrier");
        }
        start.countDown();

        List<Outcome<T>> results = new ArrayList<>();
        for (Future<Outcome<T>> f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }
}
