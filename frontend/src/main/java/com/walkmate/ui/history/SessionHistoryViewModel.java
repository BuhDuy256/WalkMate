package com.walkmate.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for the Session History screen.
 *
 * Data flow:
 *   loadHistory() → posts LOADING → calls getSessionHistory()
 *     → posts READY with empty partnerNames
 *     → fires parallel getProfile() calls for each unique partnerId
 *     → re-posts READY with progressively enriched partnerNames map.
 *
 * Partner name enrichment follows the same pattern as MatchesViewModel (Phase 8):
 * a profile cache prevents duplicate calls; failures are non-fatal (adapter
 * falls back to the raw partnerId string).
 */
public class SessionHistoryViewModel extends ViewModel {

    private final MutableLiveData<SessionHistoryUiState> uiState =
            new MutableLiveData<>(SessionHistoryUiState.loading());

    private final WalkSessionRepository  sessionRepo;
    private final UserProfileRepository  profileRepo;

    /** Cache: partnerId → display name. Avoids re-fetching on re-load. */
    private final Map<String, String> profileCache = new HashMap<>();

    public SessionHistoryViewModel(WalkSessionRepository sessionRepo,
                                   UserProfileRepository profileRepo) {
        this.sessionRepo = sessionRepo;
        this.profileRepo = profileRepo;
    }

    public LiveData<SessionHistoryUiState> getUiState() {
        return uiState;
    }

    /**
     * Fetches session history from the backend. Posts LOADING immediately,
     * then READY with an empty names map. Enrichment fires in parallel.
     */
    public void loadHistory() {
        uiState.postValue(SessionHistoryUiState.loading());

        sessionRepo.getSessionHistory(new DomainCallback<List<SessionSummary>>() {
            @Override
            public void onSuccess(List<SessionSummary> sessions) {
                uiState.postValue(SessionHistoryUiState.ready(sessions));
                enrichPartnerNames(sessions);
            }

            @Override
            public void onError(Exception e) {
                String msg = e.getMessage();
                uiState.postValue(SessionHistoryUiState.error(
                        (msg != null && !msg.isEmpty()) ? msg : "Failed to load history"));
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * For each unique partnerId in the session list, fetches the user's profile
     * and updates the state's partnerNames map as each response arrives.
     * Cache hits are applied immediately without a network call.
     */
    private void enrichPartnerNames(List<SessionSummary> sessions) {
        if (sessions == null || sessions.isEmpty()) return;

        for (SessionSummary session : sessions) {
            String partnerId = session.getPartnerId();
            if (partnerId == null || partnerId.isEmpty()) continue;

            if (profileCache.containsKey(partnerId)) {
                // Already cached — re-publish with existing names.
                rebuildWithCache();
                continue;
            }

            profileRepo.getProfile(partnerId, new DomainCallback<UserProfile>() {
                @Override
                public void onSuccess(UserProfile profile) {
                    String name = profile.getFullName();
                    if (name != null && !name.isEmpty()) {
                        profileCache.put(partnerId, name);
                        rebuildWithCache();
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Non-fatal: adapter keeps the raw partnerId as placeholder.
                }
            });
        }
    }

    /**
     * Re-posts the current READY state with the latest snapshot of the cache.
     * Called from background threads — safe via postValue().
     */
    private void rebuildWithCache() {
        SessionHistoryUiState current = uiState.getValue();
        if (current == null || current.kind != SessionHistoryUiState.Kind.READY) return;
        uiState.postValue(current.withPartnerNames(new HashMap<>(profileCache)));
    }
}
