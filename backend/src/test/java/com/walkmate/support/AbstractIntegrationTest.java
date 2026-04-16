package com.walkmate.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.walkmate.application.user.GoogleIdentity;
import com.walkmate.application.user.GoogleTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>A singleton {@link PostgreSQLContainer} running the {@code pgvector/pgvector:pg16}
 *       image — required because V1 migration uses {@code CREATE EXTENSION vector}.</li>
 *   <li>A singleton {@link MongoDBContainer} (Mongo 7) for the chat/tracking layer.</li>
 *   <li>Firebase beans ({@link FirebaseApp}, {@link FirebaseAuth}, {@link FirebaseMessaging})
 *       replaced with Mockito mocks so {@code FirebaseConfig} never attempts real
 *       credential loading during tests.</li>
 *   <li>A {@code @BeforeEach} that truncates all application tables with CASCADE,
 *       guaranteeing full test isolation between methods.</li>
 * </ul>
 *
 * <p>Containers are started in a {@code static} initialiser (not via {@code @Container})
 * so they live for the entire JVM lifetime and are shared across ALL subclasses.
 * Using {@code @Container} on an abstract class ties lifecycle to each concrete class,
 * causing containers to stop and restart between test classes. Ryuk shuts them down
 * when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    // ── Containers (singleton — started once per JVM, shared across all test classes) ──

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("walkmate_test")
                    .withUsername("test")
                    .withPassword("test");

    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    static {
        postgres.start();
        mongo.start();
    }

    // ── Property overrides ─────────────────────────────────────────────────────

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.mongodb.uri",    mongo::getReplicaSetUrl);
        // Minimal JWT secret that satisfies the 32-char HS256 requirement
        registry.add("app.jwt.secret", () -> "integration-test-secret-that-is-32c!!");
    }

    // ── Firebase mocks ─────────────────────────────────────────────────────────
    // @MockitoBean registers these at the bean-definition level, which prevents
    // FirebaseConfig's @Bean methods from executing — no real Firebase call is made.

    @MockitoBean
    FirebaseApp firebaseApp;

    @MockitoBean
    FirebaseAuth firebaseAuth;

    /**
     * FCM mock — kept {@code protected} so subclass tests can assert invocation
     * counts and captured arguments without reaching through the bean context.
     *
     * <p>Example assertion in a test method:
     * <pre>
     *   verify(firebaseMessaging, times(1)).send(any(Message.class));
     * </pre>
     */
    @MockitoBean
    protected FirebaseMessaging firebaseMessaging;

    /**
     * Google OAuth token verifier mock — {@code protected} so tests can stub
     * {@code verify()} to return a controlled {@link GoogleIdentity}.
     *
     * <p>Replaces the real {@link com.walkmate.infrastructure.security.oauth.FirebaseTokenVerifier}
     * bean entirely — no real Firebase Admin SDK call is ever made.
     *
     * <p>Example stub in a test method:
     * <pre>
     *   when(googleTokenVerifier.verify("fake-google-token"))
     *       .thenReturn(new GoogleIdentity("uid-123", "user@gmail.com", "Jane Doe", null));
     * </pre>
     */
    @MockitoBean
    protected GoogleTokenVerifier googleTokenVerifier;

    // ── Injected test helpers ──────────────────────────────────────────────────

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** Ready-to-use token factory; re-created before each test. */
    protected AuthTokenFactory authFactory;

    /** Ready-to-use data seeder; re-created before each test. */
    protected TestDataSeeder dataSeeder;

    // ── Test isolation + factory init ──────────────────────────────────────────

    /**
     * Wipes all application data and re-initialises the token factory before
     * each test.
     *
     * <p>{@code hotspot} and {@code user_account} are the two FK roots of the schema.
     * Truncating them with CASCADE removes every dependent row across all application
     * tables. {@code flyway_schema_history} is unaffected.
     */
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.hotspot, public.user_account RESTART IDENTITY CASCADE"
        );
        authFactory = new AuthTokenFactory(mockMvc, objectMapper);
        dataSeeder  = new TestDataSeeder(jdbcTemplate);
    }
}
