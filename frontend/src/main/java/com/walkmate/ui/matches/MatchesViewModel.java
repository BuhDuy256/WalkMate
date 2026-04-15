package com.walkmate.ui.matches;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.ui.matches.MatchesPagerAdapter;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final UserProfileRepository userProfileRepository;

    /** In-memory cache of userId → UserProfile for partner name enrichment. */
    private final Map<String, UserProfile> profileCache = new HashMap<>();

    /** Tracks whether loadAll() has completed at least once. */
    private volatile boolean dataLoaded = false;

    /** Returns true if loadAll() has completed at least once (data is cached). */
    public boolean hasLoadedOnce() {
        return dataLoaded;
    }

    /**
     * Handler bound to the main thread.
     * Used to dispatch rebuildUiStateWithEnrichedProposals() onto the main thread,
     * guaranteeing that any preceding postValue() calls have already been dispatched
     * (i.e. mData is current) before getValue() is read inside the rebuild.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MatchesViewModel(WalkIntentRepository intentRepository,
                            WalkProposalRepository proposalRepository,
                            WalkSessionRepository sessionRepository,
                            UserProfileRepository userProfileRepository) {
        this.intentRepository = intentRepository;
        this.proposalRepository = proposalRepository;
        this.sessionRepository = sessionRepository;
        this.userProfileRepository = userProfileRepository;
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

    /** Navigates to the given tab index (e.g. TAB_PROPOSAL) by posting a scroll event. */
    public void navigateToTab(int tabIndex) {
        scrollToTabEvent.postValue(tabIndex);
    }

    // ── Celebration event (GAP-19) ────────────────────────────────────────────

    /** One-shot event: fires true when a proposal is double-accepted (Case B = CONFIRMED). */
    private final MutableLiveData<Boolean> celebrationEvent = new MutableLiveData<>(null);

    public LiveData<Boolean> getCelebrationEvent() { return celebrationEvent; }

    public void consumeCelebration() { celebrationEvent.postValue(null); }

    // ── Activation result event ───────────────────────────────────────────────

    private final MutableLiveData<ActivationResult> activationResultEvent = new MutableLiveData<>(null);

    public LiveData<ActivationResult> getActivationResultEvent() { return activationResultEvent; }

    public void consumeActivationResult() { activationResultEvent.postValue(null); }

    public static class ActivationResult {
        public final WalkSession session;
        public final String errorCode;
        ActivationResult(WalkSession s, String e) { session = s; errorCode = e; }
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
                dataLoaded = true;
                uiState.postValue(new MatchesUiState(
                        false,
                        intentsRef.get(),
                        proposalsRef.get(),
                        sessionsRef.get(),
                        errorMessage));
                if (onComplete != null) onComplete.run();
                enrichProposalPartnerNames(proposalsRef.get());
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
                postError(error.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Proposal actions
    // -------------------------------------------------------------------------

    /**
     * Passes (or declines) a proposal.
     * <ul>
     *   <li>Private invite: optimistic remove, stay on Proposal tab.</li>
     *   <li>Public proposal: reload all data and navigate to Finding tab so the
     *       re-opened intent is immediately visible.</li>
     * </ul>
     */
    public void passProposal(String proposalId, boolean isPrivateInvite) {
        proposalRepository.passProposal(proposalId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (isPrivateInvite) {
                    // Private invite declined — optimistic remove, stay on Proposal tab
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
                } else {
                    // Public proposal passed — intent reverts to OPEN, navigate to Finding tab
                    loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_FINDING));
                }
            }

            @Override
            public void onError(Exception error) {
                postError(error.getMessage());
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
                postError(error.getMessage());
            }
        });
    }

    /**
     * Accepts a proposal.
     * Case B (CONFIRMED + session_id non-null): reload all data and navigate to Session tab.
     * Case A (PENDING, only I accepted): update the proposal in-place so the waiting
     * overlay is shown without a full reload.
     */
    public void acceptProposal(String proposalId) {
        proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkProposal>() {
            @Override
            public void onSuccess(WalkProposal result) {
                if (result.isConfirmed()) {
                    // Case B: both accepted — fire celebration then navigate to Session tab
                    celebrationEvent.postValue(true);
                    loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION));
                } else {
                    // Case A: I accepted but partner has not — show waiting overlay in-place
                    updateProposalInPlace(result);
                }
            }

            @Override
            public void onError(Exception error) {
                // UC-20 / Invariant X-5: domain-level concurrent-modification errors.
                // error.getMessage() returns the error.code string from the API response.
                String code = error.getMessage() != null ? error.getMessage() : "";
                switch (code) {
                    case "PROPOSAL_CONCURRENT_MODIFICATION":
                        postError("A conflict occurred. Please refresh and try again.");
                        loadAll();
                        break;
                    case "PROPOSAL_INTENT_NO_LONGER_OPEN":
                        postError("Could not confirm — one of the intents is no longer available. The proposal has been cancelled.");
                        loadAll();
                        break;
                    case "PROPOSAL_ALREADY_TERMINAL":
                        postError("This proposal is no longer active.");
                        loadAll();
                        break;
                    case "PROPOSAL_NOT_PARTICIPANT":
                        postError("Permission denied.");
                        break;
                    case "PROPOSAL_NOT_FOUND":
                        postError("Proposal not found.");
                        loadAll();
                        break;
                    default:
                        postError(code);
                        break;
                }
            }
        });
    }

    /** Replaces a single proposal entry in the current UiState without a full reload. */
    private void updateProposalInPlace(WalkProposal updated) {
        MatchesUiState current = uiState.getValue();
        if (current == null) return;
        List<WalkProposal> updatedList = new ArrayList<>();
        for (WalkProposal p : current.getProposals()) {
            updatedList.add(p.getProposalId().equals(updated.getProposalId()) ? updated : p);
        }
        uiState.postValue(new MatchesUiState(
                false,
                current.getActiveIntents(),
                updatedList,
                current.getActiveSessions(),
                null));
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
                String code = error.getMessage() != null ? error.getMessage() : "";
                if (code.startsWith("VALIDATION_ERROR")) {
                    postError("Please provide a reason.");
                } else {
                    postError(code);
                }
            }
        });
    }

    /**
     * Activates the caller's side of a session.
     * Case B: both activated (status ACTIVE) — posts result so SessionFragment can launch TrackingScreenActivity.
     * Case A: only caller activated — posts result so SessionFragment can show waiting UI and start polling.
     * Error: SESSION_ACTIVATION_WINDOW_CLOSED or other error — posts result with errorCode.
     */
    public void activateSession(String sessionId) {
        sessionRepository.activateSession(sessionId, new DomainCallback<WalkSession>() {
            @Override
            public void onSuccess(WalkSession result) {
                activationResultEvent.postValue(new ActivationResult(result, null));
                loadAll(null);
            }

            @Override
            public void onError(Exception e) {
                activationResultEvent.postValue(new ActivationResult(null, e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    /** Posts an error message into UiState without changing any list data. */
    private void postError(String message) {
        MatchesUiState current = uiState.getValue();
        if (current == null) return;
        uiState.postValue(new MatchesUiState(
                false,
                current.getActiveIntents(),
                current.getProposals(),
                current.getActiveSessions(),
                message));
    }

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
                enrichProposalPartnerNames(newProposals.get());
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

    // -------------------------------------------------------------------------
    // Partner name enrichment (gap 4.5)
    // -------------------------------------------------------------------------

    /**
     * For each proposal, resolves the partner's real name from cache or API.
     * Each successful fetch updates {@code profileCache} and rebuilds the
     * proposals list in the current UiState with the enriched name.
     */
    private void enrichProposalPartnerNames(List<WalkProposal> proposals) {
        for (WalkProposal p : proposals) {
            String uid = p.getMatchedUserId();
            if (profileCache.containsKey(uid)) {
                // Post to main thread instead of calling synchronously.
                // rebuildUiStateWithEnrichedProposals() calls getValue(), which on a
                // background thread returns mData (the last value committed to the main
                // thread). When profiles are cached, this method is called on the same
                // executor thread that just called uiState.postValue(data) — but that
                // postValue only queues the update; mData still holds the old loading
                // state. A synchronous call here would therefore read the loading state
                // (empty proposals) and overwrite the pending data postValue via another
                // postValue, permanently clearing proposals from the UI.
                // By posting to the main thread we always run AFTER the data postValue
                // has been dispatched, so getValue() is guaranteed to return fresh data.
                mainHandler.post(this::rebuildUiStateWithEnrichedProposals);
            } else {
                userProfileRepository.getProfile(uid, new DomainCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        profileCache.put(uid, profile);
                        // Also post to main thread for the same reason: the profile network
                        // call is fast enough on some devices that mData may not yet reflect
                        // the latest postValue() when this callback fires.
                        mainHandler.post(MatchesViewModel.this::rebuildUiStateWithEnrichedProposals);
                    }

                    @Override
                    public void onError(Exception e) {
                        // Fail silently — adapter falls back to userId placeholder
                    }
                });
            }
        }
    }

    /**
     * Reads the current UiState proposals, replaces each entry whose userId is
     * in {@code profileCache} with a copy carrying the real display name, then
     * re-posts the state.
     */
    private void rebuildUiStateWithEnrichedProposals() {
        MatchesUiState current = uiState.getValue();
        if (current == null) return;
        List<WalkProposal> enriched = new ArrayList<>();
        for (WalkProposal p : current.getProposals()) {
            UserProfile cached = profileCache.get(p.getMatchedUserId());
            enriched.add(cached != null ? p.withMatchedUserName(cached.getFullName()) : p);
        }
        uiState.postValue(new MatchesUiState(
                current.isLoading(),
                current.getActiveIntents(),
                enriched,
                current.getActiveSessions(),
                current.getError()));
    }
}
