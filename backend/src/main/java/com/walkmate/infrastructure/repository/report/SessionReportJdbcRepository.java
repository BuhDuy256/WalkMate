package com.walkmate.infrastructure.repository.report;

import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SessionReportJdbcRepository implements SessionReportRepository {

    private final JdbcClient jdbcClient;

    @Override
    public void save(SessionReport report) {
        jdbcClient.sql("""
                INSERT INTO session_report
                    (report_id, session_id, reporter_id, reported_user_id, reason, evidence_url, created_at)
                VALUES
                    (:reportId, :sessionId, :reporterId, :reportedUserId, :reason, :evidenceUrl, :createdAt)
                """)
                .param("reportId",       UUID.fromString(report.getReportId()))
                .param("sessionId",      UUID.fromString(report.getSessionId()))
                .param("reporterId",     UUID.fromString(report.getReporterId()))
                .param("reportedUserId", UUID.fromString(report.getReportedUserId()))
                .param("reason",         report.getReason())
                .param("evidenceUrl",    report.getEvidenceUrl())
                .param("createdAt",      java.sql.Timestamp.from(report.getCreatedAt()))
                .update();
    }

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
}
