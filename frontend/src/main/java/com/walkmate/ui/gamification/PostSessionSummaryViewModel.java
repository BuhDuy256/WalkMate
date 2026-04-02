package com.walkmate.ui.gamification;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.LeaderboardEntry;
import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;

import java.util.List;

/**
 * ViewModel that backs the post-session summary screen.
 *
 * <p>Loads the current user's stats and newly earned badges after a session
 * completes, and also exposes the global leaderboard. All network calls are
 * dispatched onto a background thread by the repository; results arrive on
 * whatever thread the executor chooses, so the UI must observe via
 * {@link androidx.lifecycle.LiveData} and post to the main thread via
 * {@link MutableLiveData#postValue}.</p>
 */
public class PostSessionSummaryViewModel extends ViewModel {

    public enum LoadState { IDLE, LOADING, SUCCESS, ERROR }

    private final GamificationRepository repository;

    private final MutableLiveData<LoadState>          statsState       = new MutableLiveData<>(LoadState.IDLE);
    private final MutableLiveData<UserStats>          userStats        = new MutableLiveData<>();
    private final MutableLiveData<List<UserBadge>>   badges           = new MutableLiveData<>();
    private final MutableLiveData<List<LeaderboardEntry>> leaderboard  = new MutableLiveData<>();
    private final MutableLiveData<String>             error            = new MutableLiveData<>();

    public PostSessionSummaryViewModel(GamificationRepository repository) {
        this.repository = repository;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public LiveData<LoadState>            getStatsState()   { return statsState; }
    public LiveData<UserStats>            getUserStats()     { return userStats; }
    public LiveData<List<UserBadge>>      getBadges()        { return badges; }
    public LiveData<List<LeaderboardEntry>> getLeaderboard() { return leaderboard; }
    public LiveData<String>               getError()         { return error; }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Loads user stats and badges in parallel. Both LiveDatas update
     * independently as responses arrive.
     */
    public void loadUserSummary(String userId) {
        statsState.postValue(LoadState.LOADING);

        repository.getStats(userId, new com.walkmate.domain.shared.DomainCallback<UserStats>() {
            @Override
            public void onSuccess(UserStats result) {
                userStats.postValue(result);
                statsState.postValue(LoadState.SUCCESS);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getMessage());
                statsState.postValue(LoadState.ERROR);
            }
        });

        repository.getBadges(userId, new com.walkmate.domain.shared.DomainCallback<List<UserBadge>>() {
            @Override
            public void onSuccess(List<UserBadge> result) {
                badges.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal: stats may still load successfully
                error.postValue(e.getMessage());
            }
        });
    }

    public void loadLeaderboard() {
        repository.getLeaderboard(new com.walkmate.domain.shared.DomainCallback<List<LeaderboardEntry>>() {
            @Override
            public void onSuccess(List<LeaderboardEntry> result) {
                leaderboard.postValue(result);
            }

            @Override
            public void onError(Exception e) {
                error.postValue(e.getMessage());
            }
        });
    }
}
