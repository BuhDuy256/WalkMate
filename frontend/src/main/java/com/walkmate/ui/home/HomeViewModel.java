package com.walkmate.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Home Dashboard.
 *
 * Owns: greeting/location, streak state, upcoming session snapshot,
 * quick-invite candidate list, and weekly stats.
 *
 * Data flow:
 *   loadDashboard() → posts loading state → fetches sessions via repo
 *   → assembles HomeDashboardUiState → postValue() → HomeFragment renders.
 *
 * The ViewModel holds zero Context references and never touches Views.
 */
public class HomeViewModel extends ViewModel {

    private final MutableLiveData<HomeDashboardUiState> uiState = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final WalkSessionRepository sessionRepo;
    private final UserRepository userRepo;

    public HomeViewModel(WalkSessionRepository sessionRepo, UserRepository userRepo) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<HomeDashboardUiState> getUiState() {
        return uiState;
    }

    /**
     * Triggers a full dashboard data load.
     * Safe to call multiple times (e.g., on resume). Each call posts a fresh
     * loading state before fetching, preventing stale data from showing.
     */
    public void loadDashboard() {
        uiState.postValue(HomeDashboardUiState.loading());

        // Fetch active sessions — the repo's callback fires on its own background
        // thread, so postValue() is safe here without wrapping in the executor.
        sessionRepo.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override
            public void onSuccess(List<WalkSession> sessions) {
                HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot =
                        buildSessionSnapshot(sessions);
                uiState.postValue(buildReadyState(sessionSnapshot));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(new HomeDashboardUiState(
                        false, null, null, false,
                        0, 7, 0, null, null,
                        0.0, 0, error.getMessage()));
            }
        });
    }

    /**
     * Called when the user taps the "Find a WalkMate" CTA.
     * Navigation is driven by the Fragment observing this signal — the ViewModel
     * does not hold an Activity reference.
     */
    public void onFindWalkMateClicked() {
        // Phase D: emit a navigation signal (e.g. SingleLiveEvent<Void>) to
        // the Fragment so it can navigate to the Explore screen.
        // For Phase B this is a no-op; the Fragment handles it directly.
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HomeDashboardUiState.UpcomingSessionSnapshot buildSessionSnapshot(
            List<WalkSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;
        WalkSession session = sessions.get(0);
        String timeAndPlace = formatScheduledTime(session.getScheduledTime());
        return new HomeDashboardUiState.UpcomingSessionSnapshot(
                session.getSessionId(),
                session.getPartnerName(),
                session.getPartnerAvatar(),
                timeAndPlace,
                "Confirmed");
    }

    /**
     * Builds the full ready state with mock data for fields that don't yet
     * have a backend endpoint (profile info, hotspot count, stats, invite list).
     * Replace these with real repo calls as APIs become available.
     */
    private HomeDashboardUiState buildReadyState(
            HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot) {
        return new HomeDashboardUiState(
                false,
                "Alex",                 // greetingName — replace with userRepo.getProfile()
                "Ho Chi Minh City",     // locationName — replace with location service
                true,                   // hasUnreadNotification
                5,                      // streakDays
                7,                      // streakGoal
                5,                      // nearbyHotspotCount — replace with hotspot repo
                sessionSnapshot,
                buildMockInviteList(),
                12.5,                   // weeklyDistanceKm — replace with stats repo
                3,                      // weeklySessionCount
                null);
    }

    private List<HomeDashboardUiState.QuickInviteUser> buildMockInviteList() {
        return Arrays.asList(
                new HomeDashboardUiState.QuickInviteUser("u1", "Minh", null),
                new HomeDashboardUiState.QuickInviteUser("u2", "Sarah", null),
                new HomeDashboardUiState.QuickInviteUser("u3", "Linh", null),
                new HomeDashboardUiState.QuickInviteUser("u4", "Tom", null),
                new HomeDashboardUiState.QuickInviteUser("u5", "Hà", null)
        );
    }

    /**
     * Extracts the HH:mm portion from an ISO-8601 datetime string.
     * Falls back to the raw string if parsing fails.
     */
    private String formatScheduledTime(String iso8601) {
        if (iso8601 == null || iso8601.length() < 16) return iso8601;
        // "2026-03-29T14:00:00Z" → "14:00"
        try {
            return iso8601.substring(11, 16);
        } catch (Exception e) {
            return iso8601;
        }
    }
}
