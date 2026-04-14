package com.walkmate.integration.social;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for friend-request and friend-list endpoints.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-34 Send a Friend Request</b> — T34-1, T34-2, T34-3</li>
 *   <li><b>UC-35 Respond to a Friend Request</b> — T35-1</li>
 * </ul>
 *
 * <h3>Friendship table notes</h3>
 * <ul>
 *   <li>A pending request is stored as a row with {@code status = 'PENDING'}</li>
 *   <li>Decline updates the row to {@code status = 'DECLINED'} (row is NOT deleted)</li>
 *   <li>Accept updates the row to {@code status = 'ACCEPTED'}</li>
 * </ul>
 */
class FriendshipIntegrationTest extends AbstractIntegrationTest {

    private static final String SEND_REQUEST_URL = "/api/v1/friends/{userId}/request";
    private static final String ACCEPT_URL       = "/api/v1/friends/requests/{requestId}/accept";
    private static final String DECLINE_URL      = "/api/v1/friends/requests/{requestId}/decline";

    // ── T34-1: Send Friend Request — Happy Path ───────────────────────────────

    /**
     * T34-1: User A sends a friend request to User B.
     * Returns {@code 201 Created}; DB row is {@code PENDING}.
     */
    @Test
    void t34_1_sendFriendRequest_happyPath_createsRequestPending() throws Exception {
        String tokenA = authFactory.createAndLoginUser("friend.t341.a@example.com", "Password1!");
        authFactory.createAndLoginUser("friend.t341.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("friend.t341.a@example.com");
        String userIdB = getUserIdByEmail("friend.t341.b@example.com");

        mockMvc.perform(post(SEND_REQUEST_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.friendship_id").isNotEmpty())
                .andExpect(jsonPath("$.data.requester_id").value(userIdA))
                .andExpect(jsonPath("$.data.addressee_id").value(userIdB))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // DB: friendship row exists with PENDING status
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.friendship " +
                "WHERE requester_id = ?::uuid AND addressee_id = ?::uuid",
                String.class, userIdA, userIdB);
        assertThat(status).isEqualTo("PENDING");
    }

    // ── T34-2: Send Friend Request — Self Request ─────────────────────────────

    /**
     * T34-2: User A sends a friend request to themselves.
     * Returns HTTP 400, {@code FRIEND_REQUEST_SELF_FORBIDDEN}.
     */
    @Test
    void t34_2_sendFriendRequest_selfRequest_returnsSelfForbidden() throws Exception {
        String tokenA = authFactory.createAndLoginUser("friend.t342.a@example.com", "Password1!");
        String userIdA = getUserIdByEmail("friend.t342.a@example.com");

        mockMvc.perform(post(SEND_REQUEST_URL, userIdA)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FRIEND_REQUEST_SELF_FORBIDDEN"));
    }

    // ── T34-3: Send Friend Request — Already Pending / Already Friends ────────

    /**
     * T34-3a: User A sends a request to User B; sends again → {@code FRIEND_REQUEST_ALREADY_PENDING}.
     * T34-3b: Existing ACCEPTED friendship between A and C → sending a request returns
     * {@code FRIEND_REQUEST_ALREADY_FRIENDS}.
     */
    @Test
    void t34_3_sendFriendRequest_alreadyPendingOrFriends_returnsCorrectErrors() throws Exception {
        String tokenA = authFactory.createAndLoginUser("friend.t343.a@example.com", "Password1!");
        authFactory.createAndLoginUser("friend.t343.b@example.com", "Password1!");
        authFactory.createAndLoginUser("friend.t343.c@example.com", "Password1!");

        String userIdA = getUserIdByEmail("friend.t343.a@example.com");
        String userIdB = getUserIdByEmail("friend.t343.b@example.com");
        String userIdC = getUserIdByEmail("friend.t343.c@example.com");

        // ── 3a: already-pending ──
        // First request succeeds
        mockMvc.perform(post(SEND_REQUEST_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isCreated());

        // Second request to same user
        mockMvc.perform(post(SEND_REQUEST_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FRIEND_REQUEST_ALREADY_PENDING"));

        // ── 3b: already-friends ──
        dataSeeder.seedAcceptedFriendship(userIdA, userIdC);

        mockMvc.perform(post(SEND_REQUEST_URL, userIdC)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FRIEND_REQUEST_ALREADY_FRIENDS"));
    }

    // ── T35-1: Respond to Friend Request — Accept and Decline ────────────────

    /**
     * T35-1a: User C sent a request to User A. User A accepts.
     * Returns {@code 200 OK}, {@code status = ACCEPTED}; DB row confirms.
     *
     * T35-1b: User D sent a request to User A. User A declines.
     * Returns {@code 200 OK}; DB row status is {@code DECLINED} (not deleted).
     */
    @Test
    void t35_1_respondToFriendRequest_acceptAndDecline_correctStatusTransitions() throws Exception {
        String tokenA = authFactory.createAndLoginUser("friend.t351.a@example.com", "Password1!");
        authFactory.createAndLoginUser("friend.t351.c@example.com", "Password1!");
        authFactory.createAndLoginUser("friend.t351.d@example.com", "Password1!");

        String userIdA = getUserIdByEmail("friend.t351.a@example.com");
        String userIdC = getUserIdByEmail("friend.t351.c@example.com");
        String userIdD = getUserIdByEmail("friend.t351.d@example.com");

        // ── 35-1a: Accept path ──
        String requestIdCA = seedPendingFriendRequest(userIdC, userIdA);

        mockMvc.perform(post(ACCEPT_URL, requestIdCA)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // DB: status is now ACCEPTED
        String statusCA = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.friendship WHERE friendship_id = ?::uuid",
                String.class, requestIdCA);
        assertThat(statusCA).isEqualTo("ACCEPTED");

        // ── 35-1b: Decline path ──
        String requestIdDA = seedPendingFriendRequest(userIdD, userIdA);

        mockMvc.perform(post(DECLINE_URL, requestIdDA)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DB: row remains with status DECLINED (not deleted)
        String statusDA = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.friendship WHERE friendship_id = ?::uuid",
                String.class, requestIdDA);
        assertThat(statusDA).isEqualTo("DECLINED");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    /**
     * Seeds a PENDING friend request (requester → addressee) via JDBC.
     * Returns the generated {@code friendship_id} string.
     */
    private String seedPendingFriendRequest(String requesterId, String addresseeId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO public.friendship (requester_id, addressee_id, status) " +
                "VALUES (?::uuid, ?::uuid, 'PENDING'::friend_status) " +
                "RETURNING friendship_id::text",
                String.class, requesterId, addresseeId);
    }
}
