package com.walkmate.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

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
 *   loadDashboard() → posts loading state → fetches profile + sessions
 *   → assembles HomeDashboardUiState → postValue() → HomeFragment renders.
 *
 * The ViewModel holds zero Context references and never touches Views.
 */
public class HomeViewModel extends ViewModel {

    private final MutableLiveData<HomeDashboardUiState> uiState = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final WalkSessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final UserProfileRepository profileRepo;
    private final NotificationRepository notificationRepo;

    // Cached profile data so we don't re-fetch on every loadDashboard()
    private String cachedGreetingName = null;

    public HomeViewModel(WalkSessionRepository sessionRepo,
                         UserRepository userRepo,
                         UserProfileRepository profileRepo,
                         NotificationRepository notificationRepo) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.notificationRepo = notificationRepo;
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

        // Fetch the user profile to get the greeting name from the API.
        profileRepo.getMyProfile(new DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                cachedGreetingName = profile.getFullName();
                loadSessions();
            }

            @Override
            public void onError(Exception error) {
                // Profile fetch failed — continue loading sessions with a
                // fallback greeting name. The dashboard is still useful.
                cachedGreetingName = null;
                loadSessions();
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

    private void loadSessions() {
        sessionRepo.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override
            public void onSuccess(List<WalkSession> sessions) {
                HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot =
                        buildSessionSnapshot(sessions);
                loadNotificationsAndPublish(sessionSnapshot);
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(new HomeDashboardUiState(
                        false, cachedGreetingName, "Ho Chi Minh City", false,
                        0, 7, 0, null, null,
                        0.0, 0, error.getMessage()));
            }
        });
    }

    private void loadNotificationsAndPublish(
            HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot) {
        notificationRepo.getNotifications(new DomainCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> notifications) {
                boolean hasUnreadNotification = false;
                if (notifications != null) {
                    for (Notification notification : notifications) {
                        if (notification != null && !notification.isRead()) {
                            hasUnreadNotification = true;
                            break;
                        }
                    }
                }
                uiState.postValue(buildReadyState(sessionSnapshot, hasUnreadNotification));
            }

            @Override
            public void onError(Exception error) {
                // Notification fetch failure should not block dashboard rendering.
                uiState.postValue(buildReadyState(sessionSnapshot, false));
            }
        });
    }

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
     * Builds the full ready state. Uses the real profile name from the API
     * for the greeting. Mock data is still used for fields that don't yet
     * have a backend endpoint (hotspot count, stats, invite list).
     * Replace these with real repo calls as APIs become available.
     */
    private HomeDashboardUiState buildReadyState(
            HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot,
            boolean hasUnreadNotification) {
        return new HomeDashboardUiState(
                false,
                cachedGreetingName != null ? cachedGreetingName : "WalkMate User",
                "Ho Chi Minh City",     // locationName — replace with location service
                hasUnreadNotification,
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
