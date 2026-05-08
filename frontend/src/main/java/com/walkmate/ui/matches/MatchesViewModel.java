package com.walkmate.ui.matches;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.ArrayList;
import java.util.List;

public class MatchesViewModel extends ViewModel {

    private final MutableLiveData<MatchesUiState> uiState =
            new MutableLiveData<>(MatchesUiState.initial());

    private final MutableLiveData<Integer> scrollToTabEvent = new MutableLiveData<>(null);

    private final WalkIntentRepository intentRepository;
    private final WalkProposalRepository proposalRepository;
    private final WalkSessionRepository sessionRepository;

    private int lastViewedSubTab = MatchesPagerAdapter.TAB_FINDING;

    public int getLastViewedSubTab() { return lastViewedSubTab; }
    public void setLastViewedSubTab(int tabIndex) { lastViewedSubTab = tabIndex; }

    public MatchesViewModel(WalkIntentRepository intentRepository,
                            WalkProposalRepository proposalRepository,
                            WalkSessionRepository sessionRepository) {
        this.intentRepository = intentRepository;
        this.proposalRepository = proposalRepository;
        this.sessionRepository = sessionRepository;
    }

    public LiveData<MatchesUiState> getUiState() { return uiState; }

    public LiveData<Integer> getScrollToTabEvent() { return scrollToTabEvent; }

    public void consumeScrollToTab() { scrollToTabEvent.postValue(null); }

    public void navigateToTab(int tabIndex) { scrollToTabEvent.postValue(tabIndex); }

    // ── Celebration event (GAP-19) ────────────────────────────────────────────

    private final MutableLiveData<Boolean> celebrationEvent = new MutableLiveData<>(null);

    public LiveData<Boolean> getCelebrationEvent() { return celebrationEvent; }

    public void consumeCelebration() { celebrationEvent.postValue(null); }

    // ── Activation result event ───────────────────────────────────────────────

    private final MutableLiveData<ActivationResult> activationResultEvent =
            new MutableLiveData<>(null);

    public LiveData<ActivationResult> getActivationResultEvent() { return activationResultEvent; }

    public void consumeActivationResult() { activationResultEvent.postValue(null); }

    public static class ActivationResult {
        public final WalkSession session;
        public final String errorMessage;
        public final boolean isWindowClosed;
        ActivationResult(WalkSession s, String msg, boolean windowClosed) {
            session = s; errorMessage = msg; isWindowClosed = windowClosed;
        }
    }

    // ── Per-tab data loading ──────────────────────────────────────────────────

    public void loadIntents() {
        setLoading();
        intentRepository.listActiveIntents(new DomainCallback<List<WalkIntent>>() {
            @Override public void onSuccess(List<WalkIntent> result) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, result, c.getProposals(), c.getActiveSessions(), null));
            }
            @Override public void onError(Exception error) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), c.getProposals(),
                        c.getActiveSessions(), ErrorMessageResolver.resolve(error.getMessage())));
            }
        });
    }

    public void loadProposals() {
        setLoading();
        proposalRepository.getProposals(new DomainCallback<List<WalkProposal>>() {
            @Override public void onSuccess(List<WalkProposal> result) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), result, c.getActiveSessions(), null));
            }
            @Override public void onError(Exception error) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), c.getProposals(),
                        c.getActiveSessions(), ErrorMessageResolver.resolve(error.getMessage())));
            }
        });
    }

    public void loadSessions() {
        setLoading();
        sessionRepository.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override public void onSuccess(List<WalkSession> result) {
                // Only keep sessions the caller can act on — keeps badge count and list in sync.
                List<WalkSession> visible = new ArrayList<>();
                if (result != null) {
                    for (WalkSession s : result) {
                        WalkSession.Status cs = s.getCallerStatus();
                        if (cs == WalkSession.Status.PENDING || cs == WalkSession.Status.ACTIVE) {
                            visible.add(s);
                        }
                    }
                }
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), c.getProposals(), visible, null));
            }
            @Override public void onError(Exception error) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), c.getProposals(),
                        c.getActiveSessions(), ErrorMessageResolver.resolve(error.getMessage())));
            }
        });
    }

    // ── Intent actions ────────────────────────────────────────────────────────

    public void cancelIntent(String intentId) {
        intentRepository.cancelIntent(intentId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                MatchesUiState c = safeGetState();
                List<WalkIntent> updated = new ArrayList<>();
                for (WalkIntent intent : c.getActiveIntents()) {
                    if (!intent.getId().equals(intentId)) updated.add(intent);
                }
                uiState.postValue(new MatchesUiState(
                        false, updated, c.getProposals(), c.getActiveSessions(), null));
            }
            @Override public void onError(Exception error) { postError(ErrorMessageResolver.resolve(error.getMessage())); }
        });
    }

    // ── Proposal actions ──────────────────────────────────────────────────────

    public void passProposal(String proposalId, boolean isPrivateInvite) {
        proposalRepository.passProposal(proposalId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                MatchesUiState c = safeGetState();
                List<WalkProposal> updated = new ArrayList<>();
                for (WalkProposal p : c.getProposals()) {
                    if (!p.getProposalId().equals(proposalId)) updated.add(p);
                }
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), updated, c.getActiveSessions(), null));
                if (!isPrivateInvite) {
                    // Public proposal: intent reverts to OPEN — refresh Finding data and go there
                    loadIntents();
                    scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_FINDING);
                }
            }
            @Override public void onError(Exception error) { postError(ErrorMessageResolver.resolve(error.getMessage())); }
        });
    }

    public void cancelProposal(String proposalId) {
        proposalRepository.cancelProposal(proposalId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                loadIntents();
                loadProposals();
            }
            @Override public void onError(Exception error) { postError(ErrorMessageResolver.resolve(error.getMessage())); }
        });
    }

    public void acceptProposal(String proposalId) {
        proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkProposal>() {
            @Override public void onSuccess(WalkProposal result) {
                if (result.isConfirmed()) {
                    // Case B: both accepted — fire celebration then navigate to Session tab
                    celebrationEvent.postValue(true);
                    loadSessions();
                    scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION);
                } else {
                    // Case A: I accepted, partner has not — show waiting overlay in-place
                    updateProposalInPlace(result);
                }
            }
            @Override public void onError(Exception error) {
                String code = error.getMessage() != null ? error.getMessage() : "";
                // Refresh proposal list for codes where server-side state has changed
                switch (code) {
                    case "PROPOSAL_CONCURRENT_MODIFICATION":
                    case "PROPOSAL_INTENT_NO_LONGER_OPEN":
                    case "PROPOSAL_ALREADY_TERMINAL":
                    case "PROPOSAL_NOT_FOUND":
                        loadProposals();
                        break;
                }
                postError(ErrorMessageResolver.resolve(code));
            }
        });
    }

    private void updateProposalInPlace(WalkProposal updated) {
        MatchesUiState c = safeGetState();
        List<WalkProposal> updatedList = new ArrayList<>();
        for (WalkProposal p : c.getProposals()) {
            updatedList.add(p.getProposalId().equals(updated.getProposalId()) ? updated : p);
        }
        uiState.postValue(new MatchesUiState(
                false, c.getActiveIntents(), updatedList, c.getActiveSessions(), null));
    }

    // ── Session actions ───────────────────────────────────────────────────────

    public void cancelSession(String sessionId, String reason) {
        sessionRepository.cancelSession(sessionId, reason, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                // Cancelling a session may re-open the parent WalkIntent
                loadSessions();
                loadIntents();
            }
            @Override public void onError(Exception error) {
                String msg = error.getMessage() != null ? error.getMessage() : "";
                if (msg.startsWith("VALIDATION_ERROR|")) {
                    postError("Please provide a reason for cancelling.");
                } else {
                    postError(ErrorMessageResolver.resolve(msg));
                }
            }
        });
    }

    public void activateSession(String sessionId) {
        sessionRepository.activateSession(sessionId, new DomainCallback<WalkSession>() {
            @Override public void onSuccess(WalkSession result) {
                activationResultEvent.postValue(new ActivationResult(result, null, false));
                loadSessions();
            }
            @Override public void onError(Exception e) {
                String code = e.getMessage() != null ? e.getMessage() : "";
                boolean isWindowClosed = "SESSION_ACTIVATION_WINDOW_CLOSED".equals(code);
                activationResultEvent.postValue(new ActivationResult(
                        null, ErrorMessageResolver.resolve(code), isWindowClosed));
            }
        });
    }

    // ── Error handling ────────────────────────────────────────────────────────

    private void postError(String message) {
        MatchesUiState c = safeGetState();
        uiState.postValue(new MatchesUiState(
                false, c.getActiveIntents(), c.getProposals(), c.getActiveSessions(), message));
    }

    public void consumeError() {
        MatchesUiState c = safeGetState();
        if (c.getError() == null) return;
        uiState.postValue(new MatchesUiState(
                c.isLoading(), c.getActiveIntents(), c.getProposals(), c.getActiveSessions(), null));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setLoading() {
        MatchesUiState c = safeGetState();
        uiState.postValue(new MatchesUiState(
                true, c.getActiveIntents(), c.getProposals(), c.getActiveSessions(), null));
    }

    private MatchesUiState safeGetState() {
        MatchesUiState s = uiState.getValue();
        return s != null ? s : MatchesUiState.initial();
    }
}
