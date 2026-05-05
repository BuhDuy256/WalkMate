package com.walkmate.ui.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSessionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

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
    public void loadHistory_success_postsReadyState() {
        SessionSummary s = new SessionSummary("sid","partner","partnerName","hotspot", "2020-01-01T00:00:00Z", "2020-01-01T01:00:00Z", "COMPLETED", false);
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
}
