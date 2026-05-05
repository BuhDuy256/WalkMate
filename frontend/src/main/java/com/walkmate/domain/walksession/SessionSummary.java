package com.walkmate.domain.walksession;

import java.util.Collections;
import java.util.List;

public class SessionSummary {

    private final String sessionId;
    private final WalkSession.Status status;
    private final String scheduledStart;
    private final boolean isReviewed;
    private final boolean isReported;
    private final ReviewSnapshot reviewSnapshot;
    private final ReportSnapshot reportSnapshot;
    private final long terminalAtMs;
    private final double meetingPointLat;
    private final double meetingPointLng;
    private final String hotspotName;
    private final List<ParticipantSummary> participants;

    public SessionSummary(String sessionId, WalkSession.Status status,
                          String scheduledStart, boolean isReviewed, boolean isReported,
                          ReviewSnapshot reviewSnapshot, ReportSnapshot reportSnapshot,
                          long terminalAtMs, double meetingPointLat, double meetingPointLng,
                          String hotspotName, List<ParticipantSummary> participants) {
        this.sessionId        = sessionId;
        this.status           = status;
        this.scheduledStart   = scheduledStart;
        this.isReviewed       = isReviewed;
        this.isReported       = isReported;
        this.reviewSnapshot   = reviewSnapshot;
        this.reportSnapshot   = reportSnapshot;
        this.terminalAtMs     = terminalAtMs;
        this.meetingPointLat  = meetingPointLat;
        this.meetingPointLng  = meetingPointLng;
        this.hotspotName      = hotspotName;
        this.participants     = participants != null ? participants : Collections.emptyList();
    }

    public String getSessionId()                       { return sessionId; }
    public WalkSession.Status getStatus()              { return status; }
    public String getScheduledStart()                  { return scheduledStart; }
    public boolean isReviewed()                        { return isReviewed; }
    public boolean isReported()                        { return isReported; }
    public ReviewSnapshot getReviewSnapshot()          { return reviewSnapshot; }
    public ReportSnapshot getReportSnapshot()          { return reportSnapshot; }
    public long getTerminalAtMs()                      { return terminalAtMs; }
    public double getMeetingPointLat()                 { return meetingPointLat; }
    public double getMeetingPointLng()                 { return meetingPointLng; }
    public String getHotspotName()                     { return hotspotName; }
    public List<ParticipantSummary> getParticipants()  { return participants; }

    public ParticipantSummary getCallerParticipant(String currentUserId) {
        for (ParticipantSummary p : participants) {
            if (p.getParticipantId().equals(currentUserId)) return p;
        }
        return null;
    }

    public ParticipantSummary getPartnerParticipant(String currentUserId) {
        for (ParticipantSummary p : participants) {
            if (!p.getParticipantId().equals(currentUserId)) return p;
        }
        return null;
    }

    public String getPartnerId(String currentUserId) {
        for (ParticipantSummary p : participants) {
            if (!p.getParticipantId().equals(currentUserId)) {
                return p.getParticipantId();
            }
        }
        return null;
    }

    // ── Snapshot types ────────────────────────────────────────────────────────

    public static class ReviewSnapshot {
        private final int ratingStars;
        private final String comment;
        private final List<String> tagIds;

        public ReviewSnapshot(int ratingStars, String comment, List<String> tagIds) {
            this.ratingStars = ratingStars;
            this.comment     = comment;
            this.tagIds      = tagIds != null ? tagIds : Collections.emptyList();
        }

        public int getRatingStars()    { return ratingStars; }
        public String getComment()     { return comment; }
        public List<String> getTagIds(){ return tagIds; }
    }

    public static class ReportSnapshot {
        private final String reason;
        private final String evidenceUrl;

        public ReportSnapshot(String reason, String evidenceUrl) {
            this.reason      = reason;
            this.evidenceUrl = evidenceUrl;
        }

        public String getReason()      { return reason; }
        public String getEvidenceUrl() { return evidenceUrl; }
    }
}
