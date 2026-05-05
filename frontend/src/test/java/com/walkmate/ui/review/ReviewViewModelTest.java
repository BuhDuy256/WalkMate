package com.walkmate.ui.review;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.review.ReviewTag;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

public class ReviewViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private ReviewRepository reviewRepo;

    @Mock
    private WalkSessionRepository sessionRepo;

    private ReviewViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void loadReviewTags_success_populatesTags() {
        doAnswer(invocation -> {
            DomainCallback<List<ReviewTag>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(new ReviewTag("t1", "Friendly", "POSITIVE_BEHAVIOR")));
            return null;
        }).when(reviewRepo).getReviewTags(any(DomainCallback.class));

        viewModel = new ReviewViewModel(reviewRepo, sessionRepo);

        ReviewUiState state = viewModel.getReviewUiState().getValue();
        assertFalse(state.availableTags.isEmpty());
        assertEquals(1, state.availableTags.size());
        assertEquals("t1", state.availableTags.get(0).getTagId());
    }

    @Test
    public void loadReviewTags_error_leavesEmptyTags() {
        doAnswer(invocation -> {
            DomainCallback<List<ReviewTag>> cb = invocation.getArgument(0);
            cb.onError(new Exception("tags unavailable"));
            return null;
        }).when(reviewRepo).getReviewTags(any(DomainCallback.class));

        viewModel = new ReviewViewModel(reviewRepo, sessionRepo);

        ReviewUiState state = viewModel.getReviewUiState().getValue();
        assertTrue(state.availableTags.isEmpty());
    }

    @Test
    public void submitReview_success_postsSubmittedReviewAndSuccessState() {
        viewModel = new ReviewViewModel(reviewRepo, sessionRepo);

        doAnswer(invocation -> {
            DomainCallback<WalkReview> cb = invocation.getArgument(4);
            WalkReview r = new WalkReview("rid","sid","from","to",5,"Nice","2026-05-05T12:00:00Z");
            cb.onSuccess(r);
            return null;
        }).when(reviewRepo).submitReview(anyString(), anyInt(), anyString(), anyList(), any(DomainCallback.class));

        viewModel.submitReview("sid", 5, "Nice", Collections.emptyList());

        assertEquals(ReviewViewModel.SubmitState.SUCCESS, viewModel.getSubmitState().getValue());
        WalkReview posted = viewModel.getSubmittedReview().getValue();
        assertEquals(5, posted.getRatingStars());
        assertEquals("Nice", posted.getComment());
    }

    @Test
    public void submitReview_error_postsErrorAndErrorState() {
        viewModel = new ReviewViewModel(reviewRepo, sessionRepo);

        doAnswer(invocation -> {
            DomainCallback<WalkReview> cb = invocation.getArgument(4);
            cb.onError(new Exception("submit failed"));
            return null;
        }).when(reviewRepo).submitReview(anyString(), anyInt(), anyString(), anyList(), any(DomainCallback.class));

        viewModel.submitReview("sid", 3, null, Collections.emptyList());

        assertEquals(ReviewViewModel.SubmitState.ERROR, viewModel.getSubmitState().getValue());
        assertEquals("submit failed", viewModel.getError().getValue());
    }

    @Test
    public void loadReviewState_reviewed_postsAlreadyReviewedSnapshot() {
        // ensure tags loader doesn't interfere
        doAnswer(invocation -> {
            DomainCallback<List<ReviewTag>> cb = invocation.getArgument(0);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(reviewRepo).getReviewTags(any(DomainCallback.class));

        viewModel = new ReviewViewModel(reviewRepo, sessionRepo);

        SessionSummary.ReviewSnapshot snap = new SessionSummary.ReviewSnapshot(4, "ok", List.of("t1"));
        SessionSummary session = new SessionSummary("sid", WalkSession.Status.COMPLETED,
                null, true, false, snap, null, 0L, 0.0, 0.0, null, Collections.emptyList());

        doAnswer(invocation -> {
            DomainCallback<SessionSummary> cb = invocation.getArgument(1);
            cb.onSuccess(session);
            return null;
        }).when(sessionRepo).getSessionSummary(anyString(), any(DomainCallback.class));

        viewModel.loadReviewState("sid");

        ReviewUiState state = viewModel.getReviewUiState().getValue();
        assertEquals(ReviewUiState.Kind.ALREADY_REVIEWED, state.kind);
        assertEquals(4, state.reviewSnapshot.ratingStars);
        assertEquals("ok", state.reviewSnapshot.comment);
    }

    @Test
    public void loadReviewState_notReviewed_postsIdle() {
        doAnswer(invocation -> {
            DomainCallback<List<ReviewTag>> cb = invocation.getArgument(0);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(reviewRepo).getReviewTags(any(DomainCallback.class));

        viewModel = new ReviewViewModel(reviewRepo, sessionRepo);

        SessionSummary session = new SessionSummary("sid", WalkSession.Status.COMPLETED,
                null, false, false, null, null, 0L, 0.0, 0.0, null, Collections.emptyList());

        doAnswer(invocation -> {
            DomainCallback<SessionSummary> cb = invocation.getArgument(1);
            cb.onSuccess(session);
            return null;
        }).when(sessionRepo).getSessionSummary(anyString(), any(DomainCallback.class));

        viewModel.loadReviewState("sid");

        ReviewUiState state = viewModel.getReviewUiState().getValue();
        assertEquals(ReviewUiState.Kind.IDLE, state.kind);
    }
}
