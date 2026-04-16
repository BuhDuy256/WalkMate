package com.walkmate.ui.gamification;

import com.walkmate.domain.gamification.UserBadge;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.walksession.SessionSummary;

import java.util.List;

/**
 * Immutable snapshot of the Post-Session Summary screen state.
 *
 * Fields populate independently as parallel loads complete.
 * The Fragment renders whatever is available and tolerates nulls gracefully.
 */
public class PostSessionSummaryUiState {

    public final boolean       isLoading;
    public final String        sessionId;
    public final String        partnerName;   // passed as Bundle arg, always available
    public final SessionSummary summary;       // null until loadSummary() resolves
    public final UserStats     userStats;     // null until loadUserSummary() resolves
    public final List<UserBadge> newBadges;   // null until badges load
    public final boolean       isReviewed;   // from summary.isReviewed()
    public final String        error;         // non-null on fatal error

    public PostSessionSummaryUiState(
            boolean isLoading,
            String sessionId,
            String partnerName,
            SessionSummary summary,
            UserStats userStats,
            List<UserBadge> newBadges,
            boolean isReviewed,
            String error) {
        this.isLoading   = isLoading;
        this.sessionId   = sessionId;
        this.partnerName = partnerName;
        this.summary     = summary;
        this.userStats   = userStats;
        this.newBadges   = newBadges;
        this.isReviewed  = isReviewed;
        this.error       = error;
    }
}
