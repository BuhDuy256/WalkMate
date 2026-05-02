package com.walkmate.domain.report;

import com.walkmate.domain.shared.exception.DomainException;
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

    // ── Resolution state (populated after admin acts) ─────────────────────────
    private String  status;           // OPEN | APPROVED | REJECTED
    private int     appliedTrustDelta; // actual delta written to user_account at submission
    private String  resolvedBy;       // admin userId; null while OPEN
    private Instant resolvedAt;       // null while OPEN
    private String  resolutionNote;   // null while OPEN

    protected SessionReport() {}

    /** Rehydration constructor — used by the repository when loading from DB. */
    public SessionReport(String reportId, String sessionId, String reporterId,
                         String reportedUserId, String reason, String evidenceUrl,
                         Instant createdAt, String status, int appliedTrustDelta,
                         String resolvedBy, Instant resolvedAt, String resolutionNote) {
        this.reportId           = reportId;
        this.sessionId          = sessionId;
        this.reporterId         = reporterId;
        this.reportedUserId     = reportedUserId;
        this.reason             = reason;
        this.evidenceUrl        = evidenceUrl;
        this.createdAt          = createdAt;
        this.status             = status;
        this.appliedTrustDelta  = appliedTrustDelta;
        this.resolvedBy         = resolvedBy;
        this.resolvedAt         = resolvedAt;
        this.resolutionNote     = resolutionNote;
    }

    private SessionReport(String sessionId, String reporterId, String reportedUserId,
                          String reason, String evidenceUrl) {
        this.reportId          = UUID.randomUUID().toString();
        this.sessionId         = sessionId;
        this.reporterId        = reporterId;
        this.reportedUserId    = reportedUserId;
        this.reason            = reason;
        this.evidenceUrl       = evidenceUrl;
        this.createdAt         = Instant.now();
        this.status            = "OPEN";
        this.appliedTrustDelta = 0;
    }

    public static SessionReport create(String sessionId, String reporterId,
                                       String reportedUserId, String reason, String evidenceUrl) {
        return new SessionReport(sessionId, reporterId, reportedUserId, reason, evidenceUrl);
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    /** Records the exact trust delta that was applied at submission time. */
    public void setAppliedTrustDelta(int delta) {
        this.appliedTrustDelta = delta;
    }

    // ── State machine ─────────────────────────────────────────────────────────

    /** Returns true if this report has already been approved or rejected by an admin. */
    public boolean isResolved() {
        return "APPROVED".equals(status) || "REJECTED".equals(status);
    }

    /**
     * Transitions the report from OPEN to APPROVED.
     * The trust penalty applied at submission time remains unchanged.
     *
     * @throws DomainException if the report is already resolved
     */
    public void approve(String adminUserId, String note) {
        if (isResolved()) {
            throw new DomainException(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        }
        this.status         = "APPROVED";
        this.resolvedBy     = adminUserId;
        this.resolvedAt     = Instant.now();
        this.resolutionNote = note;
    }

    /**
     * Transitions the report from OPEN to REJECTED.
     * The application layer is responsible for reversing {@link #appliedTrustDelta}.
     *
     * @throws DomainException if the report is already resolved
     */
    public void reject(String adminUserId, String note) {
        if (isResolved()) {
            throw new DomainException(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        }
        this.status         = "REJECTED";
        this.resolvedBy     = adminUserId;
        this.resolvedAt     = Instant.now();
        this.resolutionNote = note;
    }
}
