package com.walkmate.application.gamification;

import com.walkmate.domain.session.WalkSession;

/**
 * Spring ApplicationEvent fired after a WalkSession transitions to COMPLETED.
 *
 * <p>Published by {@link com.walkmate.application.session.SessionCommandService}
 * inside the committing transaction so the {@link GamificationCommandService}
 * listener can react after the commit with {@code AFTER_COMMIT} phase,
 * opening a fresh {@code REQUIRES_NEW} transaction.</p>
 */
public class SessionCompletedEvent {

    private final WalkSession session;

    public SessionCompletedEvent(WalkSession session) {
        this.session = session;
    }

    public WalkSession getSession() {
        return session;
    }
}
