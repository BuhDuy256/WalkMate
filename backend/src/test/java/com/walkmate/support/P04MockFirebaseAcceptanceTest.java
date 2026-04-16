package com.walkmate.support;

import com.walkmate.application.user.GoogleIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * P0-4 acceptance tests — verifies that the Firebase mocks registered in
 * {@link AbstractIntegrationTest} work correctly for OAuth and FCM scenarios.
 *
 * <h3>What is proven here</h3>
 * <ol>
 *   <li>The application context loads with {@code GoogleTokenVerifier} replaced by a mock
 *       (i.e. no real Firebase Admin SDK credential loading occurs).</li>
 *   <li>Tests can stub {@code googleTokenVerifier.verify()} to return a controlled
 *       {@link GoogleIdentity}, which is the prerequisite for UC-07 (Google OAuth) tests.</li>
 *   <li>{@code firebaseMessaging} is accessible as a protected mock, ready for
 *       invocation-count assertions in notification tests.</li>
 * </ol>
 *
 * <h3>FCM verification strategy (documented per P0-4 spec)</h3>
 * <p>FCM sends are verified by asserting mock invocations — not by real delivery.
 * In tests that trigger notifications, assert like:
 * <pre>
 *   verify(firebaseMessaging, times(1)).send(any(Message.class));
 * </pre>
 * Failures in FCM delivery are swallowed by {@code FcmNotificationProvider} intentionally
 * (logged, not re-thrown) — so only the stub's call-count matters in tests, not exceptions.
 */
class P04MockFirebaseAcceptanceTest extends AbstractIntegrationTest {

    @Test
    void googleTokenVerifier_mockIsInjected_andDefaultReturnsNull() {
        // The mock is registered — no real Firebase credential loading happened.
        assertThat(googleTokenVerifier).isNotNull();

        // Default Mockito behaviour: returns null for object return types.
        // This proves the mock is active (a real verifier would throw or call Firebase).
        GoogleIdentity result = googleTokenVerifier.verify("any-token");
        assertThat(result).isNull();
    }

    @Test
    void googleTokenVerifier_canBeStubbed_returnsControlledIdentity() {
        // Arrange — stub the verifier to return a fixed identity
        GoogleIdentity expected = new GoogleIdentity(
                "google-uid-abc",
                "jane@gmail.com",
                "Jane Doe",
                "https://example.com/photo.jpg"
        );
        when(googleTokenVerifier.verify("fake-id-token")).thenReturn(expected);

        // Act
        GoogleIdentity actual = googleTokenVerifier.verify("fake-id-token");

        // Assert
        assertThat(actual.sub()).isEqualTo("google-uid-abc");
        assertThat(actual.email()).isEqualTo("jane@gmail.com");
        assertThat(actual.name()).isEqualTo("Jane Doe");
        assertThat(actual.pictureUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void firebaseMessaging_mockIsAccessible_forInvocationAssertions() {
        // The FCM mock is protected — subclass tests can access it directly.
        assertThat(firebaseMessaging).isNotNull();

        // No invocations yet — Mockito default state is zero calls.
        // Real notification tests will call verify(firebaseMessaging, times(N)).send(...)
        // after exercising the business logic that triggers FCM.
    }
}
