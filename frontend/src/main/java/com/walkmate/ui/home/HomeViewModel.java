package com.walkmate.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ViewModel for the Home Dashboard.
 *
 * Owns: greeting/location, streak state, upcoming session snapshot,
 * quick-invite candidate list, and weekly stats.
 *
 * Data flow:
 *   loadDashboard() → posts loading state → fires 4 parallel calls
 *   (profile→stats chained, sessions, hotspots, friends)
 *   → when all 4 logical units complete → loads notifications → publishes state.
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
    private final HotspotRepository      hotspotRepo;
    private final GamificationRepository gamificationRepo;
    private final SocialRepository       socialRepo;

    // ── Cached dashboard data ─────────────────────────────────────────────────
    // Each field is updated as async calls return; buildReadyState() reads them all.

    private String cachedGreetingName  = null;
    private String cachedLocationName  = "Your area"; // updated via onLocationResolved()
    private int    cachedHotspotCount  = 0;
    private double cachedDistanceKm    = 0.0;
    private int    cachedSessionCount  = 0;
    private List<HomeDashboardUiState.QuickInviteUser> cachedInviteList = new ArrayList<>();

    public HomeViewModel(WalkSessionRepository sessionRepo,
                         UserRepository userRepo,
                         UserProfileRepository profileRepo,
                         NotificationRepository notificationRepo,
                         HotspotRepository hotspotRepo,
                         GamificationRepository gamificationRepo,
                         SocialRepository socialRepo) {
        this.sessionRepo      = sessionRepo;
        this.userRepo         = userRepo;
        this.profileRepo      = profileRepo;
        this.notificationRepo = notificationRepo;
        this.hotspotRepo      = hotspotRepo;
        this.gamificationRepo = gamificationRepo;
        this.socialRepo       = socialRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<HomeDashboardUiState> getUiState() {
        return uiState;
    }

    /**
     * Triggers a full dashboard data load.
     *
     * Four logical units run in parallel:
     *   1. Profile → then stats (chained so stats has a real userId)
     *   2. Active sessions
     *   3. Nearby hotspots
     *   4. Friends list
     *
     * Notifications are loaded after all four complete (they depend on no data above
     * but we batch the final publish to avoid multiple rapid state emissions).
     */
    public void loadDashboard() {
        uiState.postValue(HomeDashboardUiState.loading());

        final List<WalkSession>[] sessionsHolder = new List[]{null};

        // 4 logical units: (profile+stats), sessions, hotspots, friends
        final AtomicInteger doneCount = new AtomicInteger(0);
        Runnable checkAllDone = () -> {
            if (doneCount.incrementAndGet() == 4) {
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
                        // Stats failure is non-fatal — dashboard renders with default zeros.
                        checkAllDone.run();
                    }
                });
            }
            @Override
            public void onError(Exception e) {
                // Profile failure is non-fatal — greeting uses fallback name; stats skipped.
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
                // Sessions failure is non-fatal — session card simply hidden.
                checkAllDone.run();
            }
        });

        // ── 3. Nearby hotspots ────────────────────────────────────────────────
        hotspotRepo.getHotspots(new DomainCallback<List<Hotspot>>() {
            @Override
            public void onSuccess(List<Hotspot> hotspots) {
                cachedHotspotCount = hotspots != null ? hotspots.size() : 0;
                checkAllDone.run();
            }
            @Override
            public void onError(Exception e) {
                // Hotspot failure is non-fatal — hero subtitle shows 0.
                checkAllDone.run();
            }
        });

        // ── 4. Quick-invite friends list ──────────────────────────────────────
        socialRepo.getFriends(new DomainCallback<List<UserSummary>>() {
            @Override
            public void onSuccess(List<UserSummary> friends) {
                List<HomeDashboardUiState.QuickInviteUser> inviteList = new ArrayList<>();
                if (friends != null) {
                    for (UserSummary f : friends) {
                        inviteList.add(new HomeDashboardUiState.QuickInviteUser(
                                f.getUserId(), f.getFullName(), f.getAvatarUrl()));
                    }
                }
                cachedInviteList = inviteList;
                checkAllDone.run();
            }
            @Override
            public void onError(Exception e) {
                // Friends failure is non-fatal — invite list remains empty.
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
            // Re-emit state with updated location — all other fields unchanged.
            uiState.postValue(new HomeDashboardUiState(
                    false,
                    current.getGreetingName(),
                    cachedLocationName,
                    current.hasUnreadNotification(),
                    current.getStreakDays(),
                    current.getStreakGoal(),
                    current.getNearbyHotspotCount(),
                    current.getUpcomingSession(),
                    current.getQuickInviteList(),
                    current.getWeeklyDistanceKm(),
                    current.getWeeklySessionCount(),
                    null));
        }
    }

    /**
     * Called when the user taps the "Find a WalkMate" CTA.
     */
    public void onFindWalkMateClicked() {
        // Phase D: emit a navigation signal (e.g. SingleLiveEvent<Void>) to
        // the Fragment so it can navigate to the Explore screen.
        // For Phase B this is a no-op; the Fragment handles it directly.
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
                // Notification failure should not block dashboard rendering.
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
                5,                   // TODO: No backend endpoint for streaks yet — hardcoded.
                7,                   // streakGoal
                cachedHotspotCount,
                sessionSnapshot,
                cachedInviteList,
                cachedDistanceKm,
                cachedSessionCount,
                null);
    }

    /**
     * Extracts the HH:mm portion from an ISO-8601 datetime string.
     * Falls back to the raw string if parsing fails.
     */
    private String formatScheduledTime(String iso8601) {
        if (iso8601 == null || iso8601.length() < 16) return iso8601;
        try {
            return iso8601.substring(11, 16);
        } catch (Exception e) {
            return iso8601;
        }
    }
}
