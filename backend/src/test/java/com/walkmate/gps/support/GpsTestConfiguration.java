package com.walkmate.gps.support;

import com.walkmate.application.tracking.TrackingCommandServiceV2;
import com.walkmate.application.tracking.TrackingQueryService;
import com.walkmate.application.tracking.TrackingSealService;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.tracking.TrackingChunkRepository;
import com.walkmate.domain.tracking.TrackingStreamRepository;
import com.walkmate.infrastructure.repository.session.WalkSessionJdbcRepository;
import com.walkmate.infrastructure.repository.tracking.TrackingChunkJdbcRepository;
import com.walkmate.infrastructure.repository.tracking.TrackingStreamJdbcRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Self-contained Spring configuration for the v2 GPS sync flow only.
 * Registered inside a plain {@code AnnotationConfigApplicationContext} (no
 * autoconfiguration) — the security stack is deliberately out of these tests:
 * the caller id is passed to the services, exactly as the controller would
 * pass {@code principal.userId()}.
 *
 * <p>{@link EnableTransactionManagement} is required because the v2 command
 * service is annotated {@code @Transactional}; without a proxy the
 * commit-or-proof guarantee these tests are written to verify would be
 * silently broken.</p>
 */
@Configuration
@EnableTransactionManagement
public class GpsTestConfiguration {

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(
            @Value("${test.datasource.url}") String url,
            @Value("${test.datasource.username}") String username,
            @Value("${test.datasource.password}") String password,
            @Value("${test.datasource.pool-name:gps-test}") String poolName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(10);
        ds.setPoolName(poolName);
        return ds;
    }

    @Bean
    public JdbcClient jdbcClient(HikariDataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(HikariDataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    // ── Repositories ──────────────────────────────────────────────────────────

    @Bean
    public WalkSessionRepository walkSessionRepository(JdbcClient jdbcClient) {
        return new WalkSessionJdbcRepository(jdbcClient);
    }

    @Bean
    public TrackingStreamRepository trackingStreamRepository(JdbcClient jdbcClient) {
        return new TrackingStreamJdbcRepository(jdbcClient);
    }

    @Bean
    public TrackingChunkRepository trackingChunkRepository(JdbcClient jdbcClient) {
        return new TrackingChunkJdbcRepository(jdbcClient);
    }

    // ── Application services under test ───────────────────────────────────────

    @Bean
    public TrackingCommandServiceV2 trackingCommandServiceV2(
            WalkSessionRepository sessionRepository,
            TrackingStreamRepository streamRepository,
            TrackingChunkRepository chunkRepository) {
        return new TrackingCommandServiceV2(sessionRepository, streamRepository, chunkRepository);
    }

    @Bean
    public TrackingSealService trackingSealService(TrackingStreamRepository streamRepository) {
        return new TrackingSealService(streamRepository);
    }

    @Bean
    public TrackingQueryService trackingQueryService(
            WalkSessionRepository sessionRepository,
            TrackingChunkRepository chunkRepository) {
        return new TrackingQueryService(sessionRepository, chunkRepository);
    }}
