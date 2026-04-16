package com.walkmate.ui.history.routereplay;

import com.walkmate.domain.walksession.SessionRoute;

/**
 * Immutable snapshot of the Route Replay screen state.
 */
public class RouteReplayUiState {

    public enum Kind { LOADING, READY, ERROR }

    public final Kind         kind;
    public final SessionRoute route; // non-null when READY
    public final String       error; // non-null when ERROR

    private RouteReplayUiState(Kind kind, SessionRoute route, String error) {
        this.kind  = kind;
        this.route = route;
        this.error = error;
    }

    public static RouteReplayUiState loading() {
        return new RouteReplayUiState(Kind.LOADING, null, null);
    }

    public static RouteReplayUiState ready(SessionRoute route) {
        return new RouteReplayUiState(Kind.READY, route, null);
    }

    public static RouteReplayUiState error(String message) {
        return new RouteReplayUiState(Kind.ERROR, null, message);
    }
}
