package com.walkmate.ui.review;

import com.walkmate.domain.review.ReviewTag;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the Submit Review screen state.
 *
 * In addition to the submit lifecycle ({@link Kind}), carries the tag vocabulary
 * loaded from {@code GET /api/v1/reviews/tags} so the Fragment can render the
 * structured-feedback chip group without coupling to the repository layer.
 */
public class ReviewUiState {

    public enum Kind { IDLE, LOADING, SUCCESS, ALREADY_REVIEWED, ERROR }

    public final Kind             kind;
    public final String           error;        // non-null when ERROR
    public final List<ReviewTag>  availableTags; // empty until tags API responds
    public final boolean          tagsLoading;

    private ReviewUiState(Kind kind, String error,
                          List<ReviewTag> availableTags, boolean tagsLoading) {
        this.kind          = kind;
        this.error         = error;
        this.availableTags = availableTags != null ? availableTags : Collections.emptyList();
        this.tagsLoading   = tagsLoading;
    }

    // ── Factories (preserve tag state across kind transitions) ────────────────

    public static ReviewUiState idle() {
        return new ReviewUiState(Kind.IDLE, null, Collections.emptyList(), true);
    }

    public static ReviewUiState loading() {
        return new ReviewUiState(Kind.LOADING, null, Collections.emptyList(), true);
    }

    public static ReviewUiState alreadyReviewed() {
        return new ReviewUiState(Kind.ALREADY_REVIEWED, null, Collections.emptyList(), false);
    }

    public static ReviewUiState error(String msg) {
        return new ReviewUiState(Kind.ERROR, msg, Collections.emptyList(), false);
    }

    /** Returns a copy of this state with the tag vocabulary populated. */
    public ReviewUiState withTags(List<ReviewTag> tags) {
        return new ReviewUiState(kind, error, tags, false);
    }

    /** Returns a copy of this state with a new kind, preserving the tag vocabulary. */
    public ReviewUiState withKind(Kind newKind) {
        return new ReviewUiState(newKind, null, availableTags, tagsLoading);
    }
}
