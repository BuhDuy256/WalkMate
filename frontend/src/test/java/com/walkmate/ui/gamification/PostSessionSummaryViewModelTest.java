package com.walkmate.ui.gamification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.ParticipantSummary;
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

public class PostSessionSummaryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private GamificationRepository gamificationRepo;

    @Mock
    private WalkSessionRepository sessionRepo;

    private PostSessionSummaryViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new PostSessionSummaryViewModel(gamificationRepo, sessionRepo);
    }

    @Test
    public void loadUserSummary_statsAndBadgesSuccess_postsSuccessState() {
        UserStats stats = new UserStats("u1", 200, 12.5, 8, 90);
        List<UserBadge> badges = List.of(
                new UserBadge("FIRST_WALK", "First Walk", "Complete first walk",
                        "2026-05-01 10:00:00", "common", "Milestone")
        );

        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onSuccess(stats);
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onSuccess(badges);
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        viewModel.loadUserSummary("u1");

        assertEquals(PostSessionSummaryViewModel.LoadState.SUCCESS, viewModel.getStatsState().getValue());
        assertEquals(12.5, viewModel.getUserStats().getValue().getTotalDistanceKm(), 0.0001);
        assertEquals(1, viewModel.getBadges().getValue().size());
        assertNull(viewModel.getError().getValue());
    }

    @Test
    public void loadUserSummary_statsError_postsErrorState() {
        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onError(new Exception("stats failed"));
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        viewModel.loadUserSummary("u1");

        assertEquals(PostSessionSummaryViewModel.LoadState.ERROR, viewModel.getStatsState().getValue());
        assertEquals("stats failed", viewModel.getError().getValue());
    }

    @Test
    public void loadSummary_foundMatchingSession_postsSessionSummary() {
        SessionSummary target = summary("session-2");
        List<SessionSummary> sessions = List.of(summary("session-1"), target, summary("session-3"));

        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(sessions);
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadSummary("session-2");

        SessionSummary found = viewModel.getSessionSummary().getValue();
        assertNotNull(found);
        assertEquals("session-2", found.getSessionId());
    }

    @Test
    public void loadSummary_notFound_postsNull() {
        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(summary("session-1")));
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadSummary("missing-session");

        assertNull(viewModel.getSessionSummary().getValue());
    }

    private static SessionSummary summary(String sessionId) {
        ParticipantSummary caller = new ParticipantSummary(
                "u1", "Caller", null, 1.2, 20, WalkSession.Status.COMPLETED);
        ParticipantSummary partner = new ParticipantSummary(
                "u2", "Partner", null, 1.1, 18, WalkSession.Status.COMPLETED);
        return new SessionSummary(
                sessionId,
                WalkSession.Status.COMPLETED,
                "2026-05-06T10:00:00Z",
                false,
                false,
                null,
                null,
                0L,
                10.0,
                106.0,
                "Hotspot",
                List.of(caller, partner));
    }
}

