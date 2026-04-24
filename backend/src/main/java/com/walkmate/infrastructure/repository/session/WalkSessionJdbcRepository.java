package com.walkmate.infrastructure.repository.session;

import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalkSessionJdbcRepository implements WalkSessionRepository {

    private final JdbcClient jdbcClient;

    // ── Save (upsert) ─────────────────────────────────────────────────────────

    @Override
    public WalkSession save(WalkSession session) {
        final String sql = """
                INSERT INTO walk_session (
                    session_id, proposal_id, user_id_a, user_id_b,
                    meeting_point_lat, meeting_point_lng,
                    scheduled_start, scheduled_end,
                    status, created_at, started_at, ended_at,
                    user_a_activated_at, user_b_activated_at,
                    cancellation_reason, cancelled_by,
                    version,
                    user_a_status, user_b_status,
                    user_a_ended_at, user_b_ended_at,
                    user_a_distance_km, user_a_duration_seconds,
                    user_b_distance_km, user_b_duration_seconds
                )
                VALUES (
                    :sessionId, :proposalId, :userIdA, :userIdB,
                    :meetingPointLat, :meetingPointLng,
                    :scheduledStart, :scheduledEnd,
                    CAST(:status AS walk_session_status), :createdAt, :startedAt, :endedAt,
                    :userAActivatedAt, :userBActivatedAt,
                    :cancellationReason, :cancelledBy,
                    :version,
                    CAST(:userAStatus AS walk_session_status),
                    CAST(:userBStatus AS walk_session_status),
                    :userAEndedAt, :userBEndedAt,
                    :userADistanceKm, :userADurationSeconds,
                    :userBDistanceKm, :userBDurationSeconds
                )
                ON CONFLICT (session_id) DO UPDATE SET
                    status                  = CAST(EXCLUDED.status AS walk_session_status),
                    started_at              = EXCLUDED.started_at,
                    ended_at                = EXCLUDED.ended_at,
                    user_a_activated_at     = EXCLUDED.user_a_activated_at,
                    user_b_activated_at     = EXCLUDED.user_b_activated_at,
                    cancellation_reason     = EXCLUDED.cancellation_reason,
                    cancelled_by            = EXCLUDED.cancelled_by,
                    version                 = EXCLUDED.version,
                    user_a_status           = EXCLUDED.user_a_status,
                    user_b_status           = EXCLUDED.user_b_status,
                    user_a_ended_at         = EXCLUDED.user_a_ended_at,
                    user_b_ended_at         = EXCLUDED.user_b_ended_at,
                    user_a_distance_km      = EXCLUDED.user_a_distance_km,
                    user_a_duration_seconds = EXCLUDED.user_a_duration_seconds,
                    user_b_distance_km      = EXCLUDED.user_b_distance_km,
                    user_b_duration_seconds = EXCLUDED.user_b_duration_seconds
                """;

        jdbcClient.sql(sql)
                .param("sessionId",             UUID.fromString(session.getSessionId()))
                .param("proposalId",            UUID.fromString(session.getProposalId()))
                .param("userIdA",               UUID.fromString(session.getUserIdA()))
                .param("userIdB",               UUID.fromString(session.getUserIdB()))
                .param("meetingPointLat",       session.getMeetingPointLat())
                .param("meetingPointLng",       session.getMeetingPointLng())
                .param("scheduledStart",        Timestamp.from(session.getScheduledStart()))
                .param("scheduledEnd",          Timestamp.from(session.getScheduledEnd()))
                .param("status",                session.getStatus().name())
                .param("createdAt",             Timestamp.from(session.getCreatedAt()))
                .param("startedAt",             toTs(session.getStartedAt()))
                .param("endedAt",               toTs(session.getEndedAt()))
                .param("userAActivatedAt",      toTs(session.getUserAActivatedAt()))
                .param("userBActivatedAt",      toTs(session.getUserBActivatedAt()))
                .param("cancellationReason",    session.getCancellationReason())
                .param("cancelledBy",           session.getCancelledBy() != null
                        ? UUID.fromString(session.getCancelledBy()) : null)
                .param("version",               session.getVersion())
                .param("userAStatus",           session.getUserAStatus().name())
                .param("userBStatus",           session.getUserBStatus().name())
                .param("userAEndedAt",          toTs(session.getUserAEndedAt()))
                .param("userBEndedAt",          toTs(session.getUserBEndedAt()))
                .param("userADistanceKm",       session.getUserADistanceKm())
                .param("userADurationSeconds",  session.getUserADurationSeconds())
                .param("userBDistanceKm",       session.getUserBDistanceKm())
                .param("userBDurationSeconds",  session.getUserBDurationSeconds())
                .update();

        return session;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Override
    public Optional<WalkSession> findById(String sessionId) {
        return jdbcClient.sql(selectAll() + "WHERE session_id = :sessionId")
                .param("sessionId", UUID.fromString(sessionId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public Optional<WalkSession> findByProposalId(String proposalId) {
        return jdbcClient.sql(selectAll() + "WHERE proposal_id = :proposalId")
                .param("proposalId", UUID.fromString(proposalId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public List<WalkSession> findActiveForUser(String userId) {
        final String sql = selectAll() + """
                WHERE (user_id_a = :userId OR user_id_b = :userId)
                  AND status IN ('PENDING', 'ACTIVE')
                ORDER BY scheduled_start ASC
                """;
        return jdbcClient.sql(sql)
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public boolean hasOverlappingActiveSession(String userId, Instant start, Instant end) {
        final String sql = """
                SELECT COUNT(*) FROM walk_session
                WHERE (user_id_a = :userId OR user_id_b = :userId)
                  AND status IN ('PENDING', 'ACTIVE')
                  AND scheduled_start < :end
                  AND scheduled_end   > :start
                """;
        Integer count = jdbcClient.sql(sql)
                .param("userId", UUID.fromString(userId))
                .param("start",  Timestamp.from(start))
                .param("end",    Timestamp.from(end))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public List<WalkSession> findStalePendingSessions(Instant cutoff) {
        final String sql = selectAll() + """
                WHERE status = 'PENDING'
                  AND created_at < :cutoff
                """;
        return jdbcClient.sql(sql)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<WalkSession> findSessionsPastEndTime(Instant cutoff) {
        final String sql = selectAll() + """
                WHERE status = 'ACTIVE'
                  AND scheduled_end < :cutoff
                """;
        return jdbcClient.sql(sql)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<WalkSession> findPendingSessionsPastNoShowDeadline(Instant cutoff) {
        final String sql = selectAll() + """
                WHERE status = 'PENDING'
                  AND scheduled_start < :cutoff
                """;
        return jdbcClient.sql(sql)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<WalkSession> findHistoryByUserId(String userId) {
        final String sql = selectAll() + """
                WHERE (user_id_a = :userId OR user_id_b = :userId)
                  AND status NOT IN ('PENDING', 'CANCELLED')
                ORDER BY COALESCE(ended_at, started_at, created_at) DESC
                LIMIT 50
                """;
        return jdbcClient.sql(sql)
                .param("userId", UUID.fromString(userId))
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    @Override
    public void logStateChange(String sessionId, SessionStatus from, SessionStatus to,
                               String changedBy, String reason) {
        final String sql = """
                INSERT INTO session_state_change_log
                    (session_id, from_status, to_status, changed_by, reason, changed_at)
                VALUES
                    (:sessionId,
                     CAST(:from AS walk_session_status),
                     CAST(:to   AS walk_session_status),
                     :changedBy, :reason, NOW())
                """;
        jdbcClient.sql(sql)
                .param("sessionId", UUID.fromString(sessionId))
                .param("from",      from != null ? from.name() : null)
                .param("to",        to.name())
                .param("changedBy", changedBy != null ? UUID.fromString(changedBy) : null)
                .param("reason",    reason)
                .update();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String selectAll() {
        return """
                SELECT session_id::text, proposal_id::text,
                       user_id_a::text, user_id_b::text,
                       meeting_point_lat, meeting_point_lng,
                       scheduled_start, scheduled_end,
                       status, created_at, started_at, ended_at,
                       user_a_activated_at, user_b_activated_at,
                       cancellation_reason, cancelled_by::text,
                       version,
                       user_a_status, user_b_status,
                       user_a_ended_at, user_b_ended_at,
                       user_a_distance_km, user_a_duration_seconds,
                       user_b_distance_km, user_b_duration_seconds
                FROM walk_session
                """;
    }

    private WalkSession mapRow(ResultSet rs) throws SQLException {
        return new WalkSession(
                rs.getString("session_id"),
                rs.getString("proposal_id"),
                rs.getString("user_id_a"),
                rs.getString("user_id_b"),
                rs.getDouble("meeting_point_lat"),
                rs.getDouble("meeting_point_lng"),
                rs.getTimestamp("scheduled_start").toInstant(),
                rs.getTimestamp("scheduled_end").toInstant(),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                toInstant(rs, "started_at"),
                toInstant(rs, "ended_at"),
                toInstant(rs, "user_a_activated_at"),
                toInstant(rs, "user_b_activated_at"),
                rs.getString("cancellation_reason"),
                rs.getString("cancelled_by"),
                rs.getLong("version"),
                SessionStatus.valueOf(rs.getString("user_a_status")),
                SessionStatus.valueOf(rs.getString("user_b_status")),
                toInstant(rs, "user_a_ended_at"),
                toInstant(rs, "user_b_ended_at"),
                rs.getDouble("user_a_distance_km"),
                rs.getLong("user_a_duration_seconds"),
                rs.getDouble("user_b_distance_km"),
                rs.getLong("user_b_duration_seconds")
        );
    }

    private static Timestamp toTs(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
