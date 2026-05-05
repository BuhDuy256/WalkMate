package com.walkmate.domain.report;

import java.util.List;
import java.util.Optional;

public interface SessionReportRepository {

    void save(SessionReport report);

    boolean existsBySessionAndReporter(String sessionId, String reporterId);

    /** Returns the report the caller submitted for a specific session, if any. */
    Optional<SessionReport> findBySessionAndReporter(String sessionId, String reporterId);

    // ── Admin query methods ────────────────────────────────────────────────────

    Optional<SessionReport> findById(String reportId);

    /** Returns all reports ordered by {@code created_at DESC}. */
    List<SessionReport> findAll();

    /** Returns reports filtered by status ({@code OPEN}, {@code APPROVED}, or {@code REJECTED}). */
    List<SessionReport> findByStatus(String status);

    /** Persists status and resolution fields back to the database row. */
    void update(SessionReport report);
}
