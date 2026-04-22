package com.walkmate.presentation.mapper.proposal;

import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.ProposalStatus;
import com.walkmate.presentation.dto.response.proposal.WalkProposalResponse;
import org.springframework.stereotype.Component;

@Component
public class ProposalMapper {

    /**
     * Maps a MatchProposal to a WalkProposalResponse from the perspective of the caller.
     * The caller's intent/user fields go to "callers_*" and the partner's to "matched_*".
     *
     * @param proposal         the domain proposal
     * @param callerId         the authenticated user's ID (determines which side is "callers")
     * @param sessionId        the sessionId if the proposal is CONFIRMED, else null
     * @param matchedUserName  the resolved display name of the matched partner
     */
    public WalkProposalResponse toResponse(MatchProposal proposal, String callerId,
                                           String sessionId, String matchedUserName) {
        boolean callerIsA = callerId.equals(proposal.getUserIdA());

        String callersIntentId   = callerIsA ? proposal.getIntentIdA() : proposal.getIntentIdB();
        String matchedIntentId   = callerIsA ? proposal.getIntentIdB() : proposal.getIntentIdA();
        String callersUserId     = callerIsA ? proposal.getUserIdA()   : proposal.getUserIdB();
        String matchedUserId     = callerIsA ? proposal.getUserIdB()   : proposal.getUserIdA();
        boolean callerHasAccepted = callerIsA ? proposal.isAcceptedByA() : proposal.isAcceptedByB();
        String myAcceptanceStatus = callerHasAccepted ? "ACCEPTED" : "PENDING";

        return new WalkProposalResponse(
                proposal.getProposalId(),
                callersIntentId,
                matchedIntentId,
                callersUserId,
                matchedUserId,
                matchedUserName,
                proposal.getProposedStartTime().toString(),
                proposal.getProposedEndTime().toString(),
                proposal.getProposedLocationLat(),
                proposal.getProposedLocationLng(),
                proposal.getStatus().name(),
                proposal.getExpiresAt().toString(),
                sessionId,
                myAcceptanceStatus
        );
    }
}
