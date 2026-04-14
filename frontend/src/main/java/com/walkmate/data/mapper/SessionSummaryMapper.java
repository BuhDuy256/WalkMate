package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link WalkSessionResponse} → {@link SessionSummary}.
 *
 * <p>Used for UC-22 history list. Only the 7 summary fields are mapped;
 * totalDistanceKm and durationMinutes are not available in WalkSessionResponse —
 * they come from SessionRouteResponse (fetched separately for UC-23).</p>
 */
public class SessionSummaryMapper {

    public static SessionSummary toDomain(WalkSessionResponse response, String callerId) {
        String partnerId = callerId.equals(response.getUserIdA())
                ? response.getUserIdB()
                : response.getUserIdA();

        long terminalAtMs = 0L;
        if (response.getEndedAt() != null) {
            try { terminalAtMs = Instant.parse(response.getEndedAt()).toEpochMilli(); }
            catch (Exception ignored) {}
        }

        return new SessionSummary(
                response.getSessionId(),
                toStatus(response.getStatus()),
                partnerId,
                response.getScheduledStart(),
                0.0,   // totalDistanceKm — not in WalkSessionResponse; use SessionRouteResponse
                0,     // durationMinutes — not in WalkSessionResponse; use SessionRouteResponse
                response.isReviewed(),
                terminalAtMs
        );
    }

    public static List<SessionSummary> toDomainList(List<WalkSessionResponse> responses, String callerId) {
        List<SessionSummary> result = new ArrayList<>(responses.size());
        for (WalkSessionResponse r : responses) {
            result.add(toDomain(r, callerId));
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
