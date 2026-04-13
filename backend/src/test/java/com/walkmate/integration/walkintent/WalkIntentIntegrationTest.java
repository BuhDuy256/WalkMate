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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for walk intent creation and listing.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-15 Create Walk Intent</b> — T15-0 (auth guard), T15-1 (happy path),
 *       T15-2 (overlapping intent), T15-3 (overlapping session),
 *       T15-4 (invalid time range), T15-5 (invalid age range),
 *       T15-6 (private friend not accepted)</li>
 *   <li><b>UC-16 View My Active Intents</b> — T16-1 (list OPEN intents)</li>
 * </ul>
 *
 * <h3>Key conventions</h3>
 * <ul>
 *   <li>The controller converts {@code date + time_start/time_end} floats to
 *       {@link Instant} using {@code Asia/Ho_Chi_Minh} timezone. Time floats are
 *       fractional hours: {@code 17.5 = 17:30}, {@code 9.0 = 09:00}.</li>
 *   <li>Minimum walk duration is 15 minutes ({@code WalkIntent.MIN_WALK_DURATION}).</li>
 *   <li>{@code GET /api/v1/intents} returns only {@code OPEN/MATCHING} intents with
 *       {@code is_private = false} for the authenticated user.</li>
 *   <li>T15-3 seeds a PENDING walk_session directly via JDBC (full FK chain:
 *       walk_intent → match_proposal → walk_session) to avoid dependency on UC-17+.</li>
 * </ul>
 */
class WalkIntentIntegrationTest extends AbstractIntegrationTest {

    private static final String INTENTS_URL  = "/api/v1/intents";
    private static final ZoneId VN_ZONE      = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Tomorrow's date as a yyyy-MM-dd string (avoids "past date" edge cases). */
    private static final String TOMORROW = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    // ── T15-0: Create Intent — Unauthenticated Guard ──────────────────────────

    @Test
    void t15_0_createIntent_noAuth_returns401() throws Exception {
        mockMvc.perform(post(INTENTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── T15-1: Create Intent — Happy Path ─────────────────────────────────────

    @Test
    void t15_1_createIntent_validPayload_returns201_statusOpen() throws Exception {
        String token    = authFactory.createAndLoginUser("intent.happy@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f, 18, 60, false, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent.status").value("OPEN"))
                .andExpect(jsonPath("$.data.intent.expires_at").isNotEmpty())
                .andExpect(jsonPath("$.data.intent.id").isNotEmpty());
    }

    // ── T15-2: Create Intent — Overlapping OPEN Intent (Invariant I-1) ────────

    @Test
    void t15_2_createIntent_overlappingOpenIntent_returns400_INTENT_OVERLAPPING() throws Exception {
        String token     = authFactory.createAndLoginUser("intent.overlap@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // First intent: [17:00, 18:00]
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f, 18, 60, false, null)))
                .andExpect(status().isCreated());

        // Second intent overlapping: [17:30, 19:00] — same user, same hotspot
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.5f, 19.0f, 18, 60, false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_OVERLAPPING"));
    }

    // ── T15-3: Create Intent — Overlapping PENDING Session (Invariant I-1) ────

    @Test
    void t15_3_createIntent_overlappingPendingSession_returns400_INTENT_OVERLAPPING_SESSION()
            throws Exception {
        String token  = authFactory.createAndLoginUser("intent.session@example.com", "Password1!");
        String token2 = authFactory.createAndLoginUser("intent.session2@example.com", "Password1!");

        String userIdA   = getUserIdByEmail("intent.session@example.com");
        String userIdB   = getUserIdByEmail("intent.session2@example.com");
        String hotspotId = dataSeeder.seedHotspot();

        // Seed a PENDING walk_session covering [17:00, 18:00] for userA
        Instant start = toInstant(TOMORROW, 17.0f);
        Instant end   = toInstant(TOMORROW, 18.0f);
        dataSeeder.seedPendingSession(userIdA, userIdB, hotspotId, start, end);

        // Attempt to create an intent for userA in the same window → blocked
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f, 18, 60, false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_OVERLAPPING_SESSION"));
    }

    // ── T15-4: Create Intent — Invalid Time Range ──────────────────────────────

    @Test
    void t15_4_createIntent_endBeforeStart_returns400_INVALID_TIME_RANGE() throws Exception {
        String token     = authFactory.createAndLoginUser("intent.timerange@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // time_start (18:00) > time_end (17:00)
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 18.0f, 17.0f, 18, 60, false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));
    }

    // ── T15-5: Create Intent — Invalid Age Range ───────────────────────────────

    @Test
    void t15_5_createIntent_ageMinGreaterThanAgeMax_returns400_INVALID_AGE_RANGE() throws Exception {
        String token     = authFactory.createAndLoginUser("intent.agerange@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // age_min (40) > age_max (30)
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f, 40, 30, false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_AGE_RANGE"));
    }

    // ── T15-6: Create Private Intent — Friend Not Accepted (Invariant I-7) ────

    @Test
    void t15_6_createPrivateIntent_friendNotAccepted_returns400_INTENT_PRIVATE_FRIEND_NOT_ACCEPTED()
            throws Exception {
        String token     = authFactory.createAndLoginUser("intent.private@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // Register a second user (not a friend of the first)
        authFactory.createAndLoginUser("intent.stranger@example.com", "Password1!");
        String strangerId = getUserIdByEmail("intent.stranger@example.com");

        // is_private=true, invited_friend_id = non-friend user → INTENT_PRIVATE_FRIEND_NOT_ACCEPTED
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 17.0f, 18.0f, 18, 60,
                                true, strangerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTENT_PRIVATE_FRIEND_NOT_ACCEPTED"));
    }

    // ── T16-1: View My Active Intents ─────────────────────────────────────────

    @Test
    void t16_1_listActiveIntents_returns200_withOpenIntentsOnly() throws Exception {
        String token     = authFactory.createAndLoginUser("intent.list@example.com", "Password1!");
        String hotspotId = dataSeeder.seedHotspot();

        // Create two non-overlapping OPEN intents for the same user
        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 9.0f, 10.0f, 18, 60, false, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(INTENTS_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildIntentJson(hotspotId, TOMORROW, 11.0f, 12.0f, 18, 60, false, null)))
                .andExpect(status().isCreated());

        // List active intents — should see both (OPEN, is_private=false)
        mockMvc.perform(get(INTENTS_URL)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[1].status").value("OPEN"));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds the JSON body for {@code POST /api/v1/intents}.
     * Uses snake_case keys to match Jackson {@code @JsonProperty} mappings.
     */
    private String buildIntentJson(String hotspotId, String date,
                                   float timeStart, float timeEnd,
                                   int ageMin, int ageMax,
                                   boolean isPrivate, String invitedFriendId) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("hotspot_id",       hotspotId);
        map.put("date",             date);
        map.put("time_start",       timeStart);
        map.put("time_end",         timeEnd);
        map.put("age_min",          ageMin);
        map.put("age_max",          ageMax);
        map.put("is_private",       isPrivate);
        map.put("invited_friend_id", invitedFriendId);
        return objectMapper.writeValueAsString(map);
    }

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    /** Converts a yyyy-MM-dd date string + fractional-hour float to Instant (VN timezone). */
    private Instant toInstant(String date, float hourFloat) {
        int totalMinutes = Math.round(hourFloat * 60);
        LocalDate localDate = LocalDate.parse(date);
        LocalTime localTime = LocalTime.of(totalMinutes / 60, totalMinutes % 60);
        return LocalDateTime.of(localDate, localTime).atZone(VN_ZONE).toInstant();
    }
}
