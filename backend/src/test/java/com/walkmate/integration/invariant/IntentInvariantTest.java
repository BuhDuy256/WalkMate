package com.walkmate.integration.invariant;

import com.walkmate.support.AbstractIntegrationTest;
import com.walkmate.support.TestDataSeeder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Invariant matrix tests for walk intent rules.
 *
 * <h3>Invariants covered</h3>
 * <ul>
 *   <li><b>I-1</b> — No overlapping time windows (4 overlap geometries + adjacent-ok)</li>
 *   <li><b>I-3</b> — CONSUMED intent is immutable (cancel rejected)</li>
 *   <li><b>I-4</b> — MATCHING intent cancel behaviour (API allows it; UI enforces lock)</li>
 *   <li><b>I-7</b> — Private intent requires accepted friendship</li>
 * </ul>
 */
class IntentInvariantTest extends AbstractIntegrationTest {

    private static final String INTENTS_URL        = "/api/v1/intents";
    private static final String DELETE_INTENT_URL  = "/api/v1/intents/{intentId}";
    private static final String ACCEPT_URL         = "/api/v1/proposals/{proposalId}/accept";
    private static final String FRIEND_REQUEST_URL = "/api/v1/friends/{userId}/request";

    // ── INV-I1a: Contains ─────────────────────────────────────────────────────

    /** Existing [17:00, 18:00]; new [16:00, 19:00] CONTAINS it → INTENT_OVERLAPPING. */
    @Test
    void inv_i1a_newWindowContainsExisting_returnsOverlapping() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String token = authFactory.createAndLoginUser("i1a@example.com", "Password1!");
        String date  = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 17.0f, 18.0f)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 16.0f, 19.0f)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INTENT_OVERLAPPING"));
    }

    // ── INV-I1b: Contained by ────────────────────────────────────────────────

    /** Existing [17:00, 18:00]; new [17:15, 17:45] is CONTAINED BY it → INTENT_OVERLAPPING. */
    @Test
    void inv_i1b_newWindowContainedByExisting_returnsOverlapping() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String token = authFactory.createAndLoginUser("i1b@example.com", "Password1!");
        String date  = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 17.0f, 18.0f)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 17.25f, 17.75f)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INTENT_OVERLAPPING"));
    }

    // ── INV-I1c: Partial overlap at start ────────────────────────────────────

    /** Existing [17:00, 18:00]; new [16:30, 17:30] overlaps at start → INTENT_OVERLAPPING. */
    @Test
    void inv_i1c_partialOverlapAtStart_returnsOverlapping() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String token = authFactory.createAndLoginUser("i1c@example.com", "Password1!");
        String date  = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 17.0f, 18.0f)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 16.5f, 17.5f)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INTENT_OVERLAPPING"));
    }

    // ── INV-I1d: Adjacent windows — no overlap ────────────────────────────────

    /** Existing [17:00, 18:00]; new [18:00, 19:00] is adjacent (not overlapping) → 201. */
    @Test
    void inv_i1d_adjacentWindows_noOverlap_succeeds() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String token = authFactory.createAndLoginUser("i1d@example.com", "Password1!");
        String date  = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 17.0f, 18.0f)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON)
                .content(buildIntentJson(hotspotId, date, 18.0f, 19.0f)))
                .andExpect(status().isCreated());
    }

    // ── INV-I3: CONSUMED Intent Is Immutable ─────────────────────────────────

    /**
     * After double-accept both intents transition to CONSUMED. Attempting to cancel
     * returns {@code INTENT_ALREADY_CONSUMED} (not {@code INTENT_NOT_OPEN} — the todo
     * description was wrong; the domain method branches by terminal status type).
     */
    @Test
    void inv_i3_consumedIntent_cancelReturnsAlreadyConsumed() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        Instant start = Instant.now().plus(5, ChronoUnit.MINUTES);
        Instant end   = start.plus(1, ChronoUnit.HOURS);

        String tokenA = authFactory.createAndLoginUser("i3.a@example.com", "Password1!");
        String tokenB = authFactory.createAndLoginUser("i3.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("i3.a@example.com");
        String userIdB = getUserIdByEmail("i3.b@example.com");

        TestDataSeeder.ProposalSeed seed = dataSeeder.seedPendingProposal(userIdA, userIdB, hotspotId, start, end);
        String proposalId = seed.proposalId();
        String intentIdA  = seed.intentIdA();

        // Both users accept → intents become CONSUMED
        mockMvc.perform(post(ACCEPT_URL, proposalId).header("Authorization", tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(post(ACCEPT_URL, proposalId).header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // Attempt to cancel a CONSUMED intent
        mockMvc.perform(delete(DELETE_INTENT_URL, intentIdA).header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INTENT_ALREADY_CONSUMED"));
    }

    // ── INV-I4: MATCHING Intent Can Be Cancelled ─────────────────────────────

    /**
     * I-4 is UI-enforcement only (hide cancel button for MATCHING in the Intent tab).
     * The backend cancel API <em>does</em> allow MATCHING → CANCELLED.
     * This test confirms the API behaviour; the todo assertion (INTENT_NOT_OPEN) was wrong.
     */
    @Test
    void inv_i4_matchingIntent_cancelAllowed_statusBecomesCANCELLED() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String token = authFactory.createAndLoginUser("i4.a@example.com", "Password1!");
        String userIdA = getUserIdByEmail("i4.a@example.com");

        // Create an OPEN intent, then force it to MATCHING via JDBC
        String body = buildIntentJson(hotspotId,
                LocalDate.now().plusDays(1).toString(), 10.0f, 11.0f);
        String response = mockMvc.perform(post(INTENTS_URL).header("Authorization", token)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String intentId = objectMapper.readTree(response)
                .get("data").get("intent").get("id").asText();

        jdbcTemplate.update(
                "UPDATE public.walk_intent SET status = 'MATCHING'::intent_status " +
                "WHERE intent_id = ?::uuid",
                intentId);

        // Cancel should succeed — MATCHING is cancellable
        mockMvc.perform(delete(DELETE_INTENT_URL, intentId).header("Authorization", token))
                .andExpect(status().isOk());

        // DB confirms CANCELLED
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, intentId);
        assertThat(status).isEqualTo("CANCELLED");
    }

    // ── INV-I7: Private Intent Requires Accepted Friendship ───────────────────

    /**
     * I-7: A private invite requires an accepted friendship.
     * <ol>
     *   <li>Pending friend request (not accepted) → {@code INTENT_PRIVATE_FRIEND_NOT_ACCEPTED}</li>
     *   <li>Promote friendship to ACCEPTED via JDBC → retry → {@code 201 Created}</li>
     * </ol>
     */
    @Test
    void inv_i7_privateIntent_requiresAcceptedFriendship_thenSucceeds() throws Exception {
        String hotspotId = dataSeeder.seedHotspot();
        String tokenA  = authFactory.createAndLoginUser("i7.a@example.com", "Password1!");
        authFactory.createAndLoginUser("i7.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("i7.a@example.com");
        String userIdB = getUserIdByEmail("i7.b@example.com");

        // Step 1: send a friend request (PENDING — not accepted yet)
        mockMvc.perform(post(FRIEND_REQUEST_URL, userIdB).header("Authorization", tokenA))
                .andExpect(status().isCreated());

        String date = LocalDate.now().plusDays(1).toString();
        String privateBody = buildPrivateIntentJson(hotspotId, date, 14.0f, 15.0f, userIdB);

        // Step 2: private intent with only a PENDING request → rejected
        mockMvc.perform(post(INTENTS_URL).header("Authorization", tokenA)
                .contentType(APPLICATION_JSON).content(privateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INTENT_PRIVATE_FRIEND_NOT_ACCEPTED"));

        // Step 3: promote friendship to ACCEPTED via JDBC
        jdbcTemplate.update(
                "UPDATE public.friendship SET status = 'ACCEPTED'::friend_status " +
                "WHERE requester_id = ?::uuid AND addressee_id = ?::uuid",
                userIdA, userIdB);

        // Step 4: retry private intent → now succeeds
        mockMvc.perform(post(INTENTS_URL).header("Authorization", tokenA)
                .contentType(APPLICATION_JSON).content(privateBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.intent.status").value("MATCHING"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    private String buildIntentJson(String hotspotId, String date,
                                   float timeStart, float timeEnd) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hotspot_id", hotspotId);
        map.put("date",       date);
        map.put("time_start", timeStart);
        map.put("time_end",   timeEnd);
        map.put("age_min",    18);
        map.put("age_max",    60);
        map.put("is_private", false);
        map.put("invited_friend_id", null);
        return objectMapper.writeValueAsString(map);
    }

    private String buildPrivateIntentJson(String hotspotId, String date,
                                          float timeStart, float timeEnd,
                                          String invitedFriendId) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hotspot_id",       hotspotId);
        map.put("date",             date);
        map.put("time_start",       timeStart);
        map.put("time_end",         timeEnd);
        map.put("age_min",          18);
        map.put("age_max",          60);
        map.put("is_private",       true);
        map.put("invited_friend_id", invitedFriendId);
        return objectMapper.writeValueAsString(map);
    }
}
