package com.walkmate.ui.rating;


public class RatingUiState {
    private final boolean loading;
    private final RatingViewData data;
    private final String error;
    private final boolean submitEnabled;

    public RatingUiState(boolean loading, RatingViewData data, String error, boolean submitEnabled) {
        this.loading = loading;
        this.data = data;
        this.error = error;
        this.submitEnabled = submitEnabled;
    }

    public static RatingUiState initial(RatingViewData data) {
        return new RatingUiState(false, data, null, false);
    }

    public RatingUiState withLoading() {
        return new RatingUiState(true, data, null, false);
    }

    public RatingUiState withSuccess() {
        return new RatingUiState(false, data, null, false);
    }

    public RatingUiState withError(String error) {
        return new RatingUiState(false, data, error, submitEnabled);
    }

    public RatingUiState withData(RatingViewData newData, boolean canSubmit) {
        return new RatingUiState(loading, newData, error, canSubmit);
    }

    public boolean isLoading() {
        return loading;
    }

    public RatingViewData getData() {
        return data;
    }

    public String getError() {
        return error;
    }

    public boolean isSubmitEnabled() {
        return submitEnabled;
    }
}
