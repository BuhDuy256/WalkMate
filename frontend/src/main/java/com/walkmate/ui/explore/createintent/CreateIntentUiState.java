package com.walkmate.ui.explore.createintent;

import com.walkmate.domain.social.UserSummary;
import com.walkmate.domain.walkintent.WalkIntent;

import java.util.Collections;
import java.util.List;

/**
 * Immutable state for the embedded Create Intent form inside ExploreFragment.
 */
public class CreateIntentUiState {

    private final boolean isLoading;
    private final String error;
    private final WalkIntent submittedIntent; // non-null signals successful creation

    // Private walk fields
    private final boolean isPrivate;
    private final String invitedFriendId;
    private final String invitedFriendName;
    private final List<UserSummary> friendList;
    private final boolean isFriendListLoading;
    private final String privateIntentError;

    // Onboarding gate signal
    private final boolean onboardingRequired;

    private CreateIntentUiState(boolean isLoading, String error, WalkIntent submittedIntent,
                                boolean isPrivate, String invitedFriendId, String invitedFriendName,
                                List<UserSummary> friendList, boolean isFriendListLoading,
                                String privateIntentError, boolean onboardingRequired) {
        this.isLoading            = isLoading;
        this.error                = error;
        this.submittedIntent      = submittedIntent;
        this.isPrivate            = isPrivate;
        this.invitedFriendId      = invitedFriendId;
        this.invitedFriendName    = invitedFriendName;
        this.friendList           = friendList != null ? friendList : Collections.emptyList();
        this.isFriendListLoading  = isFriendListLoading;
        this.privateIntentError   = privateIntentError;
        this.onboardingRequired   = onboardingRequired;
    }

    public static CreateIntentUiState initial() {
        return new CreateIntentUiState(false, null, null,
                false, null, null, Collections.emptyList(), false, null, false);
    }

    // ── Copy helpers ──────────────────────────────────────────────────────────

    public CreateIntentUiState withLoading(boolean loading) {
        return new CreateIntentUiState(loading, error, submittedIntent,
                isPrivate, invitedFriendId, invitedFriendName,
                friendList, isFriendListLoading, privateIntentError, onboardingRequired);
    }

    public CreateIntentUiState withError(String newError) {
        return new CreateIntentUiState(false, newError, submittedIntent,
                isPrivate, invitedFriendId, invitedFriendName,
                friendList, isFriendListLoading, privateIntentError, false);
    }

    public CreateIntentUiState withSubmittedIntent(WalkIntent intent) {
        return new CreateIntentUiState(false, null, intent,
                isPrivate, invitedFriendId, invitedFriendName,
                friendList, isFriendListLoading, privateIntentError, false);
    }

    public CreateIntentUiState withPrivate(boolean priv) {
        String newFriendId   = priv ? invitedFriendId   : null;
        String newFriendName = priv ? invitedFriendName : null;
        return new CreateIntentUiState(isLoading, error, submittedIntent,
                priv, newFriendId, newFriendName,
                friendList, isFriendListLoading, privateIntentError, onboardingRequired);
    }

    public CreateIntentUiState withFriend(String friendId, String friendName) {
        return new CreateIntentUiState(isLoading, error, submittedIntent,
                isPrivate, friendId, friendName,
                friendList, isFriendListLoading, privateIntentError, onboardingRequired);
    }

    public CreateIntentUiState withFriendList(List<UserSummary> list) {
        return new CreateIntentUiState(isLoading, error, submittedIntent,
                isPrivate, invitedFriendId, invitedFriendName,
                list, false, privateIntentError, onboardingRequired);
    }

    public CreateIntentUiState withFriendListLoading(boolean loading) {
        return new CreateIntentUiState(isLoading, error, submittedIntent,
                isPrivate, invitedFriendId, invitedFriendName,
                friendList, loading, privateIntentError, onboardingRequired);
    }

    public CreateIntentUiState withPrivateIntentError(String msg) {
        return new CreateIntentUiState(isLoading, error, submittedIntent,
                isPrivate, invitedFriendId, invitedFriendName,
                friendList, isFriendListLoading, msg, onboardingRequired);
    }

    public CreateIntentUiState withOnboardingRequired(boolean required) {
        return new CreateIntentUiState(false, null, null,
                isPrivate, invitedFriendId, invitedFriendName,
                friendList, isFriendListLoading, privateIntentError, required);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()                          { return isLoading; }
    public String getError()                            { return error; }
    public WalkIntent getSubmittedIntent()              { return submittedIntent; }
    public boolean isPrivate()                          { return isPrivate; }
    public String getInvitedFriendId()                  { return invitedFriendId; }
    public String getInvitedFriendName()                { return invitedFriendName; }
    public List<UserSummary> getFriendList()            { return friendList; }
    public boolean isFriendListLoading()                { return isFriendListLoading; }
    public String getPrivateIntentError()               { return privateIntentError; }
    public boolean isOnboardingRequired()               { return onboardingRequired; }
}
