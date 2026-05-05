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
 *
 * {@code reviewSnapshot} is non-null in the {@link Kind#ALREADY_REVIEWED} state and
 * contains the stars, comment, and tag IDs from the previously submitted review.
 */
public class ReviewUiState {

    public enum Kind { IDLE, LOADING, SUCCESS, ALREADY_REVIEWED, ERROR }

    public final Kind             kind;
    public final String           error;          // non-null when ERROR
    public final List<ReviewTag>  availableTags;  // empty until tags API responds
    public final boolean          tagsLoading;
    public final ReviewSnapshot   reviewSnapshot; // non-null when ALREADY_REVIEWED

    private ReviewUiState(Kind kind, String error,
                          List<ReviewTag> availableTags, boolean tagsLoading,
                          ReviewSnapshot reviewSnapshot) {
        this.kind           = kind;
        this.error          = error;
        this.availableTags  = availableTags != null ? availableTags : Collections.emptyList();
        this.tagsLoading    = tagsLoading;
        this.reviewSnapshot = reviewSnapshot;
    }

    // ── Factories ────────────────────────────────────────────────────────────

    public static ReviewUiState idle() {
        return new ReviewUiState(Kind.IDLE, null, Collections.emptyList(), true, null);
    }

    public static ReviewUiState loading() {
        return new ReviewUiState(Kind.LOADING, null, Collections.emptyList(), true, null);
    }

    public static ReviewUiState alreadyReviewed() {
        return new ReviewUiState(Kind.ALREADY_REVIEWED, null, Collections.emptyList(), false, null);
    }

    public static ReviewUiState error(String msg) {
        return new ReviewUiState(Kind.ERROR, msg, Collections.emptyList(), false, null);
    }

    /** Returns a copy with the tag vocabulary populated, preserving all other fields. */
    public ReviewUiState withTags(List<ReviewTag> tags) {
        return new ReviewUiState(kind, error, tags, false, reviewSnapshot);
    }

    /** Returns a copy with a new kind, preserving the tag vocabulary. */
    public ReviewUiState withKind(Kind newKind) {
        return new ReviewUiState(newKind, null, availableTags, tagsLoading, reviewSnapshot);
    }

    /** Returns a copy transitioned to ALREADY_REVIEWED, preserving tags and attaching the snapshot. */
    public ReviewUiState withAlreadyReviewed(ReviewSnapshot snap) {
        return new ReviewUiState(Kind.ALREADY_REVIEWED, null, availableTags, tagsLoading, snap);
    }

    // ── Snapshot type ─────────────────────────────────────────────────────────

    public static class ReviewSnapshot {
        public final int ratingStars;
        public final String comment;
        public final List<String> tagIds;

        public ReviewSnapshot(int ratingStars, String comment, List<String> tagIds) {
            this.ratingStars = ratingStars;
            this.comment     = comment;
            this.tagIds      = tagIds != null ? tagIds : Collections.emptyList();
        }
    }
}
