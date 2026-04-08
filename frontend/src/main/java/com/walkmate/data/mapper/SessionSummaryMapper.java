package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;
import com.walkmate.domain.walksession.SessionSummary; // TODO Phase 4: create this domain model

import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link WalkSessionResponse} → {@link SessionSummary} (domain model created in Phase 4).
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

        return new SessionSummary(
                response.getSessionId(),
                response.getStatus(),
                partnerId,
                response.getScheduledStart(),
                0.0,   // totalDistanceKm — not in WalkSessionResponse; use SessionRouteResponse
                0,     // durationMinutes — not in WalkSessionResponse; use SessionRouteResponse
                response.isReviewed()
        );
    }

    public static List<SessionSummary> toDomainList(List<WalkSessionResponse> responses, String callerId) {
        List<SessionSummary> result = new ArrayList<>(responses.size());
        for (WalkSessionResponse r : responses) {
            result.add(toDomain(r, callerId));
        }
        return result;
    }

    private SessionSummaryMapper() {}
}
