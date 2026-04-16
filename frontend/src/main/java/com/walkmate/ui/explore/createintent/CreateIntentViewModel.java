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
 * Observed by ExploreFragment (not a sub-Fragment).
 */
public class CreateIntentViewModel extends ViewModel {

    private static final String VALIDATION_ERROR_PREFIX = "VALIDATION_ERROR|";
    private static final String PRIVATE_INTENT_FRIEND_REQUIRED =
            "A friend must be selected for a private walk.";

    private final WalkIntentRepository intentRepository;
    private final SocialRepository socialRepository;

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

    /** Toggles the private-walk switch and loads the friend list on first enable. */
    public void togglePrivate() {
        CreateIntentUiState s = current();
        boolean nowPrivate = !s.isPrivate();
        post(s.withPrivate(nowPrivate));
        if (nowPrivate && current().getFriendList().isEmpty()) {
            loadFriends();
        }
    }

    /** Called when the user picks a friend from the bottom sheet. */
    public void selectFriend(String userId, String fullName) {
        post(current().withFriend(userId, fullName).withPrivateIntentError(null));
    }

    /** Loads the authenticated user's friends list for the friend picker. */
    public void loadFriends() {
        post(current().withFriendListLoading(true));
        socialRepository.getFriends(new DomainCallback<List<UserSummary>>() {
            @Override
            public void onSuccess(List<UserSummary> friends) {
                uiState.postValue(current().withFriendList(friends));
            }

            @Override
            public void onError(Exception error) {
                // Non-blocking: silently clear the loading state; picker shows empty
                uiState.postValue(current().withFriendListLoading(false));
            }
        });
    }

    // ── Submission ────────────────────────────────────────────────────────────

    public void submit(String hotspotId, String date, float timeStart, float timeEnd,
                       int ageMin, int ageMax, java.util.List<String> tags,
                       boolean isPrivate, String invitedFriendId) {
        // Guard: private walk requires a selected friend
        if (isPrivate && (invitedFriendId == null || invitedFriendId.isEmpty())) {
            post(current().withPrivateIntentError(PRIVATE_INTENT_FRIEND_REQUIRED));
            return;
        }

        post(current().withLoading(true));

        intentRepository.createIntent(hotspotId, date, timeStart, timeEnd, ageMin, ageMax, tags,
                isPrivate, invitedFriendId, null,
                new DomainCallback<WalkIntent>() {
                    @Override
                    public void onSuccess(WalkIntent intent) {
                        uiState.postValue(current().withSubmittedIntent(intent));
                    }

                    @Override
                    public void onError(Exception error) {
                        String msg = error.getMessage();
                        if (msg != null && msg.startsWith(VALIDATION_ERROR_PREFIX)) {
                            String raw = msg.substring(VALIDATION_ERROR_PREFIX.length());
                            Map<String, String> fieldErrors = ValidationErrorParser.parse(raw);
                            // Surface the first validation error as a human-readable message
                            String firstError = fieldErrors.isEmpty()
                                    ? raw : fieldErrors.values().iterator().next();
                            uiState.postValue(current().withLoading(false).withError(firstError));
                        } else {
                            uiState.postValue(current().withError(msg));
                        }
                    }
                });
    }

    /**
     * Called by ExploreFragment after it has handled the submitted intent navigation.
     * Clears the submittedIntent signal so it is not re-delivered on rotation/re-render.
     */
    public void consumeSubmission() {
        post(CreateIntentUiState.initial());
    }

    public void consumeError() {
        post(current().withError(null));
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
