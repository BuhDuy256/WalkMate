package com.walkmate.data.repository;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOCK implementation — bypasses Retrofit and returns hardcoded data with a
 * simulated 1-second network delay. Replace each method body with real Retrofit
 * calls when the backend is ready.
 */
public class WalkSessionRepositoryImpl implements WalkSessionRepository {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ---------------------------------------------------------------------------
    // Mock data
    // ---------------------------------------------------------------------------

    private static List<WalkSession> buildMockSessions() {
        return Collections.singletonList(
                new WalkSession(
                        "session-001",
                        "proposal-003",
                        "Thu Hà",
                        null,
                        10.7769, 106.7009,
                        "2026-03-29T14:00:00Z",
                        WalkSession.Status.PENDING)
        );
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void getActiveSessions(DomainCallback<List<WalkSession>> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(buildMockSessions());
        });
    }

    @Override
    public void cancelSession(String sessionId, String reason, DomainCallback<Void> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(null);
        });
    }

    @Override
    public void activateSession(String sessionId, DomainCallback<WalkSession> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(new WalkSession(
                    sessionId,
                    "proposal-003",
                    "Thu Hà",
                    null,
                    10.7769,
                    106.7009,
                    "2026-03-29T14:00:00Z",
                    WalkSession.Status.ACTIVE
            ));
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
