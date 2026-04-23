package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.session.WalkSessionResponse;
import com.walkmate.domain.walksession.WalkSession;

import java.util.ArrayList;
import java.util.List;

public class WalkSessionMapper {

    /**
     * Maps a {@link WalkSessionResponse} DTO to a {@link WalkSession} domain object.
     *
     * @param response raw DTO from the API
     * @param callerId the authenticated user's ID, used to determine caller vs partner side
     */
    public static WalkSession toDomain(WalkSessionResponse response, String callerId) {
        boolean isCallerUserA = callerId.equals(response.getUserIdA());
        String partnerId = isCallerUserA ? response.getUserIdB() : response.getUserIdA();

        String partnerName = response.getPartnerName();

        return new WalkSession(
                response.getSessionId(),
                response.getProposalId(),
                partnerId,
                partnerName,            // null when API has not yet returned a name
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
                toStatus(response.getUserAStatus()),
                toStatus(response.getUserBStatus()),
                response.getUserAEndedAt(),
                response.getUserBEndedAt(),
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
            default:          return WalkSession.Status.PENDING;
        }
    }

    private WalkSessionMapper() {}
}
