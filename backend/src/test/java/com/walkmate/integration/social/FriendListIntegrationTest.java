package com.walkmate.integration.social;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the friends list, request lists, and friend-remove endpoints.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-36 View Friends and Friend Requests</b> — T36-1</li>
 * </ul>
 *
 * <h3>Remove-friend note</h3>
 * {@code DELETE /api/v1/friends/{userId}} updates the friendship row's status to
 * {@code DECLINED} — the row is NOT physically deleted from the table.
 */
class FriendListIntegrationTest extends AbstractIntegrationTest {

    private static final String FRIENDS_URL          = "/api/v1/friends";
    private static final String INCOMING_URL         = "/api/v1/friends/requests/incoming";
    private static final String OUTGOING_URL         = "/api/v1/friends/requests/outgoing";
    private static final String REMOVE_FRIEND_URL    = "/api/v1/friends/{userId}";
    private static final String SEND_REQUEST_URL     = "/api/v1/friends/{userId}/request";

    // ── T36-1: View Friends and Friend Requests ───────────────────────────────

    /**
     * T36-1: Verifies all four list/remove operations for UC-36.
     *
     * <p>Setup:
     * <ul>
     *   <li>User B is an accepted friend of User A (seeded via {@code seedAcceptedFriendship})</li>
     *   <li>User C sent an incoming PENDING request to User A (seeded via JDBC)</li>
     *   <li>User A sends an outgoing PENDING request to User D (via API)</li>
     * </ul>
     *
     * <p>Assertions:
     * <ol>
     *   <li>{@code GET /api/v1/friends} — User B appears in list</li>
     *   <li>{@code GET /api/v1/friends/requests/incoming} — User C's request appears</li>
     *   <li>{@code GET /api/v1/friends/requests/outgoing} — User D's request appears</li>
     *   <li>{@code DELETE /api/v1/friends/{userBId}} — 200 OK; DB row status = DECLINED</li>
     * </ol>
     */
    @Test
    void t36_1_viewFriendsAndRequests_allListsAndRemove() throws Exception {
        String tokenA = authFactory.createAndLoginUser("fl.t361.a@example.com", "Password1!");
        authFactory.createAndLoginUser("fl.t361.b@example.com", "Password1!");
        authFactory.createAndLoginUser("fl.t361.c@example.com", "Password1!");
        authFactory.createAndLoginUser("fl.t361.d@example.com", "Password1!");

        String userIdA = getUserIdByEmail("fl.t361.a@example.com");
        String userIdB = getUserIdByEmail("fl.t361.b@example.com");
        String userIdC = getUserIdByEmail("fl.t361.c@example.com");
        String userIdD = getUserIdByEmail("fl.t361.d@example.com");

        // Accepted friendship: B is a friend of A
        dataSeeder.seedAcceptedFriendship(userIdB, userIdA);

        // Incoming: C sent a request to A
        seedPendingFriendRequest(userIdC, userIdA);

        // Outgoing: A sends a request to D via API
        mockMvc.perform(post(SEND_REQUEST_URL, userIdD)
                        .header("Authorization", tokenA))
                .andExpect(status().isCreated());

        // ── Assert: GET /api/v1/friends — User B present ──
        mockMvc.perform(get(FRIENDS_URL).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath(
                        "$.data[?(@.userId == '" + userIdB + "')]").exists());

        // ── Assert: GET /api/v1/friends/requests/incoming — C's request present ──
        mockMvc.perform(get(INCOMING_URL).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath(
                        "$.data[?(@.requester_id == '" + userIdC + "')]").exists());

        // ── Assert: GET /api/v1/friends/requests/outgoing — A's request to D present ──
        mockMvc.perform(get(OUTGOING_URL).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath(
                        "$.data[?(@.addressee_id == '" + userIdD + "')]").exists());

        // ── Assert: DELETE /api/v1/friends/{userBId} — removes friend ──
        mockMvc.perform(delete(REMOVE_FRIEND_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DB: friendship row still exists but status is DECLINED
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.friendship " +
                "WHERE (requester_id = ?::uuid AND addressee_id = ?::uuid) " +
                "   OR (requester_id = ?::uuid AND addressee_id = ?::uuid)",
                String.class, userIdB, userIdA, userIdA, userIdB);
        assertThat(status).isEqualTo("DECLINED");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    private void seedPendingFriendRequest(String requesterId, String addresseeId) {
        jdbcTemplate.update(
                "INSERT INTO public.friendship (requester_id, addressee_id, status) " +
                "VALUES (?::uuid, ?::uuid, 'PENDING'::friend_status)",
                requesterId, addresseeId);
    }
}
