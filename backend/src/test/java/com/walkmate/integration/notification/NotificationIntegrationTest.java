package com.walkmate.integration.notification;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the notification feed and mark-as-read endpoints.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-39 View Notification Feed</b> — T39-1</li>
 *   <li><b>UC-40 Mark Notification as Read</b> — T40-1, T40-2, T40-3</li>
 * </ul>
 *
 * <h3>Status enum note</h3>
 * The {@code notification_status} PostgreSQL enum has three values:
 * {@code PENDING}, {@code SENT}, {@code READ}. There is no {@code UNREAD} value —
 * "unread" notifications are stored as {@code PENDING}.
 */
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String FEED_URL      = "/api/v1/notifications";
    private static final String MARK_READ_URL = "/api/v1/notifications/{notificationId}/read";

    // ── T39-1: View Notification Feed ─────────────────────────────────────────

    /**
     * T39-1: User A has one PENDING (unread) and one READ notification.
     * Feed returns both; PENDING item has {@code readAt = null};
     * READ item has {@code readAt} set.
     */
    @Test
    void t39_1_viewNotificationFeed_pendingAndRead_bothPresentWithCorrectReadAt() throws Exception {
        String tokenA = authFactory.createAndLoginUser("notif.t391.a@example.com", "Password1!");
        String userIdA = getUserIdByEmail("notif.t391.a@example.com");

        String pendingId = seedNotification(userIdA, "FRIEND_REQUEST_RECEIVED", "PENDING", false);
        String readId    = seedNotification(userIdA, "SESSION_ACTIVE",          "READ",    true);

        String responseBody = mockMvc.perform(get(FEED_URL).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode data =
                objectMapper.readTree(responseBody).get("data");

        boolean foundPending = false;
        boolean foundRead    = false;
        for (com.fasterxml.jackson.databind.JsonNode n : data) {
            String id = n.get("notificationId").asText();
            if (id.equals(pendingId)) {
                assertThat(n.get("status").asText()).isEqualTo("PENDING");
                // readAt must be absent or null
                assertThat(n.has("readAt") && !n.get("readAt").isNull())
                        .as("PENDING notification must have no readAt").isFalse();
                foundPending = true;
            }
            if (id.equals(readId)) {
                assertThat(n.get("status").asText()).isEqualTo("READ");
                assertThat(n.has("readAt") && !n.get("readAt").isNull())
                        .as("READ notification must have readAt set").isTrue();
                foundRead = true;
            }
        }
        assertThat(foundPending).as("PENDING notification must appear in feed").isTrue();
        assertThat(foundRead).as("READ notification must appear in feed").isTrue();
    }

    // ── T40-1: Mark Notification as Read — Happy Path ─────────────────────────

    /**
     * T40-1: User A marks an owned PENDING notification as read.
     * Returns {@code 200 OK}; DB confirms {@code status = 'READ'} and
     * {@code read_at IS NOT NULL}.
     */
    @Test
    void t40_1_markNotificationRead_happyPath_statusUpdatedInDb() throws Exception {
        String tokenA = authFactory.createAndLoginUser("notif.t401.a@example.com", "Password1!");
        String userIdA = getUserIdByEmail("notif.t401.a@example.com");

        String notificationId = seedNotification(userIdA, "PROPOSAL_RECEIVED", "PENDING", false);

        mockMvc.perform(post(MARK_READ_URL, notificationId)
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DB: status updated to READ
        String status = jdbcTemplate.queryForObject(
                "SELECT status::text FROM public.notification " +
                "WHERE notification_id = ?::uuid",
                String.class, notificationId);
        assertThat(status).isEqualTo("READ");

        // DB: read_at is now set
        String readAt = jdbcTemplate.queryForObject(
                "SELECT read_at::text FROM public.notification " +
                "WHERE notification_id = ?::uuid",
                String.class, notificationId);
        assertThat(readAt).isNotNull();
    }

    // ── T40-2: Mark Notification — Not Owner ──────────────────────────────────

    /**
     * T40-2: User B attempts to mark User A's notification as read.
     * Returns HTTP 400, {@code NOTIFICATION_NOT_OWNER}.
     */
    @Test
    void t40_2_markNotificationRead_notOwner_returnsNotOwner() throws Exception {
        authFactory.createAndLoginUser("notif.t402.a@example.com", "Password1!");
        String tokenB = authFactory.createAndLoginUser("notif.t402.b@example.com", "Password1!");
        String userIdA = getUserIdByEmail("notif.t402.a@example.com");

        String notificationId = seedNotification(userIdA, "SESSION_CONFIRMED", "PENDING", false);

        mockMvc.perform(post(MARK_READ_URL, notificationId)
                        .header("Authorization", tokenB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_OWNER"));
    }

    // ── T40-3: Mark Notification — Not Found ──────────────────────────────────

    /**
     * T40-3: Marking a non-existent notification as read returns
     * HTTP 400, {@code NOTIFICATION_NOT_FOUND}.
     */
    @Test
    void t40_3_markNotificationRead_notFound_returnsNotFound() throws Exception {
        String tokenA = authFactory.createAndLoginUser("notif.t403.a@example.com", "Password1!");
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(post(MARK_READ_URL, nonExistentId)
                        .header("Authorization", tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id::text FROM public.user_account WHERE email = ?",
                String.class, email);
    }

    /**
     * Seeds a single notification row via JDBC.
     *
     * @param userId   owner's UUID string
     * @param type     NotificationType enum name (e.g., "PROPOSAL_RECEIVED")
     * @param status   notification_status enum name ("PENDING", "SENT", or "READ")
     * @param withReadAt if true, sets read_at = now() (only meaningful for READ status)
     * @return the generated notification_id as a string
     */
    private String seedNotification(String userId, String type, String status, boolean withReadAt) {
        if (withReadAt) {
            return jdbcTemplate.queryForObject(
                    "INSERT INTO public.notification (user_id, type, payload, status, read_at) " +
                    "VALUES (?::uuid, ?, '{}'::jsonb, ?::notification_status, now()) " +
                    "RETURNING notification_id::text",
                    String.class, userId, type, status);
        }
        return jdbcTemplate.queryForObject(
                "INSERT INTO public.notification (user_id, type, payload, status) " +
                "VALUES (?::uuid, ?, '{}'::jsonb, ?::notification_status) " +
                "RETURNING notification_id::text",
                String.class, userId, type, status);
    }
}
