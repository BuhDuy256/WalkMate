package com.walkmate.ui.review;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.review.ReviewTag;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.Collections;
import java.util.List;

/**
 * ViewModel for the post-session review screen.
 *
 * <p>Exposes three LiveData streams that a {@code ReviewFragment} observes:</p>
 * <ul>
 *   <li>{@link #submitState} — idle / loading / success / error for the submit action.</li>
 *   <li>{@link #reviews}    — the list of reviews for a user's profile page.</li>
 *   <li>{@link #error}      — human-readable error message for transient display.</li>
 * </ul>
 */
public class ReviewViewModel extends ViewModel {

    // ── UI state enum ─────────────────────────────────────────────────────────

    public enum SubmitState { IDLE, LOADING, SUCCESS, ERROR }

    // ── LiveData ──────────────────────────────────────────────────────────────

    private final MutableLiveData<SubmitState>   submitState = new MutableLiveData<>(SubmitState.IDLE);
    private final MutableLiveData<WalkReview>    submittedReview = new MutableLiveData<>();
    private final MutableLiveData<List<WalkReview>> reviews  = new MutableLiveData<>();
    private final MutableLiveData<String>        error       = new MutableLiveData<>();

    private final MutableLiveData<ReviewUiState> reviewUiState =
            new MutableLiveData<>(ReviewUiState.idle());

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ReviewRepository      reviewRepository;
    private final WalkSessionRepository sessionRepository;

    public ReviewViewModel(ReviewRepository reviewRepository,
                           WalkSessionRepository sessionRepository) {
        this.reviewRepository  = reviewRepository;
        this.sessionRepository = sessionRepository;
        loadReviewTags();
    }

    private void loadReviewTags() {
        reviewRepository.getReviewTags(new DomainCallback<List<ReviewTag>>() {
            @Override
            public void onSuccess(List<ReviewTag> tags) {
                ReviewUiState current = reviewUiState.getValue();
                ReviewUiState updated = (current != null ? current : ReviewUiState.idle())
                        .withTags(tags);
                reviewUiState.postValue(updated);
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal: chips stay hidden; the user can still submit with stars only.
                ReviewUiState current = reviewUiState.getValue();
                ReviewUiState updated = (current != null ? current : ReviewUiState.idle())
                        .withTags(Collections.emptyList());
                reviewUiState.postValue(updated);
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submits a review for the given session. Posts to {@link #submitState} and
     * {@link #submittedReview} on completion.
     *
     * Called from {@code ReviewFragment} when the user taps "Submit".
     */
    public void submitReview(String sessionId, int ratingStars, String comment,
                             List<String> tagIds) {
        submitState.setValue(SubmitState.LOADING);

        reviewRepository.submitReview(sessionId, ratingStars, comment, tagIds,
                new DomainCallback<WalkReview>() {
            @Override
            public void onSuccess(WalkReview review) {
                submittedReview.postValue(review);
                submitState.postValue(SubmitState.SUCCESS);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getMessage());
                submitState.postValue(SubmitState.ERROR);
            }
        });
    }

    /**
     * Loads all reviews for a user's profile page.
     * Posts results to {@link #reviews}.
     */
    public void loadReviewsForUser(String userId) {
        reviewRepository.getReviewsForUser(userId, new DomainCallback<List<WalkReview>>() {
            @Override
            public void onSuccess(List<WalkReview> result) {
                reviews.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }

    /**
     * Checks whether the session has already been reviewed.
     * Loads session history, finds the matching entry, and posts
     * {@link ReviewUiState#alreadyReviewed()} if reviewed, or
     * {@link ReviewUiState#idle()} if not.
     */
    public void loadReviewState(String sessionId) {
        ReviewUiState snapshot = reviewUiState.getValue();
        ReviewUiState base = snapshot != null ? snapshot : ReviewUiState.idle();
        reviewUiState.postValue(base.withKind(ReviewUiState.Kind.LOADING));

        sessionRepository.getSessionHistory(new DomainCallback<List<SessionSummary>>() {
            @Override
            public void onSuccess(List<SessionSummary> sessions) {
                boolean alreadyReviewed = false;
                if (sessions != null) {
                    for (SessionSummary s : sessions) {
                        if (sessionId.equals(s.getSessionId())) {
                            alreadyReviewed = s.isReviewed();
                            break;
                        }
                    }
                }
                ReviewUiState cur = reviewUiState.getValue();
                ReviewUiState next = cur != null ? cur : ReviewUiState.idle();
                reviewUiState.postValue(next.withKind(
                        alreadyReviewed ? ReviewUiState.Kind.ALREADY_REVIEWED : ReviewUiState.Kind.IDLE));
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal: default to allowing a review attempt.
                ReviewUiState cur = reviewUiState.getValue();
                reviewUiState.postValue(
                        (cur != null ? cur : ReviewUiState.idle()).withKind(ReviewUiState.Kind.IDLE));
            }
        });
    }

    // ── LiveData getters ──────────────────────────────────────────────────────

    public LiveData<SubmitState>      getSubmitState()    { return submitState; }
    public LiveData<WalkReview>       getSubmittedReview(){ return submittedReview; }
    public LiveData<List<WalkReview>> getReviews()        { return reviews; }
    public LiveData<String>           getError()          { return error; }
    public LiveData<ReviewUiState>    getReviewUiState()  { return reviewUiState; }
}
