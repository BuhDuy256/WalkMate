package com.walkmate.ui.gamification.leaderboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.gamification.LeaderboardEntry;
import com.walkmate.domain.shared.DomainCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LeaderboardViewModel extends ViewModel {

    private final GamificationRepository gamificationRepository;
    private final String                 myUserId;

    private final MutableLiveData<LeaderboardUiState> uiState =
            new MutableLiveData<>(new LeaderboardUiState(false, null, null, null));

    // Cache of last successful response for offline-banner support
    private List<LeaderboardEntry> cachedEntries;
    private String                 cachedTimestamp;

    public LeaderboardViewModel(GamificationRepository gamificationRepository, String myUserId) {
        this.gamificationRepository = gamificationRepository;
        this.myUserId               = myUserId;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public LiveData<LeaderboardUiState> getUiState() { return uiState; }

    public String getMyUserId() { return myUserId; }

    // ── Actions ───────────────────────────────────────────────────────────────

    public void loadLeaderboard() {
        uiState.setValue(new LeaderboardUiState(true, null, cachedEntries, null));

        gamificationRepository.getLeaderboard(new DomainCallback<List<LeaderboardEntry>>() {
            @Override
            public void onSuccess(List<LeaderboardEntry> result) {
                cachedEntries   = result;
                cachedTimestamp = new SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date());
                uiState.postValue(new LeaderboardUiState(false, null, result, null));
            }

            @Override
            public void onError(Exception e) {
                // If we have cached data, show it with a stale-data banner
                String banner = cachedEntries != null && cachedTimestamp != null
                        ? "Last updated at " + cachedTimestamp
                        : null;
                uiState.postValue(
                        new LeaderboardUiState(false, e.getMessage(), cachedEntries, banner));
            }
        });
    }
}
