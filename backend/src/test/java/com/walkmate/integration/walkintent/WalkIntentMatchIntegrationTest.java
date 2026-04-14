package com.walkmate.integration.walkintent;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the walk intent matching trigger endpoint.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-18 Trigger Match (Internal API)</b> — T18-1 (no candidate → 204),
 *       T18-2 (candidate found → 200 + proposal), T18-3 (intent not OPEN → 400)</li>
 * </ul>
 *
 * <h3>Inline-match side-effect and why T18-2 uses JDBC seeding</h3>
 * <p>{@code WalkIntentCommandService.createIntent()} attempts an inline match after
 * persisting the intent. If a compatible candidate already exists when User B's intent
 * is created via {@code POST /intents}, the engine immediately pairs them — both intents
 * become {@code MATCHING} and the proposal is created automatically. Calling
 * {@code POST /intents/{id}/match} on the now-MATCHING intent would then return
 * {@code INVALID_INTENT_DATA} (not the "match found" happy path).
 *
 * <p>To isolate the explicit match endpoint:
 * <ol>
 *   <li>User A creates their intent via the API — no candidates exist yet → intent stays OPEN.</li>
 *   <li>User B is registered via the API so their {@code user_account} FK row exists.</li>
 *   <li>User B's intent is seeded <em>directly via JDBC</em> (bypassing the inline-match
 *       logic entirely).</li>
 *   <li>{@code POST /intents/{intentIdA}/match} is called — the matching engine now
 *       finds User B's OPEN intent as a candidate → proposal created → 200 OK.</li>
 * </ol>
 *
 * <h3>Matching compatibility requirements (V100 schema, RuleBasedMatchingStrategy)</h3>
 * <ul>
 *   <li>Same {@code hotspot_id}.</li>
 *   <li>Time-window overlap ≥ {@code MIN_WALK_DURATION} (15 minutes).</li>
 *   <li>Age-constraint ranges overlap: {@code candidate.ageMin ≤ caller.ageMax}
 *       AND {@code candidate.ageMax ≥ caller.ageMin}.</li>
 *   <li>{@code is_private = false} (public intent).</li>
 * </ul>
 */
class WalkIntentMatchIntegrationTest extends AbstractIntegrationTest {

    private static final String INTENTS_URL = "/api/v1/intents";
    private static final ZoneId VN_ZONE     = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Tomorrow's date as yyyy-MM-dd — avoids the "past date" edge case. */
    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T18-1: Trigger Match — No Candidate Found (204 No Content) ────────────

    @Test
    void t18_1_triggerMatch_noCandidate_returns204_intentStaysOpen() throws Exception {
        String token     = authFactory.createAndLoginUser("match.none@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // Create User A's intent — no other intents in DB → no candidate
        MvcResult createResult = mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/intent/id").asText();

        // Trigger match → no candidate → 204 No Content (empty body)
        mockMvc.perform(post(INTENTS_URL + "/" + intentId + "/match")
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        // Intent must remain OPEN in DB (matching engine had nothing to pair with)
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, intentId);
        assertThat(dbStatus).isEqualTo("OPEN");
    }

    // ── T18-2: Trigger Match — Candidate Found (200 OK + Proposal) ───────────

    @Test
    void t18_2_triggerMatch_candidateFound_returns200_withPendingProposal_intentMatchingInDB()
            throws Exception {
        String hotspotId = dataSeeder.seedHotspot();

        // User A creates intent [17:00, 18:00] — no candidates yet → stays OPEN
        String tokenA = authFactory.createAndLoginUser("match.found.a@example.com", "Password1!");
        MvcResult createResult = mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentIdA = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/intent/id").asText();

        // Register User B (needed for the user_account FK), but seed their intent
        // directly via JDBC to avoid triggering the inline-match on POST /intents.
        authFactory.createAndLoginUser("match.found.b@example.com", "Password1!");
        String userIdB = getUserIdByEmail("match.found.b@example.com");

        // User B's intent: same hotspot, [17:30, 18:30] — 30-min overlap with A → compatible
        Instant bStart = toInstant(TOMORROW, 17.5f);
        Instant bEnd   = toInstant(TOMORROW, 18.5f);
        dataSeeder.seedOpenIntent(userIdB, hotspotId, bStart, bEnd, 18, 60);

        // Trigger match for User A → finds User B's intent → 200 OK with proposal
        mockMvc.perform(post(INTENTS_URL + "/" + intentIdA + "/match")
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.proposal_id").isNotEmpty());

        // Assert User A's intent is now MATCHING (Invariant I-4)
        String dbStatusA = jdbcTemplate.queryForObject(
                "SELECT status FROM public.walk_intent WHERE intent_id = ?::uuid",
                String.class, intentIdA);
        assertThat(dbStatusA).isEqualTo("MATCHING");

        // Assert a PENDING proposal row exists referencing User A's intent
        Integer proposalCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM public.match_proposal
                WHERE (intent_id_a = ?::uuid OR intent_id_b = ?::uuid)
                  AND status = 'PENDING'::proposal_status
                """,
                Integer.class, intentIdA, intentIdA);
        assertThat(proposalCount).isEqualTo(1);
    }

    // ── T18-3: Trigger Match — Intent Not OPEN (Invariant I-4) ───────────────

    @Test
    void t18_3_triggerMatch_intentNotOpen_returns400_INVALID_INTENT_DATA() throws Exception {
        String token     = authFactory.createAndLoginUser("match.notopen@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // Create intent via API → OPEN
        MvcResult createResult = mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f)))
                .andExpect(status().isCreated())
                .andReturn();

        String intentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/intent/id").asText();

        // Advance to MATCHING via JDBC (simulates a proposal having already locked this intent)
        dataSeeder.forceIntentStatus(intentId, "MATCHING");

        // Trigger match on a MATCHING intent → 400 INVALID_INTENT_DATA
        mockMvc.perform(post(INTENTS_URL + "/" + intentId + "/match")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INTENT_DATA"));
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

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    /** Converts a yyyy-MM-dd date string + fractional-hour float to an Instant (VN timezone). */
    private Instant toInstant(String date, float hourFloat) {
        int totalMinutes = Math.round(hourFloat * 60);
        LocalDate localDate = LocalDate.parse(date);
        LocalTime localTime = LocalTime.of(totalMinutes / 60, totalMinutes % 60);
        return LocalDateTime.of(localDate, localTime).atZone(VN_ZONE).toInstant();
    }
}
