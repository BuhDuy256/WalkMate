package com.walkmate.ui.gamification;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.List;

/**
 * ViewModel that backs the post-session summary screen.
 *
 * Loads the current user's stats and newly earned badges after a session
 * completes. All network calls are dispatched onto a background thread by the
 * repository; results arrive on whatever thread the executor chooses, so the
 * UI observes via LiveData and receives updates on the main thread via postValue.
 */
public class PostSessionSummaryViewModel extends ViewModel {

    public enum LoadState { IDLE, LOADING, SUCCESS, ERROR }

    private final GamificationRepository gamificationRepo;
    private final WalkSessionRepository  sessionRepo;

    private final MutableLiveData<LoadState>        statsState     = new MutableLiveData<>(LoadState.IDLE);
    private final MutableLiveData<UserStats>        userStats      = new MutableLiveData<>();
    private final MutableLiveData<List<UserBadge>>  badges         = new MutableLiveData<>();
    private final MutableLiveData<String>            error          = new MutableLiveData<>();
    private final MutableLiveData<SessionSummary>    sessionSummary = new MutableLiveData<>();

    public PostSessionSummaryViewModel(GamificationRepository gamificationRepo,
                                       WalkSessionRepository sessionRepo) {
        this.gamificationRepo = gamificationRepo;
        this.sessionRepo      = sessionRepo;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public LiveData<LoadState>         getStatsState()    { return statsState; }
    public LiveData<UserStats>         getUserStats()      { return userStats; }
    public LiveData<List<UserBadge>>   getBadges()         { return badges; }
    public LiveData<String>            getError()          { return error; }
    public LiveData<SessionSummary>    getSessionSummary() { return sessionSummary; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Loads user stats and badges in parallel. Both LiveDatas update
     * independently as responses arrive.
     */
    public void loadUserSummary(String userId) {
        statsState.postValue(LoadState.LOADING);

        gamificationRepo.getStats(userId, new com.walkmate.domain.shared.DomainCallback<UserStats>() {
            @Override
            public void onSuccess(UserStats result) {
                userStats.postValue(result);
                statsState.postValue(LoadState.SUCCESS);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(ErrorMessageResolver.resolve(e.getMessage()));
                statsState.postValue(LoadState.ERROR);
            }
        });

        gamificationRepo.getBadges(userId, new com.walkmate.domain.shared.DomainCallback<List<UserBadge>>() {
            @Override
            public void onSuccess(List<UserBadge> result) {
                badges.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal: stats may still load successfully
                error.postValue(ErrorMessageResolver.resolve(e.getMessage()));
            }
        });
    }

    /**
     * Fetches session history and finds the matching SessionSummary for sessionId.
     * Posts the result to getSessionSummary(). Non-fatal: a null value is posted
     * if the session cannot be found.
     */
    public void loadSummary(String sessionId) {
        sessionRepo.getSessionHistory(new com.walkmate.domain.shared.DomainCallback<List<SessionSummary>>() {
            @Override
            public void onSuccess(List<SessionSummary> sessions) {
                SessionSummary found = null;
                if (sessions != null) {
                    for (SessionSummary s : sessions) {
                        if (sessionId.equals(s.getSessionId())) {
                            found = s;
                            break;
                        }
                    }
                }
                sessionSummary.postValue(found);
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal: summary card simply won't populate if history fails.
                error.postValue(ErrorMessageResolver.resolve(e.getMessage()));
            }
        });
    }
}
