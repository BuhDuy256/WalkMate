package com.walkmate.ui.explore.createintent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.social.UserSummary;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class CreateIntentViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private WalkIntentRepository intentRepository;

    @Mock
    private SocialRepository socialRepository;

    private CreateIntentViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new CreateIntentViewModel(intentRepository, socialRepository);
    }

    @Test
    public void initialState_isNotLoadingAndPrivateDisabled() {
        CreateIntentUiState state = viewModel.getUiState().getValue();

        assertNotNull(state);
        assertFalse(state.isLoading());
        assertFalse(state.isPrivate());
        assertNull(state.getInvitedFriendId());
        assertNull(state.getInvitedFriendName());
        assertTrue(state.getFriendList().isEmpty());
        assertFalse(state.isFriendListLoading());
        assertNull(state.getError());
        assertNull(state.getPrivateIntentError());
        assertFalse(state.isOnboardingRequired());
        assertNull(state.getSubmittedIntent());
    }

    @Test
    public void togglePrivate_whenEnableAndFriendListEmpty_loadsFriends() {
        stubFriends(List.of(friend("u-1", "Alice")));

        viewModel.togglePrivate();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertTrue(state.isPrivate());
        assertEquals(1, state.getFriendList().size());
        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
    }

    @Test
    public void togglePrivate_whenDisable_clearsSelectedFriend() {
        viewModel.selectFriend("u-2", "Bob");
        viewModel.togglePrivate(); // enable
        viewModel.togglePrivate(); // disable

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isPrivate());
        assertNull(state.getInvitedFriendId());
        assertNull(state.getInvitedFriendName());
    }

    @Test
    public void togglePrivate_whenEnableAndFriendListAlreadyLoaded_doesNotReloadFriends() {
        stubFriends(List.of(friend("u-1", "Alice")));

        viewModel.loadFriends();
        viewModel.togglePrivate();

        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
    }

    @Test
    public void preSelectFriend_firstTime_setsPrivateAndFriend_andLoadsFriendsIfEmpty() {
        stubFriends(List.of(friend("u-3", "Charlie")));

        viewModel.preSelectFriend("u-3", "Charlie");

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertTrue(state.isPrivate());
        assertEquals("u-3", state.getInvitedFriendId());
        assertEquals("Charlie", state.getInvitedFriendName());
        assertNull(state.getPrivateIntentError());
        verify(socialRepository, times(1)).getFriends(any(DomainCallback.class));
    }

    @Test
    public void preSelectFriend_whenAlreadySelected_doesNotOverride() {
        viewModel.selectFriend("u-old", "Old Name");

        viewModel.preSelectFriend("u-new", "New Name");

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("u-old", state.getInvitedFriendId());
        assertEquals("Old Name", state.getInvitedFriendName());
        verify(socialRepository, never()).getFriends(any(DomainCallback.class));
    }

    @Test
    public void loadFriends_success_setsFriendListAndStopsLoading() {
        stubFriends(List.of(friend("u-1", "Alice"), friend("u-2", "Bob")));

        viewModel.loadFriends();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isFriendListLoading());
        assertEquals(2, state.getFriendList().size());
    }

    @Test
    public void loadFriends_error_stopsLoadingWithoutCrashing() {
        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onError(new Exception("NETWORK_ERROR"));
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));

        viewModel.loadFriends();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isFriendListLoading());
        assertTrue(state.getFriendList().isEmpty());
    }

    @Test
    public void selectFriend_setsSelection_andClearsPrivateIntentError() {
        viewModel.togglePrivate();
        viewModel.consumePrivateIntentError();

        viewModel.selectFriend("u-9", "Delta");

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("u-9", state.getInvitedFriendId());
        assertEquals("Delta", state.getInvitedFriendName());
        assertNull(state.getPrivateIntentError());
    }

    @Test
    public void submit_privateWithoutFriend_postsPrivateIntentError_andSkipsRepositoryCall() {
        viewModel.submit(
                "h-1", "2026-05-08", 7.5f, 8.5f,
                18, 35, "ANY", List.of("music"),
                true, null
        );

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("A friend must be selected for a private walk.", state.getPrivateIntentError());

        verify(intentRepository, never()).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));
    }

    @Test
    public void submit_success_postsSubmittedIntent() {
        WalkIntent created = intent("intent-1", "OPEN", null);

        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onSuccess(created);
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit(
                "h-1", "2026-05-08", 7.5f, 8.5f,
                18, 35, "ANY", List.of("music", "coffee"),
                false, null
        );

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertNotNull(state.getSubmittedIntent());
        assertEquals("intent-1", state.getSubmittedIntent().getId());
    }

    @Test
    public void submit_errorProfileIncomplete_setsOnboardingRequired() {
        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onError(new Exception("PROFILE_INCOMPLETE_FOR_MATCHING"));
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit("h-2", "2026-05-08", 9f, 10f, 20, 40, "FEMALE", List.of(), false, null);

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertTrue(state.isOnboardingRequired());
    }

    @Test
    public void submit_validationError_parsesAndShowsFirstFieldMessage() {
        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onError(new Exception("VALIDATION_ERROR|time_start: must be before time_end, age_min: must be >= 13"));
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit("h-3", "2026-05-08", 11f, 10f, 18, 30, "ANY", List.of(), false, null);

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("must be before time_end", state.getError());
    }

    @Test
    public void submit_genericError_propagatesMessage() {
        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onError(new Exception("SERVER_DOWN"));
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit("h-4", "2026-05-08", 12f, 13f, 18, 30, "ANY", List.of(), false, null);

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("SERVER_DOWN", state.getError());
        assertFalse(state.isLoading());
    }

    @Test
    public void consumeSubmission_resetsStateToInitial() {
        WalkIntent created = intent("intent-9", "OPEN", null);

        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onSuccess(created);
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit("h-9", "2026-05-08", 7f, 8f, 18, 35, "ANY", List.of(), false, null);
        viewModel.consumeSubmission();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getSubmittedIntent());
        assertFalse(state.isPrivate());
        assertNull(state.getError());
    }

    @Test
    public void consumeError_clearsGenericErrorOnly() {
        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onError(new Exception("ANY_ERROR"));
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit("h-5", "2026-05-08", 14f, 15f, 18, 30, "ANY", List.of(), false, null);
        viewModel.consumeError();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
    }

    @Test
    public void consumeOnboardingRequired_resetsFlag() {
        doAnswer(invocation -> {
            DomainCallback<WalkIntent> cb = invocation.getArgument(11);
            cb.onError(new Exception("PROFILE_INCOMPLETE_FOR_MATCHING"));
            return null;
        }).when(intentRepository).createIntent(
                anyString(), anyString(), anyFloat(), anyFloat(), anyInt(), anyInt(),
                anyString(), anyList(), anyBoolean(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.submit("h-6", "2026-05-08", 16f, 17f, 18, 30, "ANY", List.of(), false, null);
        viewModel.consumeOnboardingRequired();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isOnboardingRequired());
    }

    @Test
    public void consumePrivateIntentError_clearsPrivateError() {
        viewModel.submit("h-7", "2026-05-08", 18f, 19f, 18, 30, "ANY", List.of(), true, null);
        viewModel.consumePrivateIntentError();

        CreateIntentUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getPrivateIntentError());
    }

    private void stubFriends(List<UserSummary> friends) {
        doAnswer(invocation -> {
            DomainCallback<List<UserSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(friends);
            return null;
        }).when(socialRepository).getFriends(any(DomainCallback.class));
    }

    private static UserSummary friend(String id, String name) {
        return new UserSummary(
                id,
                name,
                "https://example.com/" + id,
                80,
                "FRIENDS",
                "bio",
                List.of("music"),
                null,
                "2026-05-08T11:00:00Z"
        );
    }

    private static WalkIntent intent(String id, String status, String proposalId) {
        return new WalkIntent(
                id,
                "hotspot-1",
                "Central Park",
                "user-1",
                7.5f,
                8.5f,
                18,
                35,
                "ANY",
                status,
                "2026-05-08T07:00:00Z",
                "2026-05-08",
                List.of("music"),
                "2026-05-08T08:30:00Z",
                "morning walk",
                proposalId
        );
    }
}
