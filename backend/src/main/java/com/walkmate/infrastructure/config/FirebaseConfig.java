package com.walkmate.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Initialises the Firebase Admin SDK and exposes {@link FirebaseMessaging} as a
 * Spring bean so it can be injected anywhere in the infrastructure layer.
 *
 * <p><b>Credential resolution order (fail-fast):</b></p>
 * <ol>
 *   <li><b>Env var {@code FIREBASE_CREDENTIALS}</b> — expected in staging / production.
 *       Set this to the full JSON content of the service-account file (single line or
 *       multi-line are both accepted). This is the only safe option for containerised
 *       deployments because the JSON file must never exist on the production filesystem.</li>
 *   <li><b>Classpath resource {@code firebase-service-account.json}</b> — present only in
 *       the developer's local checkout (the file is gitignored). Used automatically when
 *       the env var is absent.</li>
 * </ol>
 *
 * <p>The application will <em>fail to start</em> if neither source provides valid credentials.
 * This is intentional: a missing Firebase config is a deployment error, not a runtime
 * edge case to silently ignore.</p>
 *
 * <p>{@link FirebaseApp} is checked for prior initialisation before calling
 * {@link FirebaseApp#initializeApp} so that Spring Boot DevTools hot-reloads (which
 * re-run {@code @Bean} methods in the same JVM) do not throw
 * {@code IllegalStateException: FirebaseApp name [DEFAULT] already exists}.</p>
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    private static final String CREDENTIALS_ENV_VAR      = "FIREBASE_CREDENTIALS";
    private static final String LOCAL_CREDENTIALS_RESOURCE = "firebase-service-account.json";

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.debug("FirebaseApp already initialised — reusing existing instance");
            return FirebaseApp.getInstance();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(resolveCredentials())
                .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("FirebaseApp initialised successfully");
        return app;
    }

    /**
     * Exposes {@link FirebaseMessaging} as a bean so infrastructure components
     * (e.g. {@code FcmNotificationProvider}) can declare it as a constructor
     * dependency without calling the static {@code FirebaseMessaging.getInstance()}.
     * Injecting via constructor keeps classes unit-testable.
     */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private GoogleCredentials resolveCredentials() throws IOException {
        String envJson = System.getenv(CREDENTIALS_ENV_VAR);

        if (envJson != null && !envJson.isBlank()) {
            log.info("Firebase credentials loaded from environment variable '{}'", CREDENTIALS_ENV_VAR);
            InputStream stream = new ByteArrayInputStream(envJson.getBytes(StandardCharsets.UTF_8));
            return GoogleCredentials.fromStream(stream);
        }

        // Fallback: read the service-account file from the classpath (local dev only).
        InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(LOCAL_CREDENTIALS_RESOURCE);

        if (stream == null) {
            throw new IllegalStateException(
                    "Firebase credentials not found. "
                    + "Set the '" + CREDENTIALS_ENV_VAR + "' environment variable with the "
                    + "service-account JSON content, or place '" + LOCAL_CREDENTIALS_RESOURCE
                    + "' in src/main/resources/ for local development.");
        }

        log.info("Firebase credentials loaded from classpath resource '{}' (local dev mode)",
                LOCAL_CREDENTIALS_RESOURCE);
        return GoogleCredentials.fromStream(stream);
    }
}
