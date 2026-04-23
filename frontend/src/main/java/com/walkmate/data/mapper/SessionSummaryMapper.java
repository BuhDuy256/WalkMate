package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.session.SessionSummaryResponse;
import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionSummaryMapper {

    public static SessionSummary toDomain(SessionSummaryResponse response) {
        long terminalAtMs = 0L;
        if (response.getEndedAt() != null) {
            try { terminalAtMs = Instant.parse(response.getEndedAt()).toEpochMilli(); }
            catch (Exception ignored) {}
        }

        List<ParticipantSummary> participants = Collections.emptyList();
        if (response.getParticipants() != null) {
            participants = new ArrayList<>(response.getParticipants().size());
            for (SessionSummaryResponse.ParticipantResponse p : response.getParticipants()) {
                participants.add(new ParticipantSummary(
                        p.getParticipantId(),
                        p.getFullName(),
                        p.getDistanceKm(),
                        p.getDurationMinutes()
                ));
            }
        }

        return new SessionSummary(
                response.getSessionId(),
                toStatus(response.getStatus()),
                response.getScheduledStart(),
                response.isReviewed(),
                terminalAtMs,
                participants
        );
    }

    public static List<SessionSummary> toDomainList(List<SessionSummaryResponse> responses) {
        List<SessionSummary> result = new ArrayList<>(responses.size());
        for (SessionSummaryResponse r : responses) {
            result.add(toDomain(r));
        }
        return result;
    }

    private static WalkSession.Status toStatus(String raw) {
        if (raw == null) return WalkSession.Status.PENDING;
        switch (raw) {
            case "ACTIVE":    return WalkSession.Status.ACTIVE;
            case "COMPLETED": return WalkSession.Status.COMPLETED;
            case "CANCELLED": return WalkSession.Status.CANCELLED;
            case "NO_SHOW":   return WalkSession.Status.NO_SHOW;
            case "ABORTED":   return WalkSession.Status.ABORTED;
            default:          return WalkSession.Status.PENDING;
        }
    }

    private SessionSummaryMapper() {}
}
