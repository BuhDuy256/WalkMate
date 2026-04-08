package com.walkmate.ui.matches;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.ui.matches.MatchesPagerAdapter;
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

    /**
     * Phase 5 — one-shot navigation signal.
     * Holds a tab index to scroll to after an action (e.g. accept proposal → Session tab).
     * Observers must call consumeScrollToTab() after handling to prevent re-delivery on rotation.
     */
    private final MutableLiveData<Integer> scrollToTabEvent = new MutableLiveData<>(null);

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

    public LiveData<Integer> getScrollToTabEvent() {
        return scrollToTabEvent;
    }

    /** Clears the scroll-to-tab signal after the Fragment has handled it. */
    public void consumeScrollToTab() {
        scrollToTabEvent.postValue(null);
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    /**
     * Fires all three repository fetches in parallel.
     * Posts a loading state immediately, then a combined result state once all
     * three callbacks have returned (success or error).
     */
    public void loadAll() {
        loadAll(null);
    }

    /**
     * Same as {@link #loadAll()} but invokes {@code onComplete} after the
     * combined state has been posted. Used by {@link #acceptProposal} to delay
     * the scroll-to-Session signal until the data is actually ready.
     */
    public void loadAll(Runnable onComplete) {
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
        // Aggregate all errors instead of silently dropping all but the first.
        AtomicReference<List<String>> errors = new AtomicReference<>(new ArrayList<>());

        Runnable onOneDone = () -> {
            if (pending.decrementAndGet() == 0) {
                List<String> errorList = errors.get();
                String errorMessage = errorList.isEmpty()
                        ? null : String.join("; ", errorList);
                uiState.postValue(new MatchesUiState(
                        false,
                        intentsRef.get(),
                        proposalsRef.get(),
                        sessionsRef.get(),
                        errorMessage));
                if (onComplete != null) onComplete.run();
            }
        };

        intentRepository.listActiveIntents(new DomainCallback<List<WalkIntent>>() {
            @Override public void onSuccess(List<WalkIntent> result) {
                intentsRef.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
                onOneDone.run();
            }
        });

        proposalRepository.getProposals(new DomainCallback<List<WalkProposal>>() {
            @Override public void onSuccess(List<WalkProposal> result) {
                proposalsRef.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
                onOneDone.run();
            }
        });

        sessionRepository.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override public void onSuccess(List<WalkSession> result) {
                sessionsRef.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
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
     * Hard cancel: removes the proposal and closes its parent intent on the backend.
     * Only Intents and Proposals are affected — Sessions are unchanged.
     */
    public void cancelProposal(String proposalId) {
        proposalRepository.cancelProposal(proposalId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                reloadIntentsAndProposals();
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
     * creates a new WalkSession. The scroll-to-Session signal is emitted only
     * after all data has been refreshed to avoid showing an empty Session tab.
     */
    public void acceptProposal(String proposalId) {
        proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkProposal>() {
            @Override
            public void onSuccess(WalkProposal result) {
                // Pass the scroll event as a callback so it fires only after
                // loadAll() has posted the updated state — not before.
                loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION));
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
     * Targeted refresh on cancel: cancelling a session may re-open the WalkIntent
     * (returning it to the Finding sub-tab), so Sessions and Intents are reloaded.
     * Proposals are unaffected and not refetched.
     */
    public void cancelSession(String sessionId, String reason) {
        sessionRepository.cancelSession(sessionId, reason, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                reloadSessionsAndIntents();
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

    // -------------------------------------------------------------------------
    // Targeted reload helpers — avoid reloading APIs that are unaffected
    // -------------------------------------------------------------------------

    /**
     * Reloads only Intents and Proposals in parallel; keeps the current Sessions.
     * Used after cancelProposal (Sessions are never changed by that action).
     */
    private void reloadIntentsAndProposals() {
        AtomicInteger pending = new AtomicInteger(2);
        AtomicReference<List<WalkIntent>> newIntents = new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<WalkProposal>> newProposals = new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<String>> errors = new AtomicReference<>(new ArrayList<>());

        Runnable onOneDone = () -> {
            if (pending.decrementAndGet() == 0) {
                MatchesUiState current = uiState.getValue();
                List<WalkSession> sessions = current != null
                        ? current.getActiveSessions() : Collections.emptyList();
                List<String> errorList = errors.get();
                String errorMessage = errorList.isEmpty()
                        ? null : String.join("; ", errorList);
                uiState.postValue(new MatchesUiState(
                        false, newIntents.get(), newProposals.get(), sessions, errorMessage));
            }
        };

        intentRepository.listActiveIntents(new DomainCallback<List<WalkIntent>>() {
            @Override public void onSuccess(List<WalkIntent> result) {
                newIntents.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
                onOneDone.run();
            }
        });

        proposalRepository.getProposals(new DomainCallback<List<WalkProposal>>() {
            @Override public void onSuccess(List<WalkProposal> result) {
                newProposals.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
                onOneDone.run();
            }
        });
    }

    /**
     * Reloads only Sessions and Intents in parallel; keeps the current Proposals.
     * Used after cancelSession (cancelling a session may re-open its parent Intent,
     * but Proposals are never affected by that action).
     */
    private void reloadSessionsAndIntents() {
        AtomicInteger pending = new AtomicInteger(2);
        AtomicReference<List<WalkIntent>> newIntents = new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<WalkSession>> newSessions = new AtomicReference<>(Collections.emptyList());
        AtomicReference<List<String>> errors = new AtomicReference<>(new ArrayList<>());

        Runnable onOneDone = () -> {
            if (pending.decrementAndGet() == 0) {
                MatchesUiState current = uiState.getValue();
                List<WalkProposal> proposals = current != null
                        ? current.getProposals() : Collections.emptyList();
                List<String> errorList = errors.get();
                String errorMessage = errorList.isEmpty()
                        ? null : String.join("; ", errorList);
                uiState.postValue(new MatchesUiState(
                        false, newIntents.get(), proposals, newSessions.get(), errorMessage));
            }
        };

        intentRepository.listActiveIntents(new DomainCallback<List<WalkIntent>>() {
            @Override public void onSuccess(List<WalkIntent> result) {
                newIntents.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
                onOneDone.run();
            }
        });

        sessionRepository.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override public void onSuccess(List<WalkSession> result) {
                newSessions.set(result);
                onOneDone.run();
            }
            @Override public void onError(Exception error) {
                synchronized (errors.get()) { errors.get().add(error.getMessage()); }
                onOneDone.run();
            }
        });
    }
}
