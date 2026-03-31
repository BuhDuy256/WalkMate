package com.walkmate.domain.session;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class WalkSession {

    private String sessionId;
    private String proposalId;
    private String userIdA;
    private String userIdB;
    private double meetingPointLat;
    private double meetingPointLng;
    private Instant scheduledStart;
    private Instant scheduledEnd;
    private SessionStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;

    protected WalkSession() {
    }

    /** Rehydration constructor — called by the repository when loading from DB. */
    public WalkSession(String sessionId, String proposalId,
                       String userIdA, String userIdB,
                       double meetingPointLat, double meetingPointLng,
                       Instant scheduledStart, Instant scheduledEnd,
                       SessionStatus status,
                       Instant createdAt, Instant startedAt, Instant endedAt) {
        this.sessionId = sessionId;
        this.proposalId = proposalId;
        this.userIdA = userIdA;
        this.userIdB = userIdB;
        this.meetingPointLat = meetingPointLat;
        this.meetingPointLng = meetingPointLng;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    private WalkSession(String proposalId, String userIdA, String userIdB,
                        double meetingPointLat, double meetingPointLng,
                        Instant scheduledStart, Instant scheduledEnd) {
        this.sessionId = UUID.randomUUID().toString();
        this.proposalId = proposalId;
        this.userIdA = userIdA;
        this.userIdB = userIdB;
        this.meetingPointLat = meetingPointLat;
        this.meetingPointLng = meetingPointLng;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.status = SessionStatus.PENDING;
        this.createdAt = Instant.now();
        this.startedAt = null;
        this.endedAt = null;
    }

    public static WalkSession create(String proposalId, String userIdA, String userIdB,
                                     double meetingPointLat, double meetingPointLng,
                                     Instant scheduledStart, Instant scheduledEnd) {
        return new WalkSession(proposalId, userIdA, userIdB,
                meetingPointLat, meetingPointLng, scheduledStart, scheduledEnd);
    }
}
