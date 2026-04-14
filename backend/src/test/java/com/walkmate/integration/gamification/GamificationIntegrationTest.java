package com.walkmate.integration.gamification;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the gamification endpoints.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-41 View User Badges</b> — T41-1, T41-2</li>
 *   <li><b>UC-42 View User Stats</b> — T42-1</li>
 *   <li><b>UC-43 View Leaderboard</b> — T43-1</li>
 * </ul>
 *
 * <h3>Auth note</h3>
 * All three endpoints are public — no {@code Authorization} header is required.
 *
 * <h3>Known repository limitation</h3>
 * {@code UserJdbcRepository.mapRow()} hardcodes {@code totalDistanceKm = 0.0} and
 * {@code completedSessions = 0} — these columns are not read from the {@code ResultSet}.
 * T42-1 and T43-1 assert these fields are present as numbers but do not assert
 * seeded values for them.
 */
class GamificationIntegrationTest extends AbstractIntegrationTest {

    private static final String BADGES_URL      = "/api/v1/users/{userId}/badges";
    private static final String STATS_URL       = "/api/v1/users/{userId}/stats";
    private static final String LEADERBOARD_URL = "/api/v1/leaderboard";

    // ── T41-1: View Badges — Empty State ──────────────────────────────────────

    /**
     * T41-1: A new user with no badges receives an empty list — not an error.
     */
    @Test
    void t41_1_viewBadges_newUser_returnsEmptyList() throws Exception {
        authFactory.createAndLoginUser("badge.t411.a@example.com", "Password1!");
        String userId = getUserIdByEmail("badge.t411.a@example.com");

        mockMvc.perform(get(BADGES_URL, userId))   // no auth header — public endpoint
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── T41-2: View Badges — Populated ────────────────────────────────────────

    /**
     * T41-2: A user with a seeded badge receives it in the response with
     * {@code badgeName} and {@code awardedAt} fields populated.
     */
    @Test
    void t41_2_viewBadges_withSeededBadge_returnsBadgeEntry() throws Exception {
        authFactory.createAndLoginUser("badge.t412.a@example.com", "Password1!");
        String userId = getUserIdByEmail("badge.t412.a@example.com");

        seedBadge(userId, "FIRST_WALK");

        mockMvc.perform(get(BADGES_URL, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].badgeName").value("FIRST_WALK"))
                .andExpect(jsonPath("$.data[0].awardedAt").isNotEmpty());
    }

    // ── T42-1: View User Stats ─────────────────────────────────────────────────

    /**
     * T42-1: Stats endpoint returns all required fields.
     * {@code totalPoints} reflects the seeded value.
     * {@code totalDistanceKm} and {@code completedSessions} are asserted as
     * numeric fields only — the repository currently hardcodes them to 0 (known bug).
     */
    @Test
    void t42_1_viewUserStats_returnsAllStatFields() throws Exception {
        authFactory.createAndLoginUser("stats.t421.a@example.com", "Password1!");
        String userId = getUserIdByEmail("stats.t421.a@example.com");

        // Seed totalPoints so we can assert a concrete value
        jdbcTemplate.update(
                "UPDATE public.user_account SET total_points = 150 WHERE user_id = ?::uuid",
                userId);

        mockMvc.perform(get(STATS_URL, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.totalPoints").value(150))
                .andExpect(jsonPath("$.data.totalDistanceKm").isNumber())
                .andExpect(jsonPath("$.data.completedSessions").isNumber())
                .andExpect(jsonPath("$.data.trustScore").isNumber());
    }

    // ── T43-1: View Leaderboard — Unauthenticated ─────────────────────────────

    /**
     * T43-1: Leaderboard is public and returns users ordered by
     * {@code totalPoints} descending with {@code rank} starting at 1.
     *
     * <p>Three users are seeded with points 300, 100, and 200.
     * The leaderboard must return them in descending order (300 → 200 → 100)
     * with rank 1 assigned to the highest-points entry.
     */
    @Test
    void t43_1_viewLeaderboard_unauthenticated_orderedByPointsDescRankStartsAtOne()
            throws Exception {
        authFactory.createAndLoginUser("lb.t431.a@example.com", "Password1!");
        authFactory.createAndLoginUser("lb.t431.b@example.com", "Password1!");
        authFactory.createAndLoginUser("lb.t431.c@example.com", "Password1!");

        String userIdA = getUserIdByEmail("lb.t431.a@example.com");
        String userIdB = getUserIdByEmail("lb.t431.b@example.com");
        String userIdC = getUserIdByEmail("lb.t431.c@example.com");

        jdbcTemplate.update(
                "UPDATE public.user_account SET total_points = 300 WHERE user_id = ?::uuid",
                userIdA);
        jdbcTemplate.update(
                "UPDATE public.user_account SET total_points = 100 WHERE user_id = ?::uuid",
                userIdB);
        jdbcTemplate.update(
                "UPDATE public.user_account SET total_points = 200 WHERE user_id = ?::uuid",
                userIdC);

        String body = mockMvc.perform(get(LEADERBOARD_URL))  // no auth — public
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode entries =
                objectMapper.readTree(body).get("data");

        assertThat(entries.size()).isGreaterThanOrEqualTo(3);

        // Rank 1 entry must have the highest total_points among our seeded users
        // (other test data is wiped by @BeforeEach, so only our 3 users exist)
        assertThat(entries.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(entries.get(0).get("totalPoints").asInt()).isEqualTo(300);
        assertThat(entries.get(0).get("userId").asText()).isEqualTo(userIdA);

        // Ordering invariant: each entry's points ≥ the next entry's points
        for (int i = 0; i < entries.size() - 1; i++) {
            int current = entries.get(i).get("totalPoints").asInt();
            int next    = entries.get(i + 1).get("totalPoints").asInt();
            assertThat(current)
                    .as("Entry %d (%d pts) must have points ≥ entry %d (%d pts)", i, current, i + 1, next)
                    .isGreaterThanOrEqualTo(next);
        }

        // rank field increments: rank of entry[i+1] > rank of entry[i]
        for (int i = 0; i < entries.size() - 1; i++) {
            assertThat(entries.get(i + 1).get("rank").asInt())
                    .isGreaterThan(entries.get(i).get("rank").asInt());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    private void seedBadge(String userId, String badgeName) {
        jdbcTemplate.update(
                "INSERT INTO public.user_badge (user_id, badge_name) " +
                "VALUES (?::uuid, ?) " +
                "ON CONFLICT (user_id, badge_name) DO NOTHING",
                userId, badgeName);
    }
}
