package com.walkmate.data.repository;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSession;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOCK implementation — bypasses Retrofit and returns hardcoded data with a
 * simulated 1-second network delay. Replace each method body with real Retrofit
 * calls when the backend is ready.
 */
public class WalkProposalRepositoryImpl implements WalkProposalRepository {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ---------------------------------------------------------------------------
    // Mock data
    // ---------------------------------------------------------------------------

    private static List<WalkProposal> buildMockProposals() {
        return Arrays.asList(
                new WalkProposal(
                        "proposal-001",
                        "intent-001",
                        "user-42",
                        "Linh Nguyễn",
                        26,
                        92,
                        Arrays.asList("Đi bộ chậm", "Nghe podcast"),
                        17.0f, 18.5f,
                        WalkProposal.Status.PENDING),
                new WalkProposal(
                        "proposal-002",
                        "intent-002",
                        "user-55",
                        "Minh Tuấn",
                        30,
                        85,
                        Arrays.asList("Chạy bộ"),
                        8.5f, 9.5f,
                        WalkProposal.Status.PENDING)
        );
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void getProposals(DomainCallback<List<WalkProposal>> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(buildMockProposals());
        });
    }

    @Override
    public void acceptProposal(String proposalId, DomainCallback<WalkSession> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(new WalkSession(
                    "session-new-" + System.currentTimeMillis(),
                    proposalId,
                    "Linh Nguyễn",
                    null,
                    10.7769, 106.7009,
                    "2026-03-29T17:00:00Z",
                    WalkSession.Status.PENDING_MEET));
        });
    }

    @Override
    public void passProposal(String proposalId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(null);
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
