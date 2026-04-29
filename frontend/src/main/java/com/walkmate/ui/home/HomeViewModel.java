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
import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeViewModel extends ViewModel {

    private static final int MAX_RECENT_MATES = 3;

    private final MutableLiveData<HomeDashboardUiState> uiState = new MutableLiveData<>();

    private final WalkSessionRepository  sessionRepo;
    private final UserRepository         userRepo;
    private final UserProfileRepository  profileRepo;
    private final NotificationRepository notificationRepo;
    private final GamificationRepository gamificationRepo;

    private String cachedGreetingName  = null;
    private String cachedLocationName  = "Your area";
    private String cachedUserId        = null;
    private double cachedDistanceKm    = 0.0;
    private int    cachedSessionCount  = 0;
    private List<HomeDashboardUiState.RecentMateSnapshot> cachedRecentMates = Collections.emptyList();

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

    public LiveData<HomeDashboardUiState> getUiState() { return uiState; }

    /**
     * Triggers a full dashboard data load — 3 parallel units, then notifications.
     *   1. Profile → chained into stats
     *   2. Active sessions (upcoming card)
     *   3. Session history (Recent Mates)
     */
    @SuppressWarnings("unchecked")
    public void loadDashboard() {
        uiState.postValue(HomeDashboardUiState.loading());

        final List<WalkSession>[]    activeHolder  = new List[]{null};
        final List<SessionSummary>[] historyHolder = new List[]{null};

        final AtomicInteger doneCount = new AtomicInteger(0);
        Runnable checkAllDone = () -> {
            if (doneCount.incrementAndGet() == 3) {
                cachedRecentMates = buildRecentMates(historyHolder[0], cachedUserId);
                loadNotificationsAndPublish(buildSessionSnapshot(activeHolder[0]));
            }
        };

        // 1. Profile → chain into stats
        profileRepo.getMyProfile(new DomainCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                cachedGreetingName = profile.getFullName();
                cachedUserId       = profile.getUserId();

                gamificationRepo.getStats(profile.getUserId(), new DomainCallback<UserStats>() {
                    @Override
                    public void onSuccess(UserStats stats) {
                        cachedDistanceKm   = stats.getTotalDistanceKm();
                        cachedSessionCount = stats.getCompletedSessions();
                        checkAllDone.run();
                    }
                    @Override public void onError(Exception e) { checkAllDone.run(); }
                });
            }
            @Override public void onError(Exception e) { checkAllDone.run(); }
        });

        // 2. Active sessions
        sessionRepo.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override
            public void onSuccess(List<WalkSession> sessions) {
                activeHolder[0] = sessions;
                checkAllDone.run();
            }
            @Override public void onError(Exception e) { checkAllDone.run(); }
        });

        // 3. Session history → Recent Mates
        sessionRepo.getSessionHistory(new DomainCallback<List<SessionSummary>>() {
            @Override
            public void onSuccess(List<SessionSummary> sessions) {
                historyHolder[0] = sessions;
                checkAllDone.run();
            }
            @Override public void onError(Exception e) { checkAllDone.run(); }
        });
    }

    public void onLocationResolved(String locationName) {
        cachedLocationName = locationName;
        HomeDashboardUiState current = uiState.getValue();
        if (current != null && !current.isLoading()) {
            uiState.postValue(new HomeDashboardUiState(
                    false, current.getGreetingName(), cachedLocationName,
                    current.hasUnreadNotification(), current.getUpcomingSession(),
                    current.getTotalDistanceKm(), current.getCompletedSessions(),
                    null, current.getRecentMates()));
        }
    }

    public void refreshNotificationBadge() {
        HomeDashboardUiState current = uiState.getValue();
        if (current == null || current.isLoading()) return;

        notificationRepo.getNotifications(new DomainCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> notifications) {
                boolean hasUnread = false;
                if (notifications != null) {
                    for (Notification n : notifications) {
                        if (n != null && !n.isRead()) { hasUnread = true; break; }
                    }
                }
                HomeDashboardUiState latest = uiState.getValue();
                if (latest == null || latest.isLoading()) return;
                uiState.postValue(new HomeDashboardUiState(
                        false, latest.getGreetingName(), latest.getLocationName(),
                        hasUnread, latest.getUpcomingSession(),
                        latest.getTotalDistanceKm(), latest.getCompletedSessions(),
                        null, latest.getRecentMates()));
            }
            @Override public void onError(Exception e) {}
        });
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
            @Override public void onError(Exception e) {
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

    private List<HomeDashboardUiState.RecentMateSnapshot> buildRecentMates(
            List<SessionSummary> sessions, String currentUserId) {
        if (sessions == null || sessions.isEmpty() || currentUserId == null) {
            return Collections.emptyList();
        }
        List<HomeDashboardUiState.RecentMateSnapshot> result = new ArrayList<>();
        for (SessionSummary s : sessions) {
            if (result.size() >= MAX_RECENT_MATES) break;
            ParticipantSummary partner = s.getPartnerParticipant(currentUserId);
            if (partner == null) continue;
            long atMs = 0L;
            try { atMs = Instant.parse(s.getScheduledStart()).toEpochMilli(); }
            catch (Exception ignored) {}
            result.add(new HomeDashboardUiState.RecentMateSnapshot(
                    partner.getFullName(),
                    partner.getAvatarUrl(),
                    s.getMeetingPointLat(),
                    s.getMeetingPointLng(),
                    atMs));
        }
        return result;
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
                null,
                cachedRecentMates);
    }

    private String formatScheduledTime(String iso8601) {
        if (iso8601 == null || iso8601.length() < 16) return iso8601;
        try { return iso8601.substring(11, 16); }
        catch (Exception e) { return iso8601; }
    }
}
