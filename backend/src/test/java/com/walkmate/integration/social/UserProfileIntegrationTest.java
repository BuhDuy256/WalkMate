package com.walkmate.integration.social;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the public user profile endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-33 View a Public User Profile</b> — T33-1, T33-2, T33-3</li>
 * </ul>
 *
 * <h3>Auth note</h3>
 * {@code GET /api/v1/users/{userId}} is a public endpoint — no JWT is required.
 * Both authenticated and unauthenticated callers receive the same profile data.
 */
class UserProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String PROFILE_URL = "/api/v1/users/{userId}";

    // ── T33-1: View Profile — Authenticated ───────────────────────────────────

    /**
     * T33-1: Authenticated user views another user's public profile.
     * Core profile fields are present in the response.
     */
    @Test
    void t33_1_viewPublicProfile_authenticated_returnsProfileFields() throws Exception {
        String tokenA = authFactory.createAndLoginUser("profile.t331.a@example.com", "Password1!");
        authFactory.createAndLoginUser("profile.t331.b@example.com", "Password1!");
        String userIdB = getUserIdByEmail("profile.t331.b@example.com");

        mockMvc.perform(get(PROFILE_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userIdB))
                .andExpect(jsonPath("$.data.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.trustScore").isNumber());
    }

    // ── T33-2: View Profile — Unauthenticated (Public Endpoint) ──────────────

    /**
     * T33-2: Unauthenticated caller can view a public profile — no token required.
     */
    @Test
    void t33_2_viewPublicProfile_unauthenticated_returnsOk() throws Exception {
        authFactory.createAndLoginUser("profile.t332.b@example.com", "Password1!");
        String userIdB = getUserIdByEmail("profile.t332.b@example.com");

        mockMvc.perform(get(PROFILE_URL, userIdB))   // no Authorization header
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userIdB));
    }

    // ── T33-3: View Profile — Not Found ───────────────────────────────────────

    /**
     * T33-3: Requesting a non-existent userId returns {@code USER_NOT_FOUND} (HTTP 400).
     */
    @Test
    void t33_3_viewPublicProfile_notFound_returnsUserNotFound() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get(PROFILE_URL, nonExistentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
