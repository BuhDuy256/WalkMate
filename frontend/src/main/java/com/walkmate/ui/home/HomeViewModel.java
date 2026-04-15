package com.walkmate.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ViewModel for the Home Dashboard.
 *
 * Owns: greeting/location, upcoming session snapshot, and lifetime stats.
 *
 * Data flow:
 *   loadDashboard() → posts loading state → fires 2 parallel calls
 *   (profile → stats chained, sessions)
 *   → when both complete → loads notifications → publishes state.
 *
 * Location name is resolved separately via onLocationResolved() — the Fragment
 * holds the Android location client and calls this when a fix is available.
 *
 * The ViewModel holds zero Context references and never touches Views.
 */
public class HomeViewModel extends ViewModel {

    private final MutableLiveData<HomeDashboardUiState> uiState = new MutableLiveData<>();

    private final WalkSessionRepository  sessionRepo;
    private final UserRepository         userRepo;
    private final UserProfileRepository  profileRepo;
    private final NotificationRepository notificationRepo;
    private final GamificationRepository gamificationRepo;

    // ── Cached dashboard data ─────────────────────────────────────────────────

    private String cachedGreetingName = null;
    private String cachedLocationName = "Your area"; // updated via onLocationResolved()
    private double cachedDistanceKm   = 0.0;
    private int    cachedSessionCount = 0;

    public HomeViewModel(WalkSessionRepository sessionRepo,
                         UserRepository userRepo,
                         UserProfileRepository profileRepo,
                         NotificationRepository notificationRepo,
                         GamificationRepository gamificationRepo) {
        this.sessionRepo      = sessionRepo;
        this.userRepo         = userRepo;
        this.profileRepo      = profileRepo;
        this.notificationRepo = notificationRepo;
        this.gamificationRepo = gamificationRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<HomeDashboardUiState> getUiState() {
        return uiState;
    }

    /**
     * Triggers a full dashboard data load.
     * Call only when {@link #getUiState()} has no value yet (first load).
     *
     * Two logical units run in parallel:
     *   1. Profile → then stats (chained so stats has a real userId)
     *   2. Active sessions
     *
     * Notifications are loaded after both complete.
     */
    public void loadDashboard() {
        uiState.postValue(HomeDashboardUiState.loading());

        final List<WalkSession>[] sessionsHolder = new List[]{null};

        final AtomicInteger doneCount = new AtomicInteger(0);
        Runnable checkAllDone = () -> {
            if (doneCount.incrementAndGet() == 2) {
                loadNotificationsAndPublish(buildSessionSnapshot(sessionsHolder[0]));
            }
        };

        // ── 1. Profile → chain into getStats ──────────────────────────────────
        profileRepo.getMyProfile(new DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                cachedGreetingName = profile.getFullName();

                gamificationRepo.getStats(profile.getUserId(), new DomainCallback<UserStats>() {
                    @Override
                    public void onSuccess(UserStats stats) {
                        cachedDistanceKm   = stats.getTotalDistanceKm();
                        cachedSessionCount = stats.getCompletedSessions();
                        checkAllDone.run();
                    }
                    @Override
                    public void onError(Exception e) {
                        checkAllDone.run();
                    }
                });
            }
            @Override
            public void onError(Exception e) {
                checkAllDone.run();
            }
        });

        // ── 2. Active sessions ────────────────────────────────────────────────
        sessionRepo.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override
            public void onSuccess(List<WalkSession> sessions) {
                sessionsHolder[0] = sessions;
                checkAllDone.run();
            }
            @Override
            public void onError(Exception e) {
                checkAllDone.run();
            }
        });
    }

    /**
     * Called by HomeFragment when it has resolved the device location to a city name.
     * Re-publishes the current ready state with the updated location, or caches it
     * for inclusion in the next publish if the dashboard hasn't loaded yet.
     */
    public void onLocationResolved(String locationName) {
        cachedLocationName = locationName;
        HomeDashboardUiState current = uiState.getValue();
        if (current != null && !current.isLoading()) {
            uiState.postValue(new HomeDashboardUiState(
                    false,
                    current.getGreetingName(),
                    cachedLocationName,
                    current.hasUnreadNotification(),
                    current.getUpcomingSession(),
                    current.getTotalDistanceKm(),
                    current.getCompletedSessions(),
                    null));
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void loadNotificationsAndPublish(
            HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot) {
        notificationRepo.getNotifications(new DomainCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> notifications) {
                boolean hasUnread = false;
                if (notifications != null) {
                    for (Notification n : notifications) {
                        if (n != null && !n.isRead()) { hasUnread = true; break; }
                    }
                }
                uiState.postValue(buildReadyState(sessionSnapshot, hasUnread));
            }
            @Override
            public void onError(Exception e) {
                uiState.postValue(buildReadyState(sessionSnapshot, false));
            }
        });
    }

    private HomeDashboardUiState.UpcomingSessionSnapshot buildSessionSnapshot(
            List<WalkSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;
        WalkSession session = sessions.get(0);
        return new HomeDashboardUiState.UpcomingSessionSnapshot(
                session.getSessionId(),
                session.getPartnerName(),
                session.getPartnerAvatar(),
                formatScheduledTime(session.getScheduledTime()),
                "Confirmed");
    }

    private HomeDashboardUiState buildReadyState(
            HomeDashboardUiState.UpcomingSessionSnapshot sessionSnapshot,
            boolean hasUnreadNotification) {
        return new HomeDashboardUiState(
                false,
                cachedGreetingName != null ? cachedGreetingName : "WalkMate User",
                cachedLocationName,
                hasUnreadNotification,
                sessionSnapshot,
                cachedDistanceKm,
                cachedSessionCount,
                null);
    }

    private String formatScheduledTime(String iso8601) {
        if (iso8601 == null || iso8601.length() < 16) return iso8601;
        try {
            return iso8601.substring(11, 16);
        } catch (Exception e) {
            return iso8601;
        }
    }
}
