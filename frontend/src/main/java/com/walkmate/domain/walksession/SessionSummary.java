package com.walkmate.domain.walksession;

import java.util.Collections;
import java.util.List;

public class SessionSummary {

    private final String sessionId;
    private final WalkSession.Status status;
    private final String scheduledStart;
    private final boolean isReviewed;
    private final long terminalAtMs;
    private final List<ParticipantSummary> participants;

    public SessionSummary(String sessionId, WalkSession.Status status,
                          String scheduledStart, boolean isReviewed,
                          long terminalAtMs, List<ParticipantSummary> participants) {
        this.sessionId    = sessionId;
        this.status       = status;
        this.scheduledStart = scheduledStart;
        this.isReviewed   = isReviewed;
        this.terminalAtMs = terminalAtMs;
        this.participants = participants != null ? participants : Collections.emptyList();
    }

    public String getSessionId()                    { return sessionId; }
    public WalkSession.Status getStatus()           { return status; }
    public String getScheduledStart()               { return scheduledStart; }
    public boolean isReviewed()                     { return isReviewed; }
    public long getTerminalAtMs()                   { return terminalAtMs; }
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
     * Returns the participant ID that is NOT the current user.
     * Used by the adapter to pass the partner ID to the report/profile flows.
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
