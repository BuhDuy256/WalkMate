package com.walkmate.domain.walksession;

public class ParticipantSummary {

    private final String participantId;
    private final String fullName;
    private final double distanceKm;
    private final int    durationMinutes;

    public ParticipantSummary(String participantId, String fullName,
                               double distanceKm, int durationMinutes) {
        this.participantId   = participantId;
        this.fullName        = fullName;
        this.distanceKm      = distanceKm;
        this.durationMinutes = durationMinutes;
    }

    public String getParticipantId()   { return participantId; }
    public String getFullName()        { return fullName; }
    public double getDistanceKm()      { return distanceKm; }
    public int    getDurationMinutes() { return durationMinutes; }
}
