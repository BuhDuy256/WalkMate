package com.walkmate.application.gamification;

/**
 * Spring ApplicationEvent fired after a WalkSession transitions to ABORTED.
 *
 * <p>Published by {@link com.walkmate.application.session.SessionCommandService}
 * inside the committing transaction so the {@link GamificationCommandService}
 * listener can react after the commit with {@code AFTER_COMMIT} phase,
 * opening a fresh {@code REQUIRES_NEW} transaction.</p>
 */
public class SessionAbortedEvent {

    private final String sessionId;
    private final String abortingUserId;

    public SessionAbortedEvent(String sessionId, String abortingUserId) {
        this.sessionId      = sessionId;
        this.abortingUserId = abortingUserId;
    }

    public String getSessionId()      { return sessionId; }
    public String getAbortingUserId() { return abortingUserId; }
}
