package com.walkmate.infrastructure.repository.report;

import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
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
public class SessionReportJdbcRepository implements SessionReportRepository {

    private final JdbcClient jdbcClient;

    // ── Write ─────────────────────────────────────────────────────────────────

    @Override
    public void save(SessionReport report) {
        jdbcClient.sql("""
                INSERT INTO session_report
                    (report_id, session_id, reporter_id, reported_user_id,
                     reason, evidence_url, created_at,
                     status, applied_trust_delta)
                VALUES
                    (:reportId, :sessionId, :reporterId, :reportedUserId,
                     :reason, :evidenceUrl, :createdAt,
                     :status::report_status, :appliedTrustDelta)
                """)
                .param("reportId",           UUID.fromString(report.getReportId()))
                .param("sessionId",          UUID.fromString(report.getSessionId()))
                .param("reporterId",         UUID.fromString(report.getReporterId()))
                .param("reportedUserId",     UUID.fromString(report.getReportedUserId()))
                .param("reason",             report.getReason())
                .param("evidenceUrl",        report.getEvidenceUrl())
                .param("createdAt",          Timestamp.from(report.getCreatedAt()))
                .param("status",             report.getStatus())
                .param("appliedTrustDelta",  report.getAppliedTrustDelta())
                .update();
    }

    @Override
    public void update(SessionReport report) {
        jdbcClient.sql("""
                UPDATE session_report
                SET status             = :status::report_status,
                    applied_trust_delta = :appliedTrustDelta,
                    resolved_by        = :resolvedBy,
                    resolved_at        = :resolvedAt,
                    resolution_note    = :resolutionNote
                WHERE report_id = :reportId
                """)
                .param("status",             report.getStatus())
                .param("appliedTrustDelta",  report.getAppliedTrustDelta())
                .param("resolvedBy",         report.getResolvedBy() != null
                                                ? UUID.fromString(report.getResolvedBy()) : null)
                .param("resolvedAt",         report.getResolvedAt() != null
                                                ? Timestamp.from(report.getResolvedAt()) : null)
                .param("resolutionNote",     report.getResolutionNote())
                .param("reportId",           UUID.fromString(report.getReportId()))
                .update();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    public boolean existsBySessionAndReporter(String sessionId, String reporterId) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM session_report
                WHERE session_id  = :sessionId
                  AND reporter_id = :reporterId
                """)
                .param("sessionId",  UUID.fromString(sessionId))
                .param("reporterId", UUID.fromString(reporterId))
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public Optional<SessionReport> findById(String reportId) {
        return jdbcClient.sql("""
                SELECT * FROM session_report
                WHERE report_id = :reportId
                """)
                .param("reportId", UUID.fromString(reportId))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Override
    public List<SessionReport> findAll() {
        return jdbcClient.sql("""
                SELECT * FROM session_report
                ORDER BY created_at DESC
                """)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    @Override
    public List<SessionReport> findByStatus(String status) {
        return jdbcClient.sql("""
                SELECT * FROM session_report
                WHERE status = :status::report_status
                ORDER BY created_at DESC
                """)
                .param("status", status)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    // ── Row mapper ────────────────────────────────────────────────────────────

    private SessionReport mapRow(ResultSet rs) throws SQLException {
        Timestamp resolvedAtTs = rs.getTimestamp("resolved_at");
        String    resolvedBy   = rs.getString("resolved_by");

        return new SessionReport(
                rs.getString("report_id"),
                rs.getString("session_id"),
                rs.getString("reporter_id"),
                rs.getString("reported_user_id"),
                rs.getString("reason"),
                rs.getString("evidence_url"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("status"),
                rs.getInt("applied_trust_delta"),
                resolvedBy,
                resolvedAtTs != null ? resolvedAtTs.toInstant() : null,
                rs.getString("resolution_note")
        );
    }
}
