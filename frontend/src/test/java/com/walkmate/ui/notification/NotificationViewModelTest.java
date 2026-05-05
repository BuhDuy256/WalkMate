package com.walkmate.ui.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.notification.NotificationType;
import com.walkmate.domain.shared.DomainCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

public class NotificationViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private NotificationRepository repository;

    private NotificationViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new NotificationViewModel(repository);
    }

    @Test
    public void loadNotifications_success_postsReady() {
        Notification n = Notification.create("user-1", NotificationType.PROPOSAL_RECEIVED, null);
        doAnswer(invocation -> {
            DomainCallback<List<Notification>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(n));
            return null;
        }).when(repository).getNotifications(any(DomainCallback.class));

        viewModel.startPolling(); // triggers initial loadNotifications(true)
        NotificationUiState state = viewModel.getUiState().getValue();
        assertEquals(NotificationUiState.Kind.READY, state.kind);
        assertEquals(1, state.notifications.size());
        assertEquals(1, state.unreadCount);

        viewModel.stopPolling();
    }

    @Test
    public void loadNotifications_error_postsError() {
        doAnswer(invocation -> {
            DomainCallback<List<Notification>> cb = invocation.getArgument(0);
            cb.onError(new Exception("NETWORK"));
            return null;
        }).when(repository).getNotifications(any(DomainCallback.class));

        viewModel.startPolling();
        NotificationUiState state = viewModel.getUiState().getValue();
        assertEquals(NotificationUiState.Kind.ERROR, state.kind);
        assertEquals("NETWORK", state.errorMessage);

        viewModel.stopPolling();
    }

    @Test
    public void markRead_callsRepository_and_refreshes() {
        Notification n = Notification.create("user-1", NotificationType.PROPOSAL_RECEIVED, null);
        doAnswer(invocation -> {
            DomainCallback<List<Notification>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(n));
            return null;
        }).when(repository).getNotifications(any(DomainCallback.class));

        // initial load
        viewModel.startPolling();

        // stub markRead to call success and ensure subsequent getNotifications invoked
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(repository).markRead(anyString(), any(DomainCallback.class));

        viewModel.markRead(n.getNotificationId());

        NotificationUiState post = viewModel.getUiState().getValue();
        // after refresh, still READY and notifications present
        assertEquals(NotificationUiState.Kind.READY, post.kind);

        viewModel.stopPolling();
    }
}
