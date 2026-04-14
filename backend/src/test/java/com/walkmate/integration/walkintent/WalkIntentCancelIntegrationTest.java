package com.walkmate.integration.walkintent;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for walk intent cancellation.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-17 Cancel Walk Intent</b> — T17-1 (happy path), T17-2 (terminal state),
 *       T17-3 (not owner)</li>
 * </ul>
 *
 * <h3>Key observations</h3>
 * <ul>
 *   <li>{@link com.walkmate.domain.walkintent.WalkIntent#cancel()} allows cancellation
 *       from BOTH {@code OPEN} and {@code MATCHING} — the user may withdraw at any
 *       point before the proposal is confirmed. Only {@code CANCELLED} and {@code CONSUMED}
 *       are terminal — they throw {@code INTENT_ALREADY_CANCELLED} and
 *       {@code INTENT_ALREADY_CONSUMED} respectively.</li>
 *   <li>T17-2 therefore tests a {@code CONSUMED} intent, not {@code MATCHING}
 *       (the todo originally said MATCHING → INTENT_NOT_OPEN; that is incorrect per the
 *       domain code).</li>
 *   <li>{@code DELETE /api/v1/intents/{intentId}} returns {@code 200 OK} with
 *       {@code { success: true, data: null }} (soft-delete; row kept for audit).</li>
 *   <li>After a successful cancel the overlap guard is lifted, so the same user can
 *       immediately re-create an intent in the same time window.</li>
 * </ul>
 */
class WalkIntentCancelIntegrationTest extends AbstractIntegrationTest {

    private static final String INTENTS_URL = "/api/v1/intents";

    /** Tomorrow's date as yyyy-MM-dd — avoids the "past date" edge case. */
    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T17-1: Cancel Intent — Happy Path ─────────────────────────────────────

    @Test
    void t17_1_cancelIntent_openStatus_returns200_statusCancelled_overlapReleased()
            throws Exception {
        String token     = authFactory.createAndLoginUser("cancel.happy@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // Create intent [17:00, 18:00]
        MvcResult createResult = mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/intent/id").asText();

        // Cancel the intent → 200 OK
        mockMvc.perform(delete(INTENTS_URL + "/" + intentId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DB: status must be CANCELLED (soft-delete — row is retained)
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, intentId);
        assertThat(dbStatus).isEqualTo("CANCELLED");

        // Overlap guard is lifted: same user can create a new intent in the same window
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated());
    }

    // ── T17-2: Cancel Intent — Already CONSUMED (terminal state) ──────────────

    /**
     * {@code CONSUMED} is a terminal state (set after a MatchProposal is confirmed).
     * Cancelling a CONSUMED intent must return {@code INTENT_ALREADY_CONSUMED}.
     *
     * <p>Note: the original todo said "MATCHING → INTENT_NOT_OPEN". The actual
     * {@code WalkIntent.cancel()} implementation allows MATCHING → CANCELLED.
     * The correct terminal-state test uses CONSUMED — corrected here.
     */
    @Test
    void t17_2_cancelIntent_consumedStatus_returns400_INTENT_ALREADY_CONSUMED()
            throws Exception {
        String token     = authFactory.createAndLoginUser("cancel.consumed@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // Create intent via API
        MvcResult createResult = mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/intent/id").asText();

        // Force the intent into the CONSUMED terminal state via JDBC
        dataSeeder.forceIntentStatus(intentId, "CONSUMED");

        // Cancel → 400 INTENT_ALREADY_CONSUMED
        mockMvc.perform(delete(INTENTS_URL + "/" + intentId)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_ALREADY_CONSUMED"));
    }

    // ── T17-3: Cancel Intent — Not Owner ──────────────────────────────────────

    @Test
    void t17_3_cancelIntent_notOwner_returns400_INTENT_NOT_OWNER() throws Exception {
        String tokenA    = authFactory.createAndLoginUser("cancel.ownerA@example.com", "Password1!");
        String tokenB    = authFactory.createAndLoginUser("cancel.ownerB@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // User A creates intent
        MvcResult createResult = mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/intent/id").asText();

        // User B attempts to cancel User A's intent → 400 INTENT_NOT_OWNER
        mockMvc.perform(delete(INTENTS_URL + "/" + intentId)
                        .header("Authorization", tokenB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_NOT_OWNER"));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String buildIntentJson(String hotspotId, String date,
                                   float timeStart, float timeEnd) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("hotspot_id",        hotspotId);
        map.put("date",              date);
        map.put("time_start",        timeStart);
        map.put("time_end",          timeEnd);
        map.put("age_min",           18);
        map.put("age_max",           60);
        map.put("is_private",        false);
        map.put("invited_friend_id", null);
        return objectMapper.writeValueAsString(map);
    }
}
