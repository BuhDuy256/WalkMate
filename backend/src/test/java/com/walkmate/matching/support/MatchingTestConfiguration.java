package com.walkmate.matching.support;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.shared.MatchingTransactionExecutor;
import com.walkmate.application.walkintent.RuleBasedMatchingStrategy;
import com.walkmate.application.walkintent.WalkIntentCommandService;
import com.walkmate.domain.chat.ChatRoomRepository;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.proposal.MatchProposalRepository;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.NotificationPublisher;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.infrastructure.lock.UserCalendarLockManager;
import com.walkmate.infrastructure.repository.hotspot.HotspotJdbcRepository;
import com.walkmate.infrastructure.repository.proposal.MatchProposalJdbcRepository;
import com.walkmate.infrastructure.repository.session.WalkSessionJdbcRepository;
import com.walkmate.infrastructure.repository.social.SocialJdbcRepository;
import com.walkmate.infrastructure.repository.user.UserJdbcRepository;
import com.walkmate.infrastructure.repository.walkintent.WalkIntentJdbcRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/**
 * Self-contained Spring configuration for the matching flow only. Each instance of this
 * configuration is an independent "application instance": its own connection pool,
 * transaction manager and service graph. Loading it twice against the same PostgreSQL
 * database simulates two deployed instances competing for the same data.
 *
 * <p>Firebase / MongoDB / Cloudinary / SMTP and the web/security stack are deliberately
 * excluded; {@link NotificationPublisher} and {@link ChatRoomRepository} are no-op stubs.</p>
 */
@Configuration
public class MatchingTestConfiguration {

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(
            @Value("${test.datasource.url}") String url,
            @Value("${test.datasource.username}") String username,
            @Value("${test.datasource.password}") String password,
            @Value("${test.datasource.pool-name:walkmate-test}") String poolName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(20);
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

    @Bean
    public MatchingTransactionExecutor matchingTransactionExecutor(
            TransactionTemplate transactionTemplate, JdbcClient jdbcClient) {
        return new MatchingTransactionExecutor(
                transactionTemplate,
                jdbcClient,
                5,
                Duration.ofMillis(20),
                Duration.ofMillis(200),
                Duration.ofMillis(2000),
                Duration.ofMillis(10000));
    }

    // ── Repositories ──────────────────────────────────────────────────────────

    @Bean
    public WalkIntentRepository walkIntentRepository(JdbcClient jdbcClient) {
        return new WalkIntentJdbcRepository(jdbcClient);
    }

    @Bean
    public MatchProposalRepository matchProposalRepository(JdbcClient jdbcClient) {
        return new MatchProposalJdbcRepository(jdbcClient);
    }

    @Bean
    public WalkSessionRepository walkSessionRepository(JdbcClient jdbcClient) {
        return new WalkSessionJdbcRepository(jdbcClient);
    }

    @Bean
    public HotspotRepository hotspotRepository(JdbcClient jdbcClient) {
        return new HotspotJdbcRepository(jdbcClient);
    }

    @Bean
    public SocialRepository socialRepository(JdbcClient jdbcClient) {
        return new SocialJdbcRepository(jdbcClient);
    }

    @Bean
    public UserRepository userRepository(JdbcClient jdbcClient) {
        return new UserJdbcRepository(jdbcClient);
    }

    // ── Stubs for out-of-scope ports ──────────────────────────────────────────

    @Bean
    public NotificationPublisher notificationPublisher() {
        return notification -> { /* no-op in matching-flow tests */ };
    }

    @Bean
    public ChatRoomRepository chatRoomRepository() {
        return new ChatRoomRepository() {
            @Override
            public void initRoom(String sessionId) { /* no-op */ }

            @Override
            public void closeRoom(String sessionId) { /* no-op */ }
        };
    }

    // ── Application services ──────────────────────────────────────────────────

    @Bean
    public RuleBasedMatchingStrategy matchingStrategy(
            WalkIntentRepository walkIntentRepository,
            SocialRepository socialRepository,
            UserRepository userRepository) {
        return new RuleBasedMatchingStrategy(walkIntentRepository, socialRepository, userRepository);
    }

    @Bean
    public UserCalendarLockManager userCalendarLockManager(JdbcClient jdbcClient) {
        return new UserCalendarLockManager(jdbcClient);
    }

    @Bean
    public MatchingCommandService matchingCommandService(
            WalkIntentRepository walkIntentRepository,
            MatchProposalRepository matchProposalRepository,
            WalkSessionRepository walkSessionRepository,
            HotspotRepository hotspotRepository,
            RuleBasedMatchingStrategy matchingStrategy,
            NotificationPublisher notificationPublisher,
            ChatRoomRepository chatRoomRepository,
            UserCalendarLockManager userCalendarLockManager,
            MatchingTransactionExecutor matchingTransactionExecutor) {
        return new MatchingCommandService(
                walkIntentRepository,
                matchProposalRepository,
                walkSessionRepository,
                hotspotRepository,
                matchingStrategy,
                notificationPublisher,
                chatRoomRepository,
                userCalendarLockManager,
                matchingTransactionExecutor);
    }

    @Bean
    public WalkIntentCommandService walkIntentCommandService(
            WalkIntentRepository walkIntentRepository,
            HotspotRepository hotspotRepository,
            WalkSessionRepository walkSessionRepository,
            SocialRepository socialRepository,
            MatchingCommandService matchingCommandService,
            MatchProposalRepository matchProposalRepository,
            NotificationPublisher notificationPublisher,
            UserCalendarLockManager userCalendarLockManager,
            MatchingTransactionExecutor matchingTransactionExecutor) {
        return new WalkIntentCommandService(
                walkIntentRepository,
                hotspotRepository,
                walkSessionRepository,
                socialRepository,
                matchingCommandService,
                matchProposalRepository,
                notificationPublisher,
                userCalendarLockManager,
                matchingTransactionExecutor);
    }
}
