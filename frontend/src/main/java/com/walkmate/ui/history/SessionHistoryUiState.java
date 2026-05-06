package com.walkmate.ui.history;

import com.walkmate.domain.walksession.SessionSummary;

import java.util.List;

/**
 * Immutable snapshot of the Session History screen state.
 *
 * {@code currentUserId} is used by the adapter to label the caller's row
 * as "You" and derive the partner ID for report/profile navigation.
 */
public class SessionHistoryUiState {

    public enum Kind {
        LOADING, READY, ERROR
    }

    public final Kind kind;
    public final List<SessionSummary> sessions; // non-null when READY
    public final String currentUserId; // non-null when READY
    public final String error; // non-null when ERROR

    private SessionHistoryUiState(Kind kind, List<SessionSummary> sessions,
            String currentUserId, String error) {
        this.kind = kind;
        this.sessions = sessions;
        this.currentUserId = currentUserId;
        this.error = error;
    }

    public static SessionHistoryUiState loading() {
        return new SessionHistoryUiState(Kind.LOADING, null, null, null);
    }

    public static SessionHistoryUiState ready(List<SessionSummary> sessions, String currentUserId) {
        return new SessionHistoryUiState(Kind.READY, sessions, currentUserId, null);
    }

    public static SessionHistoryUiState error(String message) {
        return new SessionHistoryUiState(Kind.ERROR, null, null, message);
    }
}
