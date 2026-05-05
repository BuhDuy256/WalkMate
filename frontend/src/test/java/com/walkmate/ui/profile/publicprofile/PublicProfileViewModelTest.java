package com.walkmate.ui.profile.publicprofile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.review.WalkReview;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.shared.DomainCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

public class PublicProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private SocialRepository socialRepo;

    @Mock
    private GamificationRepository gamificationRepo;

    @Mock
    private ReviewRepository reviewRepo;

    private PublicProfileViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new PublicProfileViewModel(socialRepo, gamificationRepo, reviewRepo, "local-user-id");
    }

    @Test
    public void loadProfile_friend_profileShowsLastActive_andIsFriend() {
        UserSummary profile = new UserSummary(
                "u1", "Alice", null, 10, "FRIENDS",
                "Hello", List.of("tag1"), null, "2026-05-05T12:00:00Z");

        // socialRepo returns profile
        doAnswer(invocation -> {
            DomainCallback<UserSummary> cb = invocation.getArgument(1);
            cb.onSuccess(profile);
            return null;
        }).when(socialRepo).getPublicProfile(anyString(), any(DomainCallback.class));

        // gamificationRepo returns empty lists/stats
        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<WalkReview>> cb = invocation.getArgument(1);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(reviewRepo).getReviewsForUser(anyString(), any(DomainCallback.class));

        viewModel.loadProfile("u1");

        PublicProfileUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertTrue(state.isFriend());
        assertEquals("2026-05-05T12:00:00Z", state.getProfile().getLastActiveAt());
    }

    @Test
    public void loadProfile_notFriend_profileHidesLastActive_andNotFriend() {
        UserSummary profile = new UserSummary(
                "u2", "Bob", null, 5, "NONE",
                null, Collections.emptyList(), null, null);

        doAnswer(invocation -> {
            DomainCallback<UserSummary> cb = invocation.getArgument(1);
            cb.onSuccess(profile);
            return null;
        }).when(socialRepo).getPublicProfile(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<WalkReview>> cb = invocation.getArgument(1);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(reviewRepo).getReviewsForUser(anyString(), any(DomainCallback.class));

        viewModel.loadProfile("u2");

        PublicProfileUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertFalse(state.isFriend());
        assertNull(state.getProfile().getLastActiveAt());
    }

    @Test
    public void loadProfile_profileError_setsErrorState() {
        doAnswer(invocation -> {
            DomainCallback<UserSummary> cb = invocation.getArgument(1);
            cb.onError(new Exception("Profile not available"));
            return null;
        }).when(socialRepo).getPublicProfile(anyString(), any(DomainCallback.class));

        viewModel.loadProfile("u3");

        PublicProfileUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isLoading());
        assertEquals("Profile not available", state.getError());
    }
}
