package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;
import com.walkmate.domain.walksession.WalkSession;

import java.util.ArrayList;
import java.util.List;

public class WalkSessionMapper {

    /**
     * Maps a {@link WalkSessionResponse} DTO to a {@link WalkSession} domain object.
     *
     * <p>The API returns both user IDs but the domain model represents the session
     * from the perspective of the viewing user as "partnerName / partnerAvatar".
     * Those enrichment fields (name, avatar) are not yet provided by the session
     * endpoint and use safe defaults until a profile endpoint is available.</p>
     *
     * @param response   raw DTO from the API
     * @param callerId   the authenticated user's ID, used to determine which side is the partner
     */
    public static WalkSession toDomain(WalkSessionResponse response, String callerId) {
        boolean isCallerUserA = callerId.equals(response.getUserIdA());
        String partnerId = isCallerUserA ? response.getUserIdB() : response.getUserIdA();

        return new WalkSession(
                response.getSessionId(),
                response.getProposalId(),
                partnerId,
                partnerId,              // fallback: use partnerId until the API returns a name
                null,                   // partnerAvatar not yet in API
                response.getMeetingPointLat(),
                response.getMeetingPointLng(),
                response.getScheduledStart(),
                toStatus(response.getStatus()),
                response.getScheduledEnd(),
                response.getStartedAt(),
                response.getEndedAt(),
                response.getUserAActivatedAt(),
                response.getUserBActivatedAt(),
                response.isReviewed(),
                isCallerUserA
        );
    }

    public static List<WalkSession> toDomainList(List<WalkSessionResponse> responses, String callerId) {
        List<WalkSession> result = new ArrayList<>(responses.size());
        for (WalkSessionResponse r : responses) {
            result.add(toDomain(r, callerId));
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private WalkSessionMapper() {}
}
