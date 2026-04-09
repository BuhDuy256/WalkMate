package com.walkmate.ui.history;

import com.walkmate.domain.walksession.SessionSummary;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the Session History screen state.
 *
 * {@code partnerNames} maps partnerId → display name and is populated
 * progressively by {@link SessionHistoryViewModel}'s enrichment step.
 * The adapter falls back to the raw partnerId when a name is absent.
 */
public class SessionHistoryUiState {

    public enum Kind { LOADING, READY, ERROR }

    public final Kind                 kind;
    public final List<SessionSummary> sessions;     // non-null when READY
    public final Map<String, String>  partnerNames; // partnerId → display name; may be partial
    public final String               error;         // non-null when ERROR

    private SessionHistoryUiState(Kind kind,
                                   List<SessionSummary> sessions,
                                   Map<String, String> partnerNames,
                                   String error) {
        this.kind         = kind;
        this.sessions     = sessions;
        this.partnerNames = partnerNames != null ? partnerNames : Collections.emptyMap();
        this.error        = error;
    }

    public static SessionHistoryUiState loading() {
        return new SessionHistoryUiState(Kind.LOADING, null, null, null);
    }

    public static SessionHistoryUiState ready(List<SessionSummary> sessions) {
        return new SessionHistoryUiState(Kind.READY, sessions, Collections.emptyMap(), null);
    }

    public static SessionHistoryUiState error(String message) {
        return new SessionHistoryUiState(Kind.ERROR, null, null, message);
    }

    /** Returns a new state with the enriched partner names map. */
    public SessionHistoryUiState withPartnerNames(Map<String, String> names) {
        return new SessionHistoryUiState(kind, sessions, names, error);
    }
}
