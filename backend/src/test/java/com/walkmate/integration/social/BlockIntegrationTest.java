package com.walkmate.integration.social;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the block/unblock endpoints.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-37 Block a User</b> — T37-1, T37-2, T37-3</li>
 *   <li><b>UC-38 Unblock a User</b> — T38-1</li>
 * </ul>
 *
 * <h3>Block side-effect note</h3>
 * Blocking physically DELETEs any existing friendship rows in both directions
 * between the blocker and the blocked user, then inserts a {@code block_relation} row.
 */
class BlockIntegrationTest extends AbstractIntegrationTest {

    private static final String BLOCK_URL   = "/api/v1/users/{userId}/block";
    private static final String UNBLOCK_URL = "/api/v1/users/{userId}/block";

    // ── T37-1: Block a User — Happy Path ──────────────────────────────────────

    /**
     * T37-1: User A has an accepted friendship with User B.
     * User A blocks User B:
     * <ul>
     *   <li>Returns {@code 200 OK}</li>
     *   <li>{@code block_relation} row exists in DB</li>
     *   <li>Friendship row(s) between A and B are deleted from DB</li>
     * </ul>
     */
    @Test
    void t37_1_blockUser_happyPath_blockCreatedAndFriendshipDeleted() throws Exception {
        String tokenA = authFactory.createAndLoginUser("block.t371.a@example.com", "Password1!");
        authFactory.createAndLoginUser("block.t371.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("block.t371.a@example.com");
        String userIdB = getUserIdByEmail("block.t371.b@example.com");

        dataSeeder.seedAcceptedFriendship(userIdA, userIdB);

        // Confirm friendship exists before block
        int friendshipBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.friendship " +
                "WHERE (requester_id = ?::uuid AND addressee_id = ?::uuid) " +
                "   OR (requester_id = ?::uuid AND addressee_id = ?::uuid)",
                Integer.class, userIdA, userIdB, userIdB, userIdA);
        assertThat(friendshipBefore).isEqualTo(1);

        mockMvc.perform(post(BLOCK_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DB: block_relation row exists (A → B)
        Integer blockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.block_relation " +
                "WHERE blocker_id = ?::uuid AND blocked_id = ?::uuid",
                Integer.class, userIdA, userIdB);
        assertThat(blockCount).isEqualTo(1);

        // DB: friendship row deleted (block side-effect)
        Integer friendshipAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.friendship " +
                "WHERE (requester_id = ?::uuid AND addressee_id = ?::uuid) " +
                "   OR (requester_id = ?::uuid AND addressee_id = ?::uuid)",
                Integer.class, userIdA, userIdB, userIdB, userIdA);
        assertThat(friendshipAfter).isEqualTo(0);
    }

    // ── T37-2: Block Self ──────────────────────────────────────────────────────

    /**
     * T37-2: Attempting to block own userId returns
     * {@code BLOCK_SELF_BLOCK_FORBIDDEN} (HTTP 400).
     */
    @Test
    void t37_2_blockUser_selfBlock_returnsSelfBlockForbidden() throws Exception {
        String tokenA = authFactory.createAndLoginUser("block.t372.a@example.com", "Password1!");
        String userIdA = getUserIdByEmail("block.t372.a@example.com");

        mockMvc.perform(post(BLOCK_URL, userIdA)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BLOCK_SELF_BLOCK_FORBIDDEN"));
    }

    // ── T37-3: Block — Already Blocked ────────────────────────────────────────

    /**
     * T37-3: Blocking the same user twice returns
     * {@code BLOCK_ALREADY_BLOCKED} (HTTP 400).
     */
    @Test
    void t37_3_blockUser_alreadyBlocked_returnsAlreadyBlocked() throws Exception {
        String tokenA = authFactory.createAndLoginUser("block.t373.a@example.com", "Password1!");
        authFactory.createAndLoginUser("block.t373.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("block.t373.a@example.com");
        String userIdB = getUserIdByEmail("block.t373.b@example.com");

        // First block succeeds
        mockMvc.perform(post(BLOCK_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk());

        // Second block is rejected
        mockMvc.perform(post(BLOCK_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BLOCK_ALREADY_BLOCKED"));
    }

    // ── T38-1: Unblock a User — Happy Path ────────────────────────────────────

    /**
     * T38-1: User A blocks then unblocks User B.
     * After unblock, the {@code block_relation} row is deleted from DB.
     */
    @Test
    void t38_1_unblockUser_happyPath_blockRowDeleted() throws Exception {
        String tokenA = authFactory.createAndLoginUser("block.t381.a@example.com", "Password1!");
        authFactory.createAndLoginUser("block.t381.b@example.com", "Password1!");

        String userIdA = getUserIdByEmail("block.t381.a@example.com");
        String userIdB = getUserIdByEmail("block.t381.b@example.com");

        // Block first
        mockMvc.perform(post(BLOCK_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk());

        // Unblock
        mockMvc.perform(delete(UNBLOCK_URL, userIdB)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DB: block_relation row deleted
        Integer blockCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.block_relation " +
                "WHERE blocker_id = ?::uuid AND blocked_id = ?::uuid",
                Integer.class, userIdA, userIdB);
        assertThat(blockCount).isEqualTo(0);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }
}
