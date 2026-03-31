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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WalkSessionJdbcRepository implements WalkSessionRepository {

    private final JdbcClient jdbcClient;

    @Override
    public WalkSession save(WalkSession session) {
        final String sql = """
                INSERT INTO walk_session (
                    session_id, proposal_id, user_id_a, user_id_b,
                    meeting_point_lat, meeting_point_lng,
                    scheduled_start, scheduled_end,
                    status, created_at, started_at, ended_at
                )
                VALUES (
                    :sessionId, :proposalId, :userIdA, :userIdB,
                    :meetingPointLat, :meetingPointLng,
                    :scheduledStart, :scheduledEnd,
                    CAST(:status AS walk_session_status), :createdAt, :startedAt, :endedAt
                )
                ON CONFLICT (session_id) DO UPDATE SET
                    status     = CAST(EXCLUDED.status AS walk_session_status),
                    started_at = EXCLUDED.started_at,
                    ended_at   = EXCLUDED.ended_at
                """;

        jdbcClient.sql(sql)
                .param("sessionId",        UUID.fromString(session.getSessionId()))
                .param("proposalId",       UUID.fromString(session.getProposalId()))
                .param("userIdA",          UUID.fromString(session.getUserIdA()))
                .param("userIdB",          UUID.fromString(session.getUserIdB()))
                .param("meetingPointLat",  session.getMeetingPointLat())
                .param("meetingPointLng",  session.getMeetingPointLng())
                .param("scheduledStart",   Timestamp.from(session.getScheduledStart()))
                .param("scheduledEnd",     Timestamp.from(session.getScheduledEnd()))
                .param("status",           session.getStatus().name())
                .param("createdAt",        Timestamp.from(session.getCreatedAt()))
                .param("startedAt",        session.getStartedAt() != null
                        ? Timestamp.from(session.getStartedAt()) : null)
                .param("endedAt",          session.getEndedAt() != null
                        ? Timestamp.from(session.getEndedAt()) : null)
                .update();

        return session;
    }

    @Override
    public Optional<WalkSession> findByProposalId(String proposalId) {
        final String sql = selectAll() + "WHERE session_id = :proposalId";
        return jdbcClient.sql(sql)
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String selectAll() {
        return """
                SELECT session_id::text, proposal_id::text,
                       user_id_a::text, user_id_b::text,
                       meeting_point_lat, meeting_point_lng,
                       scheduled_start, scheduled_end,
                       status, created_at, started_at, ended_at
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
                rs.getTimestamp("started_at") != null
                        ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("ended_at") != null
                        ? rs.getTimestamp("ended_at").toInstant() : null
        );
    }
}
