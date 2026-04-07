package com.walkmate.application.gamification;

/**
 * Spring ApplicationEvent fired after a WalkSession transitions to NO_SHOW.
 *
 * <p>Published by {@link com.walkmate.application.session.SessionCommandService}
 * inside the committing transaction so the {@link GamificationCommandService}
 * listener can react after the commit with {@code AFTER_COMMIT} phase,
 * opening a fresh {@code REQUIRES_NEW} transaction.</p>
 */
public class SessionNoShowEvent {

    private final String sessionId;
    private final String penalizedUserId;

    public SessionNoShowEvent(String sessionId, String penalizedUserId) {
        this.sessionId       = sessionId;
        this.penalizedUserId = penalizedUserId;
    }

    public String getSessionId()       { return sessionId; }
    public String getPenalizedUserId() { return penalizedUserId; }
}
