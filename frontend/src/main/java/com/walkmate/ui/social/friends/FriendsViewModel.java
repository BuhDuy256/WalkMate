package com.walkmate.ui.social.friends;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.FriendRequest;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared ViewModel for FriendsFragment and its three sub-fragments.
 *
 * Sub-fragments obtain this VM via ViewModelProvider(requireParentFragment()).
 *
 * loadAll() fires getFriends(), getIncomingRequests(), getOutgoingRequests() in parallel
 * and merges the results into a single FriendsUiState once all three complete.
 */
public class FriendsViewModel extends ViewModel {

    private final MutableLiveData<FriendsUiState> uiState =
            new MutableLiveData<>(FriendsUiState.loading());

    /**
     * One-shot navigation event that carries a friendId to the ExploreFragment.
     * Observers must call consumeInviteWalkEvent() after handling.
     */
    private final MutableLiveData<String> inviteWalkEvent = new MutableLiveData<>(null);

    private final SocialRepository socialRepository;

    public FriendsViewModel(SocialRepository socialRepository) {
        this.socialRepository = socialRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<FriendsUiState> getUiState() { return uiState; }

    public LiveData<String> getInviteWalkEvent() { return inviteWalkEvent; }

    public void consumeInviteWalkEvent() { inviteWalkEvent.postValue(null); }

    /**
     * Fires three parallel repository calls and merges their results.
     * The AtomicInteger barrier ensures the final state is posted only once
     * all three calls have completed (or failed).
     */
    public void loadAll() {
        uiState.postValue(FriendsUiState.loading());

        final AtomicReference<List<UserSummary>>   friendsHolder   = new AtomicReference<>(Collections.emptyList());
        final AtomicReference<List<FriendRequest>> incomingHolder  = new AtomicReference<>(Collections.emptyList());
        final AtomicReference<List<FriendRequest>> outgoingHolder  = new AtomicReference<>(Collections.emptyList());
        final AtomicReference<String>              errorHolder     = new AtomicReference<>(null);
        final AtomicInteger                        doneCount       = new AtomicInteger(0);

        Runnable publish = () -> {
            if (doneCount.incrementAndGet() == 3) {
                String err = errorHolder.get();
                if (err != null) {
                    uiState.postValue(FriendsUiState.error(err));
                } else {
                    uiState.postValue(new FriendsUiState(
                            false, null,
                            friendsHolder.get(),
                            incomingHolder.get(),
                            outgoingHolder.get()));
                }
            }
        };

        // ── Friends ───────────────────────────────────────────────────────────
        socialRepository.getFriends(new DomainCallback<List<UserSummary>>() {
            @Override public void onSuccess(List<UserSummary> result) {
                friendsHolder.set(result != null ? result : Collections.emptyList());
                publish.run();
            }
            @Override public void onError(Exception e) {
                errorHolder.compareAndSet(null, friendlyError(e));
                publish.run();
            }
        });

        // ── Incoming requests ─────────────────────────────────────────────────
        socialRepository.getIncomingRequests(new DomainCallback<List<FriendRequest>>() {
            @Override public void onSuccess(List<FriendRequest> result) {
                incomingHolder.set(result != null ? result : Collections.emptyList());
                publish.run();
            }
            @Override public void onError(Exception e) {
                // Non-fatal — show empty incoming list
                publish.run();
            }
        });

        // ── Outgoing requests ─────────────────────────────────────────────────
        socialRepository.getOutgoingRequests(new DomainCallback<List<FriendRequest>>() {
            @Override public void onSuccess(List<FriendRequest> result) {
                outgoingHolder.set(result != null ? result : Collections.emptyList());
                publish.run();
            }
            @Override public void onError(Exception e) {
                // Non-fatal — show empty outgoing list
                publish.run();
            }
        });
    }

    public void acceptRequest(String requestId) {
        socialRepository.acceptFriendRequest(requestId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v) { loadAll(); }
            @Override public void onError(Exception e) {
                uiState.postValue(FriendsUiState.error(friendlyError(e)));
            }
        });
    }

    public void declineRequest(String requestId) {
        socialRepository.declineFriendRequest(requestId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v) { loadAll(); }
            @Override public void onError(Exception e) {
                uiState.postValue(FriendsUiState.error(friendlyError(e)));
            }
        });
    }

    public void removeFriend(String userId) {
        socialRepository.removeFriend(userId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v) { loadAll(); }
            @Override public void onError(Exception e) {
                uiState.postValue(FriendsUiState.error(friendlyError(e)));
            }
        });
    }

    public void cancelRequest(String requestId) {
        socialRepository.cancelFriendRequest(requestId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void v) { loadAll(); }
            @Override public void onError(Exception e) {
                uiState.postValue(FriendsUiState.error(friendlyError(e)));
            }
        });
    }

    /**
     * Posts a navigation event so the container FriendsFragment can deep-link
     * to ExploreFragment with the friend's id pre-filled.
     */
    public void navigateToInviteWalk(String friendId) {
        inviteWalkEvent.postValue(friendId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String friendlyError(Exception e) {
        return e.getMessage() != null ? e.getMessage() : "Something went wrong.";
    }
}
