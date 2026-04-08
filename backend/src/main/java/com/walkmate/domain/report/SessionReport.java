package com.walkmate.domain.report;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class SessionReport {

    private String  reportId;
    private String  sessionId;
    private String  reporterId;
    private String  reportedUserId;
    private String  reason;
    private String  evidenceUrl;
    private Instant createdAt;

    protected SessionReport() {}

    /** Rehydration constructor — used by the repository when loading from DB. */
    public SessionReport(String reportId, String sessionId, String reporterId,
                         String reportedUserId, String reason, String evidenceUrl,
                         Instant createdAt) {
        this.reportId       = reportId;
        this.sessionId      = sessionId;
        this.reporterId     = reporterId;
        this.reportedUserId = reportedUserId;
        this.reason         = reason;
        this.evidenceUrl    = evidenceUrl;
        this.createdAt      = createdAt;
    }

    private SessionReport(String sessionId, String reporterId, String reportedUserId,
                          String reason, String evidenceUrl) {
        this.reportId       = UUID.randomUUID().toString();
        this.sessionId      = sessionId;
        this.reporterId     = reporterId;
        this.reportedUserId = reportedUserId;
        this.reason         = reason;
        this.evidenceUrl    = evidenceUrl;
        this.createdAt      = Instant.now();
    }

    public static SessionReport create(String sessionId, String reporterId,
                                       String reportedUserId, String reason, String evidenceUrl) {
        return new SessionReport(sessionId, reporterId, reportedUserId, reason, evidenceUrl);
    }
}
