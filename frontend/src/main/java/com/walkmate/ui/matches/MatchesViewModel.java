package com.walkmate.ui.matches;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MatchesViewModel extends ViewModel {

    private final MutableLiveData<MatchesUiState> uiState =
            new MutableLiveData<>(MatchesUiState.initial());

    private final MutableLiveData<Integer> scrollToTabEvent = new MutableLiveData<>(null);

    private final WalkIntentRepository intentRepository;
    private final WalkProposalRepository proposalRepository;
    private final WalkSessionRepository sessionRepository;
    private final UserProfileRepository userProfileRepository;

    private final Map<String, UserProfile> profileCache = new HashMap<>();

    private int lastViewedSubTab = MatchesPagerAdapter.TAB_FINDING;

    public int getLastViewedSubTab() { return lastViewedSubTab; }
    public void setLastViewedSubTab(int tabIndex) { lastViewedSubTab = tabIndex; }

    public MatchesViewModel(WalkIntentRepository intentRepository,
                            WalkProposalRepository proposalRepository,
                            WalkSessionRepository sessionRepository,
                            UserProfileRepository userProfileRepository) {
        this.intentRepository = intentRepository;
        this.proposalRepository = proposalRepository;
        this.sessionRepository = sessionRepository;
        this.userProfileRepository = userProfileRepository;
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
        public final String errorCode;
        ActivationResult(WalkSession s, String e) { session = s; errorCode = e; }
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
                        c.getActiveSessions(), error.getMessage()));
            }
        });
    }

    /**
     * Fetches proposals then blocks on profile API calls before posting state,
     * so the UI never renders raw UUIDs as partner names.
     */
    public void loadProposals() {
        setLoading();
        proposalRepository.getProposals(new DomainCallback<List<WalkProposal>>() {
            @Override public void onSuccess(List<WalkProposal> result) {
                enrichAndPostProposals(result);
            }
            @Override public void onError(Exception error) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), c.getProposals(),
                        c.getActiveSessions(), error.getMessage()));
            }
        });
    }

    public void loadSessions() {
        setLoading();
        sessionRepository.getActiveSessions(new DomainCallback<List<WalkSession>>() {
            @Override public void onSuccess(List<WalkSession> result) {
                enrichAndPostSessions(result);
            }
            @Override public void onError(Exception error) {
                MatchesUiState c = safeGetState();
                uiState.postValue(new MatchesUiState(
                        false, c.getActiveIntents(), c.getProposals(),
                        c.getActiveSessions(), error.getMessage()));
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
            @Override public void onError(Exception error) { postError(error.getMessage()); }
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
            @Override public void onError(Exception error) { postError(error.getMessage()); }
        });
    }

    public void cancelProposal(String proposalId) {
        proposalRepository.cancelProposal(proposalId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                loadIntents();
                loadProposals();
            }
            @Override public void onError(Exception error) { postError(error.getMessage()); }
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
                switch (code) {
                    case "PROPOSAL_CONCURRENT_MODIFICATION":
                        postError("A conflict occurred. Please refresh and try again.");
                        loadProposals();
                        break;
                    case "PROPOSAL_INTENT_NO_LONGER_OPEN":
                        postError("Could not confirm — one of the intents is no longer available. The proposal has been cancelled.");
                        loadProposals();
                        break;
                    case "PROPOSAL_ALREADY_TERMINAL":
                        postError("This proposal is no longer active.");
                        loadProposals();
                        break;
                    case "PROPOSAL_NOT_PARTICIPANT":
                        postError("Permission denied.");
                        break;
                    case "PROPOSAL_NOT_FOUND":
                        postError("Proposal not found.");
                        loadProposals();
                        break;
                    default:
                        postError(code);
                        break;
                }
            }
        });
    }

    private void updateProposalInPlace(WalkProposal updated) {
        UserProfile cached = profileCache.get(updated.getMatchedUserId());
        WalkProposal enriched = cached != null
                ? updated.withMatchedUserName(cached.getFullName()) : updated;
        MatchesUiState c = safeGetState();
        List<WalkProposal> updatedList = new ArrayList<>();
        for (WalkProposal p : c.getProposals()) {
            updatedList.add(p.getProposalId().equals(enriched.getProposalId()) ? enriched : p);
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
                String code = error.getMessage() != null ? error.getMessage() : "";
                if (code.startsWith("VALIDATION_ERROR")) {
                    postError("Please provide a reason.");
                } else {
                    postError(code);
                }
            }
        });
    }

    public void activateSession(String sessionId) {
        sessionRepository.activateSession(sessionId, new DomainCallback<WalkSession>() {
            @Override public void onSuccess(WalkSession result) {
                activationResultEvent.postValue(new ActivationResult(result, null));
                loadSessions();
            }
            @Override public void onError(Exception e) {
                activationResultEvent.postValue(new ActivationResult(null, e.getMessage()));
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

    private void enrichAndPostProposals(List<WalkProposal> proposals) {
        List<String> uncachedIds = new ArrayList<>();
        for (WalkProposal p : proposals) {
            String uid = p.getMatchedUserId();
            if (uid != null && !profileCache.containsKey(uid) && !uncachedIds.contains(uid)) {
                uncachedIds.add(uid);
            }
        }
        if (uncachedIds.isEmpty()) {
            postEnrichedProposals(applyEnrichment(proposals));
            return;
        }
        AtomicInteger pending = new AtomicInteger(uncachedIds.size());
        for (String uid : uncachedIds) {
            userProfileRepository.getProfile(uid, new DomainCallback<UserProfile>() {
                @Override public void onSuccess(UserProfile profile) {
                    profileCache.put(uid, profile);
                    if (pending.decrementAndGet() == 0)
                        postEnrichedProposals(applyEnrichment(proposals));
                }
                @Override public void onError(Exception e) {
                    if (pending.decrementAndGet() == 0)
                        postEnrichedProposals(applyEnrichment(proposals));
                }
            });
        }
    }

    private List<WalkProposal> applyEnrichment(List<WalkProposal> proposals) {
        List<WalkProposal> enriched = new ArrayList<>(proposals.size());
        for (WalkProposal p : proposals) {
            UserProfile cached = profileCache.get(p.getMatchedUserId());
            enriched.add(cached != null ? p.withMatchedUserName(cached.getFullName()) : p);
        }
        return enriched;
    }

    private void postEnrichedProposals(List<WalkProposal> enriched) {
        MatchesUiState c = safeGetState();
        uiState.postValue(new MatchesUiState(
                false, c.getActiveIntents(), enriched, c.getActiveSessions(), null));
    }

    private void enrichAndPostSessions(List<WalkSession> sessions) {
        List<String> uncachedIds = new ArrayList<>();
        for (WalkSession s : sessions) {
            String uid = s.getPartnerId();
            if (uid != null && (s.getPartnerName() == null || s.getPartnerName().isEmpty())
                    && !profileCache.containsKey(uid) && !uncachedIds.contains(uid)) {
                uncachedIds.add(uid);
            }
        }
        if (uncachedIds.isEmpty()) {
            postEnrichedSessions(applySessionEnrichment(sessions));
            return;
        }
        AtomicInteger pending = new AtomicInteger(uncachedIds.size());
        for (String uid : uncachedIds) {
            userProfileRepository.getProfile(uid, new DomainCallback<UserProfile>() {
                @Override public void onSuccess(UserProfile profile) {
                    profileCache.put(uid, profile);
                    if (pending.decrementAndGet() == 0)
                        postEnrichedSessions(applySessionEnrichment(sessions));
                }
                @Override public void onError(Exception e) {
                    if (pending.decrementAndGet() == 0)
                        postEnrichedSessions(applySessionEnrichment(sessions));
                }
            });
        }
    }

    private List<WalkSession> applySessionEnrichment(List<WalkSession> sessions) {
        List<WalkSession> enriched = new ArrayList<>(sessions.size());
        for (WalkSession s : sessions) {
            if (s.getPartnerName() == null || s.getPartnerName().isEmpty()) {
                UserProfile cached = profileCache.get(s.getPartnerId());
                enriched.add(cached != null ? s.withPartnerName(cached.getFullName()) : s);
            } else {
                enriched.add(s);
            }
        }
        return enriched;
    }

    private void postEnrichedSessions(List<WalkSession> enriched) {
        MatchesUiState c = safeGetState();
        uiState.postValue(new MatchesUiState(
                false, c.getActiveIntents(), c.getProposals(), enriched, null));
    }
}
