package com.walkmate.ui.explore.createintent;

import com.walkmate.domain.walkintent.WalkIntent;

/**
 * Immutable state for the embedded Create Intent form inside ExploreFragment.
 */
public class CreateIntentUiState {

    private final boolean isLoading;
    private final String error;
    private final WalkIntent submittedIntent; // non-null signals successful creation

    public CreateIntentUiState(boolean isLoading, String error, WalkIntent submittedIntent) {
        this.isLoading       = isLoading;
        this.error           = error;
        this.submittedIntent = submittedIntent;
    }

    public static CreateIntentUiState initial() {
        return new CreateIntentUiState(false, null, null);
    }

    public boolean isLoading()               { return isLoading; }
    public String getError()                 { return error; }
    public WalkIntent getSubmittedIntent()   { return submittedIntent; }
}
