package com.walkmate.ui.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.FriendRequest;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FriendsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private SocialRepository socialRepository;

    private FriendsViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new FriendsViewModel(socialRepository);
    }

    @Test
    public void initialState_isLoadingAndNoError() {
        FriendsUiState state = viewModel.getUiState().getValue();

        assertNotNull(state);
        assertTrue(state.isLoading());
        assertNull(state.getError());
        assertTrue(state.getFriends().isEmpty());
        assertTrue(state.getIncomingRequests().isEmpty());
        assertTrue(state.getOutgoingRequests().isEmpty());
        assertEquals(0, state.getIncomingBadgeCount());
    }

    @Test
    public void loadAll_success_combinesResultsAndCounts() {
        UserSummary friendA = user("u-1", "Alice", "FRIENDS");
        UserSummary friendB = user("u-2", "Bob", "FRIENDS");
        FriendRequest incomingA = request("in-1", "sender-1", "PENDING");
        FriendRequest incomingB = request("in-2", "sender-2", "PENDING");
        FriendRequest outgoingA = request("out-1", "sender-3", "PENDING");

        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(friendA, friendB));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(incomingA, incomingB));
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(outgoingA));
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertEquals(2, state.getFriends().size());
        assertEquals(2, state.getIncomingRequests().size());
        assertEquals(1, state.getOutgoingRequests().size());
        assertEquals(2, state.getIncomingBadgeCount());
        assertEquals(2, state.getFriendsCount());
        assertEquals(1, state.getOutgoingCount());
    }

    @Test
    public void loadAll_whenFriendsReturnsNull_usesEmptyList() {
        stubIncomingSuccess(List.of(request("in-1", "sender-1", "PENDING")));
        stubOutgoingSuccess(List.of(request("out-1", "sender-2", "PENDING")));

        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
        assertTrue(state.getFriends().isEmpty());
        assertEquals(1, state.getIncomingRequests().size());
        assertEquals(1, state.getOutgoingRequests().size());
    }

    @Test
    public void loadAll_whenIncomingReturnsNull_usesEmptyList() {
        stubFriendsSuccess(List.of(user("u-1", "Alice", "FRIENDS")));
        stubOutgoingSuccess(List.of(request("out-1", "sender-2", "PENDING")));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
        assertEquals(1, state.getFriends().size());
        assertTrue(state.getIncomingRequests().isEmpty());
        assertEquals(1, state.getOutgoingRequests().size());
        assertEquals(0, state.getIncomingBadgeCount());
    }

    @Test
    public void loadAll_whenOutgoingReturnsNull_usesEmptyList() {
        stubFriendsSuccess(List.of(user("u-1", "Alice", "FRIENDS")));
        stubIncomingSuccess(List.of(request("in-1", "sender-1", "PENDING")));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
        assertEquals(1, state.getFriends().size());
        assertEquals(1, state.getIncomingRequests().size());
        assertTrue(state.getOutgoingRequests().isEmpty());
    }

    @Test
    public void loadAll_whenIncomingFails_isNonFatal() {
        stubFriendsSuccess(List.of(user("u-1", "Alice", "FRIENDS")));
        stubOutgoingSuccess(List.of(request("out-1", "sender-2", "PENDING")));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onError(new Exception("incoming failed"));
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
        assertEquals(1, state.getFriends().size());
        assertTrue(state.getIncomingRequests().isEmpty());
        assertEquals(1, state.getOutgoingRequests().size());
    }

    @Test
    public void loadAll_whenOutgoingFails_isNonFatal() {
        stubFriendsSuccess(List.of(user("u-1", "Alice", "FRIENDS")));
        stubIncomingSuccess(List.of(request("in-1", "sender-1", "PENDING")));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onError(new Exception("outgoing failed"));
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
        assertEquals(1, state.getFriends().size());
        assertEquals(1, state.getIncomingRequests().size());
        assertTrue(state.getOutgoingRequests().isEmpty());
    }

    @Test
    public void loadAll_whenFriendsFails_postsFatalErrorMessage() {
        stubIncomingSuccess(List.of(request("in-1", "sender-1", "PENDING")));
        stubOutgoingSuccess(List.of(request("out-1", "sender-2", "PENDING")));

        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onError(new Exception("friends failed"));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertEquals("friends failed", state.getError());
        assertTrue(state.getFriends().isEmpty());
        assertTrue(state.getIncomingRequests().isEmpty());
        assertTrue(state.getOutgoingRequests().isEmpty());
    }

    @Test
    public void loadAll_whenFriendsFailsWithNullMessage_usesFallbackMessage() {
        stubIncomingSuccess(List.of());
        stubOutgoingSuccess(List.of());

        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onError(new Exception((String) null));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("Something went wrong.", state.getError());
    }

    @Test
    public void loadAll_publishesOnlyAfterAllThreeCallbacksFinish() {
        AtomicReference<DomainCallback<List<UserSummary>>> friendsCbRef = new AtomicReference<>();
        AtomicReference<DomainCallback<List<FriendRequest>>> incomingCbRef = new AtomicReference<>();
        AtomicReference<DomainCallback<List<FriendRequest>>> outgoingCbRef = new AtomicReference<>();

        doAnswer(invocation -> {
            friendsCbRef.set(invocation.getArgument(0));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        doAnswer(invocation -> {
            incomingCbRef.set(invocation.getArgument(0));
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));

        doAnswer(invocation -> {
            outgoingCbRef.set(invocation.getArgument(0));
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState loadingState = viewModel.getUiState().getValue();
        assertNotNull(loadingState);
        assertTrue(loadingState.isLoading());

        incomingCbRef.get().onSuccess(List.of(request("in-1", "sender-1", "PENDING")));
        FriendsUiState afterIncoming = viewModel.getUiState().getValue();
        assertNotNull(afterIncoming);
        assertTrue(afterIncoming.isLoading());

        outgoingCbRef.get().onSuccess(List.of(request("out-1", "sender-2", "PENDING")));
        FriendsUiState afterOutgoing = viewModel.getUiState().getValue();
        assertNotNull(afterOutgoing);
        assertTrue(afterOutgoing.isLoading());

        friendsCbRef.get().onSuccess(List.of(user("u-1", "Alice", "FRIENDS")));
        FriendsUiState finalState = viewModel.getUiState().getValue();
        assertNotNull(finalState);
        assertFalse(finalState.isLoading());
        assertNull(finalState.getError());
        assertEquals(1, finalState.getFriends().size());
        assertEquals(1, finalState.getIncomingRequests().size());
        assertEquals(1, finalState.getOutgoingRequests().size());
    }

    @Test
    public void loadAll_multipleErrors_keepsFirstFatalErrorFromFriends() {
        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onError(new Exception("friends error first"));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onError(new Exception("incoming error second"));
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onError(new Exception("outgoing error third"));
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("friends error first", state.getError());
    }

    @Test
    public void acceptRequest_onSuccess_triggersReload() {
        stubLoadAllSuccessWithEmptyLists();

        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).acceptFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.acceptRequest("req-1");

        verify(socialRepository, times(1)).acceptFriendRequest(anyString(), any(DomainCallback.class));
        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
        verify(socialRepository, times(1)).getIncomingRequests(any(DomainCallback.class));
        verify(socialRepository, times(1)).getOutgoingRequests(any(DomainCallback.class));

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
    }

    @Test
    public void acceptRequest_onError_postsErrorAndDoesNotReload() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onError(new Exception("ACCEPT_FAILED"));
            return null;
        }).when(socialRepository).acceptFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.acceptRequest("req-1");

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("ACCEPT_FAILED", state.getError());

        verify(socialRepository, never()).getFriends(any(DomainCallback.class));
        verify(socialRepository, never()).getIncomingRequests(any(DomainCallback.class));
        verify(socialRepository, never()).getOutgoingRequests(any(DomainCallback.class));
    }

    @Test
    public void acceptRequest_onErrorWithNullMessage_usesFallbackMessage() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onError(new Exception((String) null));
            return null;
        }).when(socialRepository).acceptFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.acceptRequest("req-1");

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("Something went wrong.", state.getError());
    }

    @Test
    public void declineRequest_onSuccess_triggersReload() {
        stubLoadAllSuccessWithEmptyLists();

        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).declineFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.declineRequest("req-1");

        verify(socialRepository, times(1)).declineFriendRequest(anyString(), any(DomainCallback.class));
        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
        verify(socialRepository, times(1)).getIncomingRequests(any(DomainCallback.class));
        verify(socialRepository, times(1)).getOutgoingRequests(any(DomainCallback.class));
    }

    @Test
    public void declineRequest_onError_postsError() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onError(new Exception("DECLINE_FAILED"));
            return null;
        }).when(socialRepository).declineFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.declineRequest("req-1");

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("DECLINE_FAILED", state.getError());
    }

    @Test
    public void removeFriend_onSuccess_triggersReload() {
        stubLoadAllSuccessWithEmptyLists();

        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).removeFriend(anyString(), any(DomainCallback.class));

        viewModel.removeFriend("user-1");

        verify(socialRepository, times(1)).removeFriend(anyString(), any(DomainCallback.class));
        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
        verify(socialRepository, times(1)).getIncomingRequests(any(DomainCallback.class));
        verify(socialRepository, times(1)).getOutgoingRequests(any(DomainCallback.class));
    }

    @Test
    public void removeFriend_onError_postsError() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onError(new Exception("REMOVE_FAILED"));
            return null;
        }).when(socialRepository).removeFriend(anyString(), any(DomainCallback.class));

        viewModel.removeFriend("user-1");

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("REMOVE_FAILED", state.getError());
    }

    @Test
    public void cancelRequest_onSuccess_triggersReload() {
        stubLoadAllSuccessWithEmptyLists();

        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).cancelFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.cancelRequest("req-1");

        verify(socialRepository, times(1)).cancelFriendRequest(anyString(), any(DomainCallback.class));
        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
        verify(socialRepository, times(1)).getIncomingRequests(any(DomainCallback.class));
        verify(socialRepository, times(1)).getOutgoingRequests(any(DomainCallback.class));
    }

    @Test
    public void cancelRequest_onError_postsError() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onError(new Exception("CANCEL_FAILED"));
            return null;
        }).when(socialRepository).cancelFriendRequest(anyString(), any(DomainCallback.class));

        viewModel.cancelRequest("req-1");

        FriendsUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("CANCEL_FAILED", state.getError());
    }

    @Test
    public void navigateToInviteWalk_postsEvent_andConsumeClearsIt() {
        viewModel.navigateToInviteWalk("friend-123");
        assertEquals("friend-123", viewModel.getInviteWalkEvent().getValue());

        viewModel.consumeInviteWalkEvent();
        assertNull(viewModel.getInviteWalkEvent().getValue());
    }

    @Test
    public void consumeInviteWalkEvent_whenAlreadyNull_remainsNull() {
        assertNull(viewModel.getInviteWalkEvent().getValue());

        viewModel.consumeInviteWalkEvent();

        assertNull(viewModel.getInviteWalkEvent().getValue());
    }

    private void stubLoadAllSuccessWithEmptyLists() {
        stubFriendsSuccess(new ArrayList<>());
        stubIncomingSuccess(new ArrayList<>());
        stubOutgoingSuccess(new ArrayList<>());
    }

    private void stubFriendsSuccess(List<UserSummary> result) {
        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(result);
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));
    }

    private void stubIncomingSuccess(List<FriendRequest> result) {
        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(result);
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));
    }

    private void stubOutgoingSuccess(List<FriendRequest> result) {
        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(result);
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));
    }

    private static UserSummary user(String id, String name, String friendshipStatus) {
        return new UserSummary(
                id,
                name,
                "https://example.com/avatar/" + id,
                80,
                friendshipStatus,
                "bio-" + id,
                List.of("night walk", "music"),
                null,
                "2026-05-05T10:00:00Z"
        );
    }

    private static FriendRequest request(String requestId, String senderId, String status) {
        return new FriendRequest(
                requestId,
                senderId,
                "sender-" + senderId,
                "https://example.com/avatar/" + senderId,
                "receiver-1",
                status,
                "2026-05-05T11:00:00Z"
        );
    }
}
