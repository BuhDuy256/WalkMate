package com.walkmate.ui.social.blocked;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the Blocked Users screen.
 *
 * loadBlocked() fetches the current blocked-users list.
 * unblock() removes the user from the list on success and shows a toast
 * via the unblockSuccessEvent one-shot LiveData.
 */
public class BlockedUsersViewModel extends ViewModel {

    private final MutableLiveData<BlockedUsersUiState> uiState =
            new MutableLiveData<>(BlockedUsersUiState.loading());

    /** Carries the display name of the successfully unblocked user for the toast. */
    private final MutableLiveData<String> unblockSuccessEvent = new MutableLiveData<>(null);

    private final SocialRepository socialRepository;

    public BlockedUsersViewModel(SocialRepository socialRepository) {
        this.socialRepository = socialRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<BlockedUsersUiState> getUiState()         { return uiState; }

    public LiveData<String> getUnblockSuccessEvent()          { return unblockSuccessEvent; }

    public void consumeUnblockSuccessEvent()                  { unblockSuccessEvent.postValue(null); }

    public void loadBlocked() {
        uiState.postValue(BlockedUsersUiState.loading());
        socialRepository.getBlockedUsers(new DomainCallback<List<UserSummary>>() {
            @Override public void onSuccess(List<UserSummary> result) {
                uiState.postValue(new BlockedUsersUiState(false, null, result));
            }
            @Override public void onError(Exception e) {
                uiState.postValue(BlockedUsersUiState.error(friendlyError(e)));
            }
        });
    }

    public void unblock(String userId, String displayName) {
        socialRepository.unblock(userId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v) {
                // Optimistic: remove user from the current list without re-fetching.
                BlockedUsersUiState current = uiState.getValue();
                if (current != null && !current.isLoading()) {
                    List<UserSummary> updated = new ArrayList<>(current.getBlockedUsers());
                    updated.removeIf(u -> userId.equals(u.getUserId()));
                    uiState.postValue(new BlockedUsersUiState(false, null, updated));
                }
                unblockSuccessEvent.postValue(displayName);
            }
            @Override public void onError(Exception e) {
                uiState.postValue(BlockedUsersUiState.error(friendlyError(e)));
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String friendlyError(Exception e) {
        return ErrorMessageResolver.resolve(e.getMessage());
    }
}
