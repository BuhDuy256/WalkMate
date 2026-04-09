package com.walkmate.ui.review;

/**
 * Immutable snapshot of the Submit Review screen state.
 */
public class ReviewUiState {

    public enum Kind { IDLE, LOADING, SUCCESS, ALREADY_REVIEWED, ERROR }

    public final Kind   kind;
    public final String error; // non-null when ERROR

    private ReviewUiState(Kind kind, String error) {
        this.kind  = kind;
        this.error = error;
    }

    public static ReviewUiState idle()            { return new ReviewUiState(Kind.IDLE,             null); }
    public static ReviewUiState loading()          { return new ReviewUiState(Kind.LOADING,          null); }
    public static ReviewUiState success()          { return new ReviewUiState(Kind.SUCCESS,          null); }
    public static ReviewUiState alreadyReviewed()  { return new ReviewUiState(Kind.ALREADY_REVIEWED, null); }
    public static ReviewUiState error(String msg)  { return new ReviewUiState(Kind.ERROR,            msg); }
}
