package com.walkmate.domain.walksession;

public class ParticipantSummary {

    private final String            participantId;
    private final String            fullName;
    private final double            distanceKm;
    private final int               durationMinutes;
    private final WalkSession.Status userStatus;

    public ParticipantSummary(String participantId, String fullName,
                               double distanceKm, int durationMinutes,
                               WalkSession.Status userStatus) {
        this.participantId   = participantId;
        this.fullName        = fullName;
        this.distanceKm      = distanceKm;
        this.durationMinutes = durationMinutes;
        this.userStatus      = userStatus;
    }

    public String             getParticipantId()   { return participantId; }
    public String             getFullName()        { return fullName; }
    public double             getDistanceKm()      { return distanceKm; }
    public int                getDurationMinutes() { return durationMinutes; }
    public WalkSession.Status getUserStatus()      { return userStatus; }
}
