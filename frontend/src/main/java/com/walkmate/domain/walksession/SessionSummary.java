package com.walkmate.domain.walksession;

import java.util.Collections;
import java.util.List;

public class SessionSummary {

    private final String sessionId;
    private final WalkSession.Status status;
    private final String scheduledStart;
    private final boolean isReviewed;
    private final long terminalAtMs;
    private final double meetingPointLat;
    private final double meetingPointLng;
    private final String hotspotName;
    private final List<ParticipantSummary> participants;

    public SessionSummary(String sessionId, WalkSession.Status status,
                          String scheduledStart, boolean isReviewed,
                          long terminalAtMs, double meetingPointLat, double meetingPointLng,
                          String hotspotName, List<ParticipantSummary> participants) {
        this.sessionId       = sessionId;
        this.status          = status;
        this.scheduledStart  = scheduledStart;
        this.isReviewed      = isReviewed;
        this.terminalAtMs    = terminalAtMs;
        this.meetingPointLat = meetingPointLat;
        this.meetingPointLng = meetingPointLng;
        this.hotspotName     = hotspotName;
        this.participants    = participants != null ? participants : Collections.emptyList();
    }

    public String getSessionId()                    { return sessionId; }
    public WalkSession.Status getStatus()           { return status; }
    public String getScheduledStart()               { return scheduledStart; }
    public boolean isReviewed()                     { return isReviewed; }
    public long getTerminalAtMs()                   { return terminalAtMs; }
    public double getMeetingPointLat()              { return meetingPointLat; }
    public double getMeetingPointLng()              { return meetingPointLng; }
    public String getHotspotName()                  { return hotspotName; }
    public List<ParticipantSummary> getParticipants() { return participants; }

    /**
     * Returns the participant entry for the current user, or null if not found.
     */
    public ParticipantSummary getCallerParticipant(String currentUserId) {
        for (ParticipantSummary p : participants) {
            if (p.getParticipantId().equals(currentUserId)) return p;
        }
        return null;
    }

    /**
     * Returns the partner's participant entry (the one that is NOT the current user).
     */
    public ParticipantSummary getPartnerParticipant(String currentUserId) {
        for (ParticipantSummary p : participants) {
            if (!p.getParticipantId().equals(currentUserId)) return p;
        }
        return null;
    }

    /**
     * Returns the participant ID that is NOT the current user.
     */
    public String getPartnerId(String currentUserId) {
        for (ParticipantSummary p : participants) {
            if (!p.getParticipantId().equals(currentUserId)) {
                return p.getParticipantId();
            }
        }
        return null;
    }
}
