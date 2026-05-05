package com.walkmate.ui.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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

import java.util.List;

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
    public void loadAll_success_combinesResults() {
        UserSummary u = new UserSummary("id","name",null,25,false);
        FriendRequest fr = new FriendRequest("req","id","name",null);

        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(u));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(fr));
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of());
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.loadAll();

        FriendsUiState state = viewModel.getUiState().getValue();
        assertTrue(state.getFriends().size() == 1);
        assertTrue(state.getIncomingRequests().size() == 1);
        assertTrue(state.getOutgoingRequests().isEmpty());
        assertNull(state.getError());
    }

    @Test
    public void navigateToInviteWalk_postsEvent_andConsumeClearsIt() {
        viewModel.navigateToInviteWalk("friend-123");
        String ev = viewModel.getInviteWalkEvent().getValue();
        assertEquals("friend-123", ev);

        viewModel.consumeInviteWalkEvent();
        String cleared = viewModel.getInviteWalkEvent().getValue();
        assertNull(cleared);
    }

    @Test
    public void acceptRequest_onSuccess_triggersLoadAll() {
        // stub accept to succeed and make getFriends return empty lists so loadAll completes
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(socialRepository).acceptFriendRequest(any(String.class), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of());
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));
        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of());
            return null;
        }).when(socialRepository).getIncomingRequests(any(DomainCallback.class));
        doAnswer(invocation -> {
            DomainCallback<List<FriendRequest>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of());
            return null;
        }).when(socialRepository).getOutgoingRequests(any(DomainCallback.class));

        viewModel.acceptRequest("r1");

        FriendsUiState state = viewModel.getUiState().getValue();
        // loadAll posts a non-loading final state (loading -> result)
        assertTrue(state.getFriends().isEmpty());
    }

    @Test
    public void declineRequest_onError_postsError() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onError(new Exception("DECLINE_FAILED"));
            return null;
        }).when(socialRepository).declineFriendRequest(any(String.class), any(DomainCallback.class));

        viewModel.declineRequest("r1");

        FriendsUiState state = viewModel.getUiState().getValue();
        assertEquals("DECLINE_FAILED", state.getError());
    }
}
