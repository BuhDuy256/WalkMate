package com.walkmate.presentation.mapper.proposal;

import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.presentation.dto.response.proposal.WalkProposalResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProposalMapper {

    public WalkProposalResponse toResponse(MatchProposal proposal, String callerId,
                                           String sessionId, String matchedUserName,
                                           String matchedUserAvatarUrl,
                                           int matchedUserAge, int matchedUserTrustScore,
                                           List<String> overlappingTags, boolean isPrivate) {
        boolean callerIsA = callerId.equals(proposal.getUserIdA());

        String callersIntentId    = callerIsA ? proposal.getIntentIdA() : proposal.getIntentIdB();
        String matchedIntentId    = callerIsA ? proposal.getIntentIdB() : proposal.getIntentIdA();
        String callersUserId      = callerIsA ? proposal.getUserIdA()   : proposal.getUserIdB();
        String matchedUserId      = callerIsA ? proposal.getUserIdB()   : proposal.getUserIdA();
        boolean callerHasAccepted = callerIsA ? proposal.isAcceptedByA() : proposal.isAcceptedByB();
        String myAcceptanceStatus = callerHasAccepted ? "ACCEPTED" : "PENDING";

        return new WalkProposalResponse(
                proposal.getProposalId(),
                callersIntentId,
                matchedIntentId,
                callersUserId,
                matchedUserId,
                matchedUserName,
                matchedUserAvatarUrl,
                matchedUserAge,
                matchedUserTrustScore,
                overlappingTags,
                proposal.getProposedStartTime().toString(),
                proposal.getProposedEndTime().toString(),
                proposal.getHotspotId(),
                proposal.getHotspotName(),
                proposal.getStatus().name(),
                proposal.getExpiresAt().toString(),
                sessionId,
                myAcceptanceStatus,
                isPrivate
        );
    }
}
