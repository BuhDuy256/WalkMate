package com.walkmate.ui.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SessionHistoryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private WalkSessionRepository sessionRepo;

    private SessionHistoryViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new SessionHistoryViewModel(sessionRepo, "me");
    }

    @Test
    public void initialState_isLoading() {
        SessionHistoryUiState state = viewModel.getUiState().getValue();

        assertNotNull(state);
        assertEquals(SessionHistoryUiState.Kind.LOADING, state.kind);
        assertNull(state.sessions);
        assertNull(state.error);
    }

    @Test
    public void loadHistory_success_postsReadyState() {
        SessionSummary s = summary(
                "sid",
                WalkSession.Status.COMPLETED,
                WalkSession.Status.COMPLETED,
                WalkSession.Status.COMPLETED,
                "2020-01-01T00:00:00Z",
                1_700_000_000_000L);
        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(s));
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadHistory();

        SessionHistoryUiState state = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.READY, state.kind);
        assertTrue(state.sessions.size() == 1);
        assertEquals("me", state.currentUserId);
    }

    @Test
    public void loadHistory_success_allowsNullList() {
        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            cb.onSuccess(null);
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadHistory();

        SessionHistoryUiState state = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.READY, state.kind);
        assertNull(state.sessions);
        assertEquals("me", state.currentUserId);
    }

    @Test
    public void loadHistory_error_postsErrorState() {
        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            cb.onError(new Exception("FAILED"));
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadHistory();

        SessionHistoryUiState state = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.ERROR, state.kind);
        assertEquals("FAILED", state.error);
    }

    @Test
    public void loadHistory_error_nullMessage_usesFallback() {
        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            cb.onError(new Exception((String) null));
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadHistory();

        SessionHistoryUiState state = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.ERROR, state.kind);
        assertEquals("Failed to load history", state.error);
    }

    @Test
    public void loadHistory_postsLoadingBeforeCallbackCompletes() {
        AtomicReference<DomainCallback<List<SessionSummary>>> cbRef = new AtomicReference<>();

        doAnswer(invocation -> {
            cbRef.set(invocation.getArgument(0));
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadHistory();

        SessionHistoryUiState loadingState = viewModel.getUiState().getValue();
        assertNotNull(loadingState);
        assertEquals(SessionHistoryUiState.Kind.LOADING, loadingState.kind);

        cbRef.get().onSuccess(List.of(summary(
                "sid",
                WalkSession.Status.COMPLETED,
                WalkSession.Status.COMPLETED,
                WalkSession.Status.COMPLETED,
                "2020-01-01T00:00:00Z",
                1_700_000_000_000L)));

        SessionHistoryUiState readyState = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.READY, readyState.kind);
    }

    @Test
    public void loadHistory_calledTwice_usesLatestResult() {
        AtomicInteger callCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            DomainCallback<List<SessionSummary>> cb = invocation.getArgument(0);
            if (callCount.getAndIncrement() == 0) {
                cb.onError(new Exception("FIRST_FAIL"));
            } else {
                cb.onSuccess(List.of(summary(
                        "sid",
                        WalkSession.Status.COMPLETED,
                        WalkSession.Status.COMPLETED,
                        WalkSession.Status.COMPLETED,
                        "2020-01-01T00:00:00Z",
                        1_700_000_000_000L)));
            }
            return null;
        }).when(sessionRepo).getSessionHistory(any(DomainCallback.class));

        viewModel.loadHistory();
        SessionHistoryUiState firstState = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.ERROR, firstState.kind);
        assertEquals("FIRST_FAIL", firstState.error);

        viewModel.loadHistory();
        SessionHistoryUiState secondState = viewModel.getUiState().getValue();
        assertEquals(SessionHistoryUiState.Kind.READY, secondState.kind);
        assertEquals(1, secondState.sessions.size());

        verify(sessionRepo, times(2)).getSessionHistory(any(DomainCallback.class));
    }

    private static SessionSummary summary(
            String sessionId,
            WalkSession.Status globalStatus,
            WalkSession.Status callerStatus,
            WalkSession.Status partnerStatus,
            String scheduledStart,
            long terminalAtMs) {
        ParticipantSummary caller = new ParticipantSummary(
                "me",
                "Me",
                "https://example.com/me.png",
                2.5,
                75,
                callerStatus);
        ParticipantSummary partner = new ParticipantSummary(
                "partner-1",
                "Pat",
                "https://example.com/p.png",
                1.25,
                42,
                partnerStatus);
        return new SessionSummary(
                sessionId,
                globalStatus,
                scheduledStart,
                false,
                false,
                null,
                null,
                terminalAtMs,
                10.0,
                20.0,
                "Central Park",
                List.of(caller, partner));
    }
}
