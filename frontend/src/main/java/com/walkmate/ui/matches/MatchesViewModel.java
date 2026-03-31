package com.walkmate.ui.matches;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single Source of Truth for the Matches tab.
 * Owns all three repositories and aggregates their results into one MatchesUiState.
 * Sub-fragments (Finding, Proposal, Session) access this VM via
 * ViewModelProvider(requireParentFragment()) to share the same instance.
 */
public class MatchesViewModel extends ViewModel {

    private final MutableLiveData<MatchesUiState> uiState = new MutableLiveData<>(MatchesUiState.initial());

    private final WalkIntentRepository intentRepository;
    private final WalkProposalRepository proposalRepository;
    private final WalkSessionRepository sessionRepository;

    public MatchesViewModel(WalkIntentRepository intentRepository,
                            WalkProposalRepository proposalRepository,
                            WalkSessionRepository sessionRepository) {
        this.intentRepository = intentRepository;
        this.proposalRepository = proposalRepository;
        this.sessionRepository = sessionRepository;
    }

    public LiveData<MatchesUiState> getUiState() {
        return uiState;
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    /**
     * Fires all three repository fetches in parallel via their own executors.
     * Posts a loading state immediately, then a combined result state once all
     * three callbacks have returned (success or error).
     */
    public void loadAll() {
        uiState.postValue(new MatchesUiState(true,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null));

        AtomicInteger pending = new AtomicInteger(3);
        AtomicReference<List<WalkIntent>> intentsRef =
                new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<WalkProposal>> proposalsRef =
                new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<WalkSession>> sessionsRef =
                new AtomicReference<>(Collections.emptyList());
        AtomicReference<String> firstError = new AtomicReference<>(null);

        Runnable onOneDone = () -> {
            if (pending.decrementAndGet() == 0) {
                uiState.postValue(new MatchesUiState(
                        false,
                        intentsRef.get(),
                        proposalsRef.get(),
                        sessionsRef.get(),
                        firstError.get()));
            }
        };

        intentRepository.listActiveIntents(new DomainCallback<List<WalkIntent>>() {
            @Override public void onSuccess(List<WalkIntent> result) {
                intentsRef.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                firstError.compareAndSet(null, error.getMessage());
                onOneDone.run();
            }
        });

        proposalRepository.getProposals(new DomainCallback<List<WalkProposal>>() {
            @Override public void onSuccess(List<WalkProposal> result) {
                proposalsRef.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                firstError.compareAndSet(null, error.getMessage());
                onOneDone.run();
            }
        });

        sessionRepository.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override public void onSuccess(List<WalkSession> result) {
                sessionsRef.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                firstError.compareAndSet(null, error.getMessage());
                onOneDone.run();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Intent actions
    // -------------------------------------------------------------------------

    /**
     * Cancels an active intent. On success, removes it from the list and
     * posts the updated state so FindingFragment re-renders without it.
     */
    public void cancelIntent(String intentId) {
        intentRepository.cancelIntent(intentId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                MatchesUiState current = uiState.getValue();
                if (current == null) return;
                List<WalkIntent> updated = new ArrayList<>();
                for (WalkIntent intent : current.getActiveIntents()) {
                    if (!intent.getId().equals(intentId)) {
                        updated.add(intent);
                    }
                }
                uiState.postValue(new MatchesUiState(
                        false,
                        updated,
                        current.getProposals(),
                        current.getActiveSessions(),
                        null));
            }

            @Override
            public void onError(Exception error) {
                MatchesUiState current = uiState.getValue();
                if (current == null) return;
                uiState.postValue(new MatchesUiState(
                        false,
                        current.getActiveIntents(),
                        current.getProposals(),
                        current.getActiveSessions(),
                        error.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Proposal actions
    // -------------------------------------------------------------------------

    /**
     * Optimistic pass: immediately removes the proposal from the list and posts
     * the updated state — no full reload needed since no new entity is created.
     */
    public void passProposal(String proposalId) {
        proposalRepository.passProposal(proposalId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                MatchesUiState current = uiState.getValue();
                if (current == null) return;
                List<WalkProposal> updated = new ArrayList<>();
                for (WalkProposal p : current.getProposals()) {
                    if (!p.getProposalId().equals(proposalId)) updated.add(p);
                }
                uiState.postValue(new MatchesUiState(
                        false,
                        current.getActiveIntents(),
                        updated,
                        current.getActiveSessions(),
                        null));
            }

            @Override
            public void onError(Exception error) {
                MatchesUiState current = uiState.getValue();
                if (current == null) return;
                uiState.postValue(new MatchesUiState(
                        false,
                        current.getActiveIntents(),
                        current.getProposals(),
                        current.getActiveSessions(),
                        error.getMessage()));
            }
        });
    }

    /**
     * Full refresh on accept: the backend atomically removes the proposal and
     * creates a new WalkSession, so a full loadAll() gives the most consistent view.
     */
    public void acceptProposal(String proposalId) {
        proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkSession>() {
            @Override
            public void onSuccess(WalkSession result) {
                loadAll();
            }

            @Override
            public void onError(Exception error) {
                MatchesUiState current = uiState.getValue();
                if (current == null) return;
                uiState.postValue(new MatchesUiState(
                        false,
                        current.getActiveIntents(),
                        current.getProposals(),
                        current.getActiveSessions(),
                        error.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Session actions
    // -------------------------------------------------------------------------

    /**
     * Full refresh on cancel: cancelling a session may re-open the WalkIntent
     * (returning it to the Finding sub-tab), so a full loadAll() gives the most
     * consistent view of all three sub-tabs.
     */
    public void cancelSession(String sessionId, String reason) {
        sessionRepository.cancelSession(sessionId, reason, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadAll();
            }

            @Override
            public void onError(Exception error) {
                MatchesUiState current = uiState.getValue();
                if (current == null) return;
                uiState.postValue(new MatchesUiState(
                        false,
                        current.getActiveIntents(),
                        current.getProposals(),
                        current.getActiveSessions(),
                        error.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    public void consumeError() {
        MatchesUiState current = uiState.getValue();
        if (current == null || current.getError() == null) return;
        uiState.postValue(new MatchesUiState(
                current.isLoading(),
                current.getActiveIntents(),
                current.getProposals(),
                current.getActiveSessions(),
                null));
    }
}
