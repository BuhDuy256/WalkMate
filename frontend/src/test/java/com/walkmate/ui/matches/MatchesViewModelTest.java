package com.walkmate.ui.matches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkproposal.WalkProposal;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.domain.walksession.WalkSessionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class MatchesViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private WalkIntentRepository intentRepository;

    @Mock
    private WalkProposalRepository proposalRepository;

    @Mock
    private WalkSessionRepository sessionRepository;

    private MatchesViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new MatchesViewModel(intentRepository, proposalRepository, sessionRepository);
    }

    @Test
    public void loadProposals_success_updatesUiState() {
        WalkProposal p = new WalkProposal("pid","iid","u","name",null,25,0,null,16f,16.5f, WalkProposal.Status.PENDING, null,"h","H",null,null,false);
        doAnswer(invocation -> {
            DomainCallback<List<WalkProposal>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(p));
            return null;
        }).when(proposalRepository).getProposals(any(DomainCallback.class));

        viewModel.loadProposals();

        MatchesUiState state = viewModel.getUiState().getValue();
        assertTrue(state.getProposals().size() == 1);
        assertNull(state.getError());
    }

    @Test
    public void loadProposals_error_updatesError() {
        doAnswer(invocation -> {
            DomainCallback<List<WalkProposal>> cb = invocation.getArgument(0);
            cb.onError(new Exception("SOME_CODE"));
            return null;
        }).when(proposalRepository).getProposals(any(DomainCallback.class));

        viewModel.loadProposals();

        MatchesUiState state = viewModel.getUiState().getValue();
        assertEquals("SOME_CODE", state.getError());
    }

    @Test
    public void acceptProposal_whenConfirmed_triggersCelebrationAndNavigatesToSession() {
        WalkProposal confirmed = new WalkProposal("pid","iid","u","name",null,25,0,null,16f,16.5f, WalkProposal.Status.CONFIRMED, "iso","h","H",null,"session-1",false);
        doAnswer(invocation -> {
            DomainCallback<WalkProposal> cb = invocation.getArgument(1);
            cb.onSuccess(confirmed);
            return null;
        }).when(proposalRepository).acceptProposal(any(String.class), any(DomainCallback.class));

        viewModel.acceptProposal("pid");

        Boolean celebr = viewModel.getCelebrationEvent().getValue();
        assertTrue(celebr != null && celebr);
        Integer tab = viewModel.getScrollToTabEvent().getValue();
        assertEquals(MatchesPagerAdapter.TAB_SESSION, tab.intValue());
    }

    @Test
    public void passProposal_privateInvite_removesProposalAndDoesNotNavigate() {
        WalkProposal p = new WalkProposal("pid","iid","u","name",null,25,0,null,16f,16.5f,
                WalkProposal.Status.PENDING, null,"h","H",null,null,true);
        viewModel.getUiState().setValue(new MatchesUiState(false, List.of(), List.of(p), List.of(), null));

        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(proposalRepository).passProposal(any(String.class), any(DomainCallback.class));

        viewModel.passProposal("pid", true);

        MatchesUiState state = viewModel.getUiState().getValue();
        assertTrue(state.getProposals().isEmpty());
        // Private decline must NOT trigger Finding refresh or tab navigation
        assertNull(viewModel.getScrollToTabEvent().getValue());
    }

    @Test
    public void passProposal_publicProposal_removesProposalAndNavigatesToFinding() {
        WalkProposal p = new WalkProposal("pid","iid","u","name",null,25,0,null,16f,16.5f,
                WalkProposal.Status.PENDING, null,"h","H",null,null,false);
        viewModel.getUiState().setValue(new MatchesUiState(false, List.of(), List.of(p), List.of(), null));

        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(1);
            cb.onSuccess(null);
            return null;
        }).when(proposalRepository).passProposal(any(String.class), any(DomainCallback.class));

        doAnswer(invocation -> {
            DomainCallback<List<WalkIntent>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of());
            return null;
        }).when(intentRepository).listActiveIntents(any(DomainCallback.class));

        viewModel.passProposal("pid", false);

        MatchesUiState state = viewModel.getUiState().getValue();
        assertTrue(state.getProposals().isEmpty());
        Integer tab = viewModel.getScrollToTabEvent().getValue();
        assertEquals(MatchesPagerAdapter.TAB_FINDING, tab.intValue());
    }

    @Test
    public void acceptProposal_whenNotConfirmed_updatesProposalInPlace() {
        WalkProposal original = new WalkProposal("pid","iid","u","name",null,25,0,null,16f,16.5f, WalkProposal.Status.PENDING, null,"h","H",null,null,false);
        WalkProposal updated = new WalkProposal("pid","iid","u","name",null,25,0,null,16f,16.5f, WalkProposal.Status.PENDING, null,"h","H","ACCEPTED",null,false);

        // seed initial state with original proposal
        viewModel.getUiState().setValue(new MatchesUiState(false, List.of(), List.of(original), List.of(), null));

        doAnswer(invocation -> {
            DomainCallback<WalkProposal> cb = invocation.getArgument(1);
            cb.onSuccess(updated);
            return null;
        }).when(proposalRepository).acceptProposal(any(String.class), any(DomainCallback.class));

        viewModel.acceptProposal("pid");

        MatchesUiState state = viewModel.getUiState().getValue();
        assertEquals(1, state.getProposals().size());
        assertEquals("ACCEPTED", state.getProposals().get(0).getMyAcceptanceStatus());
    }
}
