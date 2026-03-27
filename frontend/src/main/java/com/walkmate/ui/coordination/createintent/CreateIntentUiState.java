package com.walkmate.ui.coordination.createintent;

import com.walkmate.domain.walkintent.WalkIntent;

public class CreateIntentUiState {

    private final boolean isLoading;
    private final String error;
    private final WalkIntent submittedIntent; // non-null = creation succeeded

    public CreateIntentUiState(boolean isLoading, String error, WalkIntent submittedIntent) {
        this.isLoading = isLoading;
        this.error = error;
        this.submittedIntent = submittedIntent;
    }

    public static CreateIntentUiState initial() {
        return new CreateIntentUiState(false, null, null);
    }

    public boolean isLoading() { return isLoading; }
    public String getError() { return error; }
    public WalkIntent getSubmittedIntent() { return submittedIntent; }
}
