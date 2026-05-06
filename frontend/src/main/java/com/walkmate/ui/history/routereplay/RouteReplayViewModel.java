package com.walkmate.ui.history.routereplay;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionRoute;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * ViewModel for the Route Replay screen.
 *
 * Loads the encoded polyline data for a completed session.
 * The Activity draws the decoded paths on a GoogleMap in onMapReady().
 */
public class RouteReplayViewModel extends ViewModel {

    private final MutableLiveData<RouteReplayUiState> uiState = new MutableLiveData<>(RouteReplayUiState.loading());

    private final WalkSessionRepository sessionRepo;

    public RouteReplayViewModel(WalkSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public LiveData<RouteReplayUiState> getUiState() {
        return uiState;
    }

    /**
     * Fetches the route data for the given session.
     * Posts LOADING immediately, then READY or ERROR when the response arrives.
     */
    public void loadRoute(String sessionId) {
        uiState.postValue(RouteReplayUiState.loading());

        sessionRepo.getSessionRoute(sessionId, new DomainCallback<SessionRoute>() {
            @Override
            public void onSuccess(SessionRoute route) {
                uiState.postValue(RouteReplayUiState.ready(route));
            }

            @Override
            public void onError(Exception e) {
                String msg = e.getMessage();
                uiState.postValue(RouteReplayUiState.error(
                        (msg != null && !msg.isEmpty()) ? msg : "Failed to load route"));
            }
        });
    }
}
