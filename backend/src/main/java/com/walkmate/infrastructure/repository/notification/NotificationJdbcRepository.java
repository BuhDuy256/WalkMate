package com.walkmate.infrastructure.repository.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.notification.NotificationStatus;
import com.walkmate.domain.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationJdbcRepository implements NotificationRepository {

    private final JdbcClient   jdbcClient;
    private final ObjectMapper objectMapper;

    // ── Save (upsert) ─────────────────────────────────────────────────────────

    @Override
    public Notification save(Notification notification) {
        final String sql = """
                INSERT INTO notification (notification_id, user_id, type, payload, status, created_at, read_at)
                VALUES (
                    :notificationId, :userId, :type,
                    CAST(:payload AS jsonb),
                    CAST(:status AS notification_status),
                    :createdAt, :readAt
                )
                ON CONFLICT (notification_id) DO UPDATE SET
                    status   = CAST(EXCLUDED.status AS notification_status),
                    read_at  = EXCLUDED.read_at
                """;

        jdbcClient.sql(sql)
                .param("notificationId", UUID.fromString(notification.getNotificationId()))
                .param("userId",         UUID.fromString(notification.getUserId()))
                .param("type",           notification.getType().name())
                .param("payload",        toJson(notification.getPayload()))
                .param("status",         notification.getStatus().name())
                .param("createdAt",      Timestamp.from(notification.getCreatedAt()))
                .param("readAt",         toTs(notification.getReadAt()))
                .update();

        return notification;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<Notification> findById(String notificationId) {
        return jdbcClient.sql(selectAll() + "WHERE notification_id = :notificationId")
                .param("notificationId", UUID.fromString(notificationId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public List<Notification> findByUserId(String userId) {
        return jdbcClient.sql(selectAll() + "WHERE user_id = :userId ORDER BY created_at DESC")
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String selectAll() {
        return """
                SELECT notification_id::text, user_id::text, type,
                       payload::text, status, created_at, read_at
                FROM notification
                """;
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        return new Notification(
                rs.getString("notification_id"),
                rs.getString("user_id"),
                NotificationType.valueOf(rs.getString("type")),
                fromJson(rs.getString("payload")),
                NotificationStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                toInstant(rs, "read_at")
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static Timestamp toTs(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
