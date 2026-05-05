package com.walkmate.ui.explore.createintent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ValidationErrorParser;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;

import java.util.List;
import java.util.Map;

/**
 * Owns the Create Intent form submission logic, including private walk configuration.
 */
public class CreateIntentViewModel extends ViewModel {

    private static final String VALIDATION_ERROR_PREFIX         = "VALIDATION_ERROR|";
    private static final String PROFILE_INCOMPLETE_FOR_MATCHING = "PROFILE_INCOMPLETE_FOR_MATCHING";
    private static final String PRIVATE_INTENT_FRIEND_REQUIRED  =
            "A friend must be selected for a private walk.";

    private final WalkIntentRepository intentRepository;
    private final SocialRepository     socialRepository;

    private final MutableLiveData<CreateIntentUiState> uiState =
            new MutableLiveData<>(CreateIntentUiState.initial());

    public CreateIntentViewModel(WalkIntentRepository intentRepository,
                                 SocialRepository socialRepository) {
        this.intentRepository = intentRepository;
        this.socialRepository = socialRepository;
    }

    public LiveData<CreateIntentUiState> getUiState() {
        return uiState;
    }

    // ── Private walk actions ──────────────────────────────────────────────────

    public void togglePrivate() {
        CreateIntentUiState s = current();
        boolean nowPrivate = !s.isPrivate();
        post(s.withPrivate(nowPrivate));
        if (nowPrivate && current().getFriendList().isEmpty()) {
            loadFriends();
        }
    }

    public void selectFriend(String userId, String fullName) {
        post(current().withFriend(userId, fullName).withPrivateIntentError(null));
    }

    /**
     * Called when ExploreFragment is opened from FriendsFragment with a friend pre-selected.
     * Enables private mode and pins the friend only if no friend has been chosen yet,
     * so a manual selection or a config-change re-entry never overwrites the user's choice.
     */
    public void preSelectFriend(String userId, String fullName) {
        CreateIntentUiState s = current();
        if (s.getInvitedFriendId() != null) return;
        CreateIntentUiState updated = s.withPrivate(true).withFriend(userId, fullName).withPrivateIntentError(null);
        post(updated);
        if (updated.getFriendList().isEmpty()) {
            loadFriends();
        }
    }

    public void loadFriends() {
        post(current().withFriendListLoading(true));
        socialRepository.getFriends(new DomainCallback<List<UserSummary>>() {
            @Override
            public void onSuccess(List<UserSummary> friends) {
                uiState.postValue(current().withFriendList(friends));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(current().withFriendListLoading(false));
            }
        });
    }

    // ── Submission ────────────────────────────────────────────────────────────

    public void submit(String hotspotId, String date, float timeStart, float timeEnd,
                       int ageMin, int ageMax, String preferredGender, java.util.List<String> tags,
                       boolean isPrivate, String invitedFriendId) {
        if (isPrivate && (invitedFriendId == null || invitedFriendId.isEmpty())) {
            post(current().withPrivateIntentError(PRIVATE_INTENT_FRIEND_REQUIRED));
            return;
        }

        post(current().withLoading(true));

        intentRepository.createIntent(hotspotId, date, timeStart, timeEnd, ageMin, ageMax,
                preferredGender, tags, isPrivate, invitedFriendId, null,
                new DomainCallback<WalkIntent>() {
                    @Override
                    public void onSuccess(WalkIntent intent) {
                        uiState.postValue(current().withSubmittedIntent(intent));
                    }

                    @Override
                    public void onError(Exception error) {
                        String msg = error.getMessage();
                        if (PROFILE_INCOMPLETE_FOR_MATCHING.equals(msg)) {
                            uiState.postValue(current().withLoading(false).withOnboardingRequired(true));
                        } else if (msg != null && msg.startsWith(VALIDATION_ERROR_PREFIX)) {
                            String raw = msg.substring(VALIDATION_ERROR_PREFIX.length());
                            Map<String, String> fieldErrors = ValidationErrorParser.parse(raw);
                            String firstError = fieldErrors.isEmpty()
                                    ? raw : fieldErrors.values().iterator().next();
                            uiState.postValue(current().withLoading(false).withError(firstError));
                        } else {
                            uiState.postValue(current().withLoading(false).withError(msg));
                        }
                    }
                });
    }

    public void consumeSubmission() {
        post(CreateIntentUiState.initial());
    }

    public void consumeError() {
        post(current().withError(null));
    }

    public void consumeOnboardingRequired() {
        post(current().withOnboardingRequired(false));
    }

    public void consumePrivateIntentError() {
        post(current().withPrivateIntentError(null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CreateIntentUiState current() {
        CreateIntentUiState s = uiState.getValue();
        return s != null ? s : CreateIntentUiState.initial();
    }

    private void post(CreateIntentUiState state) {
        uiState.setValue(state);
    }
}
