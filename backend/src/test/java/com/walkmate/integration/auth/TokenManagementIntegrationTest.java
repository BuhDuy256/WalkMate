package com.walkmate.integration.auth;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for token lifecycle management.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-10 Logout (This Device)</b> — T10-1</li>
 *   <li><b>UC-11 Logout All Devices</b> — T11-1</li>
 *   <li><b>UC-12 Silent Token Refresh</b> — T12-1 (happy path), T12-2 (revoked token)</li>
 * </ul>
 *
 * <h3>Key corrections vs. use-case doc</h3>
 * <ul>
 *   <li>{@code POST /auth/logout} and {@code POST /auth/logout-all} return
 *       {@code 204 No Content} (not 200) — {@code ResponseEntity.noContent().build()}.</li>
 *   <li>Calling {@code /auth/refresh} with a revoked token returns HTTP {@code 400}
 *       {@code INVALID_USER_DATA} (not 401) — it is a {@link com.walkmate.domain.shared.exception.DomainException}
 *       caught by {@code GlobalExceptionHandler}.</li>
 *   <li>{@code logout} and {@code logout-all} both {@code DELETE} the relevant
 *       {@code refresh_token} rows (not just mark them revoked). Token rotation on
 *       {@code /auth/refresh} marks the old row {@code revoked = true} and inserts a
 *       new row — both rows can coexist briefly.</li>
 * </ul>
 */
class TokenManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String REGISTER_URL    = "/api/v1/auth/register";
    private static final String LOGIN_URL        = "/api/v1/auth/login";
    private static final String LOGOUT_URL       = "/api/v1/auth/logout";
    private static final String LOGOUT_ALL_URL   = "/api/v1/auth/logout-all";
    private static final String REFRESH_URL      = "/api/v1/auth/refresh";

    // ── T10-1: Logout (This Device) ───────────────────────────────────────────

    @Test
    void t10_1_logout_thisDevice_returns204_andRefreshTokenDeletedFromDb() throws Exception {
        String deviceId = "device-logout-single";
        String email    = "logout.single@example.com";

        // Arrange — register and login; refresh token row is created in DB
        AuthSession session = createSession(email, "Password1!", deviceId);
        UUID userId = getUserIdByEmail(email);

        // Pre-condition: token row exists for this device
        assertThat(activeRefreshTokenCount(userId, deviceId)).isEqualTo(1);

        // Act — logout from this device
        mockMvc.perform(post(LOGOUT_URL)
                        .header("Authorization", session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deviceId", deviceId))))
                .andExpect(status().isNoContent());  // 204, not 200

        // Assert — refresh_token row for this device deleted
        assertThat(activeRefreshTokenCount(userId, deviceId)).isEqualTo(0);
    }

    // ── T11-1: Logout All Devices ─────────────────────────────────────────────

    @Test
    void t11_1_logoutAll_returns204_andAllRefreshTokensDeletedFromDb() throws Exception {
        String email    = "logout.all@example.com";
        String password = "Password1!";

        // Arrange — register (creates token for "reg-device"), then login from two more devices
        register(email, password, "reg-device");
        AuthSession sessionA = login(email, password, "device-A");
        AuthSession sessionB = login(email, password, "device-B");

        UUID userId = getUserIdByEmail(email);

        // Pre-condition: 3 active refresh token rows (reg-device, device-A, device-B)
        assertThat(totalRefreshTokenCount(userId)).isEqualTo(3);

        // Act — logout all devices using session A's access token
        mockMvc.perform(post(LOGOUT_ALL_URL)
                        .header("Authorization", sessionA.accessToken()))
                .andExpect(status().isNoContent());  // 204

        // Assert — deleteAllByUserId removes ALL rows for this user
        assertThat(totalRefreshTokenCount(userId)).isEqualTo(0);
    }

    // ── T12-1: Token Refresh — Happy Path ─────────────────────────────────────

    @Test
    void t12_1_tokenRefresh_validToken_returns200_withNewTokenPair_andOldTokenRevoked()
            throws Exception {
        String deviceId = "device-refresh";
        String email    = "refresh.happy@example.com";

        // Arrange
        AuthSession session = createSession(email, "Password1!", deviceId);
        String oldRefreshToken = session.refreshToken();

        // Act — call /auth/refresh with the current refresh token
        MvcResult result = mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", oldRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();

        String newRefreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/refreshToken").asText();

        // Assert — old token is marked revoked=true (rotation, not deleted)
        Boolean oldRevoked = jdbcTemplate.queryForObject(
                "SELECT revoked FROM public.refresh_token WHERE token_value = ?",
                Boolean.class,
                oldRefreshToken
        );
        assertThat(oldRevoked).isTrue();

        // Assert — new token is a different value and is NOT revoked
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        Boolean newRevoked = jdbcTemplate.queryForObject(
                "SELECT revoked FROM public.refresh_token WHERE token_value = ?",
                Boolean.class,
                newRefreshToken
        );
        assertThat(newRevoked).isFalse();
    }

    // ── T12-2: Token Refresh — Revoked Token ──────────────────────────────────

    @Test
    void t12_2_tokenRefresh_revokedToken_returns400_INVALID_USER_DATA() throws Exception {
        String deviceId = "device-refresh-reuse";
        String email    = "refresh.revoked@example.com";

        // Arrange — create session and rotate the token once
        AuthSession session = createSession(email, "Password1!", deviceId);
        String originalToken = session.refreshToken();

        // First refresh — rotates the original token (marks it revoked, issues new token)
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", originalToken))))
                .andExpect(status().isOk());

        // Act — attempt to reuse the now-revoked original token (reuse detection)
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", originalToken))))
                // HTTP 400 (DomainException → GlobalExceptionHandler), NOT 401
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_USER_DATA"));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Registers a new user and returns a {@link AuthSession} containing the
     * access token, refresh token, and device ID from the login step.
     */
    private AuthSession createSession(String email, String password, String deviceId)
            throws Exception {
        register(email, password, deviceId);
        return login(email, password, deviceId);
    }

    /** Registers a user via POST /auth/register. */
    private void register(String email, String password, String deviceId) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullname", "Test User",
                                "email",    email,
                                "password", password,
                                "deviceId", deviceId
                        ))))
                .andExpect(status().isCreated());
    }

    /**
     * Logs in an already-registered user and returns an {@link AuthSession}
     * with both the access token and refresh token.
     */
    private AuthSession login(String email, String password, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email",    email,
                                "password", password,
                                "deviceId", deviceId
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        return new AuthSession(
                "Bearer " + data.at("/accessToken").asText(),
                data.at("/refreshToken").asText(),
                deviceId
        );
    }

    /** Looks up the user's UUID by email in the DB. */
    private UUID getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM public.user_account WHERE email = ?",
                UUID.class,
                email
        );
    }

    /**
     * Counts non-revoked refresh token rows for a specific user+device pair.
     * Used to verify that a single-device logout deleted exactly the right row.
     */
    private int activeRefreshTokenCount(UUID userId, String deviceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.refresh_token WHERE user_id = ? AND device_id = ? AND revoked = false",
                Integer.class,
                userId, deviceId
        );
        return count != null ? count : 0;
    }

    /**
     * Counts ALL refresh token rows for a user (revoked or not).
     * Used to verify that logout-all deleted every row for the user.
     */
    private int totalRefreshTokenCount(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.refresh_token WHERE user_id = ?",
                Integer.class,
                userId
        );
        return count != null ? count : 0;
    }

    // ── Minimal session value holder ───────────────────────────────────────────

    private record AuthSession(String accessToken, String refreshToken, String deviceId) {}
}
