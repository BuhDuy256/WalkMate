package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.proposal.WalkProposalResponse;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walksession.WalkSession;

import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class WalkProposalMapper {

    /**
     * Maps a WalkProposalResponse DTO to a WalkProposal domain object.
     * Fields not provided by the API (matchedUserName, trustScore, tags) use safe defaults
     * until a user-profile enrichment endpoint is added.
     */
    public static WalkProposal toDomain(WalkProposalResponse response) {
        return new WalkProposal(
                response.getProposalId(),
                response.getCallersIntentId(),
                response.getMatchedUserId(),
                response.getMatchedUserName(),
                0,                              // age not yet in API
                0,                              // trustScore not yet in API
                Collections.emptyList(),        // tags not yet in API
                toHourFloat(response.getProposedTimeStart()),
                toHourFloat(response.getProposedTimeEnd()),
                toStatus(response.getStatus()),
                response.getExpiresAt(),
                response.getProposedLat(),
                response.getProposedLng(),
                response.getMyAcceptanceStatus(),
                response.getSessionId(),
                response.isPrivateInvite()
        );
    }

    public static List<WalkProposal> toDomainList(List<WalkProposalResponse> responses) {
        List<WalkProposal> result = new java.util.ArrayList<>(responses.size());
        for (WalkProposalResponse r : responses) {
            result.add(toDomain(r));
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static float toHourFloat(String instantString) {
        if (instantString == null || instantString.trim().isEmpty()) return 0f;
        try {
            LocalTime localTime = Instant.parse(instantString)
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toLocalTime();
            return localTime.getHour() + (localTime.getMinute() / 60f);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private static WalkProposal.Status toStatus(String raw) {
        if (raw == null) return WalkProposal.Status.PENDING;
        switch (raw) {
            case "CONFIRMED": return WalkProposal.Status.CONFIRMED;
            case "REJECTED":  return WalkProposal.Status.REJECTED;
            default:          return WalkProposal.Status.PENDING;
        }
    }

    private WalkProposalMapper() {}
}
