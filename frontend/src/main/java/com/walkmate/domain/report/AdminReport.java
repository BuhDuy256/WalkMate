package com.walkmate.domain.report;

public class AdminReport {

    public enum Status { PENDING, APPROVED, REJECTED }

    public enum Reason { SAFETY_CONCERN, MISCONDUCT, EMERGENCY, OTHER }

    private final String reportId;
    private final String sessionId;
    private final String reporterId;
    private final String reporterName;
    private final String reportedUserId;
    private final String reportedUserName;
    private final Reason reason;
    private final String evidenceUrl;
    private final Status status;
    private final int    appliedTrustDelta;
    private final String createdAt;
    private final String resolvedBy;
    private final String resolvedAt;
    private final String resolutionNote;

    public AdminReport(String reportId, String sessionId,
                       String reporterId, String reporterName,
                       String reportedUserId, String reportedUserName,
                       Reason reason, String evidenceUrl,
                       Status status, int appliedTrustDelta,
                       String createdAt, String resolvedBy,
                       String resolvedAt, String resolutionNote) {
        this.reportId          = reportId;
        this.sessionId         = sessionId;
        this.reporterId        = reporterId;
        this.reporterName      = reporterName;
        this.reportedUserId    = reportedUserId;
        this.reportedUserName  = reportedUserName;
        this.reason            = reason;
        this.evidenceUrl       = evidenceUrl;
        this.status            = status;
        this.appliedTrustDelta = appliedTrustDelta;
        this.createdAt         = createdAt;
        this.resolvedBy        = resolvedBy;
        this.resolvedAt        = resolvedAt;
        this.resolutionNote    = resolutionNote;
    }

    public String getReportId()          { return reportId; }
    public String getSessionId()         { return sessionId; }
    public String getReporterId()        { return reporterId; }
    public String getReporterName()      { return reporterName; }
    public String getReportedUserId()    { return reportedUserId; }
    public String getReportedUserName()  { return reportedUserName; }
    public Reason getReason()            { return reason; }
    public String getEvidenceUrl()       { return evidenceUrl; }
    public Status getStatus()            { return status; }
    public int    getAppliedTrustDelta() { return appliedTrustDelta; }
    public String getCreatedAt()         { return createdAt; }
    public String getResolvedBy()        { return resolvedBy; }
    public String getResolvedAt()        { return resolvedAt; }
    public String getResolutionNote()    { return resolutionNote; }
}
