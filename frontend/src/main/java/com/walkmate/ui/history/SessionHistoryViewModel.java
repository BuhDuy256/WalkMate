package com.walkmate.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.List;

/**
 * ViewModel for the Session History screen.
 *
 * Partner name enrichment is now performed on the backend and included in the
 * history payload. The ViewModel simply fetches and forwards the result.
 */
public class SessionHistoryViewModel extends ViewModel {

    private final MutableLiveData<SessionHistoryUiState> uiState = new MutableLiveData<>(
            SessionHistoryUiState.loading());

    private final WalkSessionRepository sessionRepo;
    private final String currentUserId;

    public SessionHistoryViewModel(WalkSessionRepository sessionRepo, String currentUserId) {
        this.sessionRepo = sessionRepo;
        this.currentUserId = currentUserId;
    }

    public LiveData<SessionHistoryUiState> getUiState() {
        return uiState;
    }

    public void loadHistory() {
        uiState.postValue(SessionHistoryUiState.loading());

        sessionRepo.getSessionHistory(new DomainCallback<List<SessionSummary>>() {
            @Override
            public void onSuccess(List<SessionSummary> sessions) {
                uiState.postValue(SessionHistoryUiState.ready(sessions, currentUserId));
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(SessionHistoryUiState.error(
                        ErrorMessageResolver.resolve(e.getMessage())));
            }
        });
    }
}
