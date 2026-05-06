package com.walkmate.ui.badge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.gamification.BadgeCatalogItem;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.shared.DomainCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

public class BadgeViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private GamificationRepository gamificationRepo;

    private BadgeViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new BadgeViewModel(gamificationRepo, "u1");
    }

    @Test
    public void load_success_buildsUiStateWithEarnedAndProgress() {
        List<BadgeCatalogItem> catalog = List.of(
                new BadgeCatalogItem("FIRST_WALK", "First Walk", "Complete first walk", "common", "Milestone"),
                new BadgeCatalogItem("TEN_KM_WALKER", "10km Walker", "Walk 10km", "rare", "Distance")
        );
        List<UserBadge> earned = List.of(
                new UserBadge("FIRST_WALK", "First Walk", "Complete first walk",
                        "2026-04-20 10:30:00", "common", "Milestone")
        );
        UserStats stats = new UserStats("u1", 120, 7.9, 3, 40);

        doAnswer(invocation -> {
            DomainCallback<List<BadgeCatalogItem>> cb = invocation.getArgument(0);
            cb.onSuccess(catalog);
            return null;
        }).when(gamificationRepo).getBadgeCatalog(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onSuccess(earned);
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onSuccess(stats);
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        viewModel.load();

        BadgeUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertEquals(1, state.getEarnedCount());
        assertEquals(2, state.getTotalCount());
        assertEquals(50, state.getProgressPct());

        BadgeUiState.BadgeItem firstWalk = findBadge(state, "FIRST_WALK");
        assertNotNull(firstWalk);
        assertTrue(firstWalk.earned);
        assertNotNull(firstWalk.earnedDate);

        BadgeUiState.BadgeItem tenKm = findBadge(state, "TEN_KM_WALKER");
        assertNotNull(tenKm);
        assertFalse(tenKm.earned);
        assertEquals(70, tenKm.progressPct);
        assertEquals("7 / 10 km", tenKm.progressLabel);
    }

    @Test
    public void load_catalogFailsAndEmpty_postsErrorState() {
        doAnswer(invocation -> {
            DomainCallback<List<BadgeCatalogItem>> cb = invocation.getArgument(0);
            cb.onError(new Exception("catalog down"));
            return null;
        }).when(gamificationRepo).getBadgeCatalog(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onSuccess(Collections.emptyList());
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onSuccess(new UserStats("u1", 0, 0.0, 0, 0));
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        viewModel.load();

        BadgeUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertEquals("Failed to load badges", state.getError());
        assertNull(state.getCategories());
    }

    @Test
    public void load_badgesAndStatsFail_butCatalogSuccess_stillBuildsState() {
        List<BadgeCatalogItem> catalog = List.of(
                new BadgeCatalogItem("FIRST_FIVE", "First Five", "Complete 5 walks", "common", "Milestone")
        );

        doAnswer(invocation -> {
            DomainCallback<List<BadgeCatalogItem>> cb = invocation.getArgument(0);
            cb.onSuccess(catalog);
            return null;
        }).when(gamificationRepo).getBadgeCatalog(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserBadge>> cb = invocation.getArgument(1);
            cb.onError(new Exception("badges timeout"));
            return null;
        }).when(gamificationRepo).getBadges(anyString(), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<UserStats> cb = invocation.getArgument(1);
            cb.onError(new Exception("stats timeout"));
            return null;
        }).when(gamificationRepo).getStats(anyString(), any(DomainCallback.class));

        viewModel.load();

        BadgeUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertEquals(0, state.getEarnedCount());
        assertEquals(1, state.getTotalCount());
        assertEquals(1, state.getCategories().size());

        BadgeUiState.BadgeItem item = findBadge(state, "FIRST_FIVE");
        assertNotNull(item);
        assertFalse(item.earned);
        assertEquals(0, item.progressPct);
        assertNull(item.progressLabel);
    }

    private static BadgeUiState.BadgeItem findBadge(BadgeUiState state, String badgeName) {
        if (state == null || state.getCategories() == null) return null;
        for (BadgeUiState.BadgeCategory category : state.getCategories()) {
            for (BadgeUiState.BadgeItem badge : category.badges) {
                if (badgeName.equals(badge.badgeName)) return badge;
            }
        }
        return null;
    }
}

