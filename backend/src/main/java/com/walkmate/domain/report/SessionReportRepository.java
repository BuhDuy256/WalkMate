package com.walkmate.domain.report;

public interface SessionReportRepository {

    void save(SessionReport report);

    boolean existsBySessionAndReporter(String sessionId, String reporterId);
}
