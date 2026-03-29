package com.walkmate.ui.matches;

import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walksession.WalkSession;

import java.util.Collections;
import java.util.List;

public class MatchesUiState {

    private final boolean isLoading;
    private final List<WalkIntent> activeIntents;    // Finding sub-tab
    private final List<WalkProposal> proposals;      // Proposal sub-tab
    private final List<WalkSession> activeSessions;  // Session sub-tab
    private final String error;

    public MatchesUiState(boolean isLoading,
                          List<WalkIntent> activeIntents,
                          List<WalkProposal> proposals,
                          List<WalkSession> activeSessions,
                          String error) {
        this.isLoading = isLoading;
        this.activeIntents = activeIntents;
        this.proposals = proposals;
        this.activeSessions = activeSessions;
        this.error = error;
    }

    public static MatchesUiState initial() {
        return new MatchesUiState(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null);
    }

    public boolean isLoading() { return isLoading; }
    public List<WalkIntent> getActiveIntents() { return activeIntents; }
    public List<WalkProposal> getProposals() { return proposals; }
    public List<WalkSession> getActiveSessions() { return activeSessions; }
    public String getError() { return error; }
}
