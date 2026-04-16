package com.walkmate.application.walkintent;

import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.walkintent.WalkIntent;

/**
 * Value record returned by WalkIntentCommandService.createIntent().
 * proposal is null when no inline match was found (Case A2).
 */
public record CreateIntentResult(WalkIntent intent, MatchProposal proposal) {
}
