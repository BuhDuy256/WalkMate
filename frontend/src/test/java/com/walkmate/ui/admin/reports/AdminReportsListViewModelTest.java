package com.walkmate.ui.admin.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.report.AdminReport;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.shared.DomainCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class AdminReportsListViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private AdminReportRepository reportRepo;

    private AdminReportsListViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new AdminReportsListViewModel(reportRepo);
    }

    @Test
    public void loadReports_success_updatesUiStateAndCounts() {
        AdminReport pending = report("r-1", AdminReport.Status.PENDING);
        AdminReport approved = report("r-2", AdminReport.Status.APPROVED);
        AdminReport rejected = report("r-3", AdminReport.Status.REJECTED);

        doAnswer(invocation -> {
            DomainCallback<List<AdminReport>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of(pending, approved, rejected));
            return null;
        }).when(reportRepo).getAllReports(any(DomainCallback.class));

        viewModel.loadReports();

        AdminReportsListUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertEquals(3, state.getReports().size());
        assertEquals(3, state.getTotalCount());
        assertEquals(1, state.getPendingCount());
        assertEquals(1, state.getApprovedCount());
        assertEquals(1, state.getRejectedCount());
    }

    @Test
    public void loadReports_successWithNullList_mapsToEmptyList() {
        doAnswer(invocation -> {
            DomainCallback<List<AdminReport>> cb = invocation.getArgument(0);
            cb.onSuccess(null);
            return null;
        }).when(reportRepo).getAllReports(any(DomainCallback.class));

        viewModel.loadReports();

        AdminReportsListUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertNull(state.getError());
        assertTrue(state.getReports().isEmpty());
        assertEquals(0, state.getTotalCount());
    }

    @Test
    public void loadReports_errorWithMessage_updatesError() {
        doAnswer(invocation -> {
            DomainCallback<List<AdminReport>> cb = invocation.getArgument(0);
            cb.onError(new Exception("REPORTS_LOAD_FAILED"));
            return null;
        }).when(reportRepo).getAllReports(any(DomainCallback.class));

        viewModel.loadReports();

        AdminReportsListUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertEquals("REPORTS_LOAD_FAILED", state.getError());
        assertTrue(state.getReports().isEmpty());
    }

    @Test
    public void loadReports_errorWithoutMessage_usesFallbackMessage() {
        doAnswer(invocation -> {
            DomainCallback<List<AdminReport>> cb = invocation.getArgument(0);
            cb.onError(new Exception((String) null));
            return null;
        }).when(reportRepo).getAllReports(any(DomainCallback.class));

        viewModel.loadReports();

        AdminReportsListUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("Failed to load reports", state.getError());
    }

    @Test
    public void loadReports_callsRepositoryExactlyOnce() {
        doAnswer(invocation -> {
            DomainCallback<List<AdminReport>> cb = invocation.getArgument(0);
            cb.onSuccess(List.of());
            return null;
        }).when(reportRepo).getAllReports(any(DomainCallback.class));

        viewModel.loadReports();

        verify(reportRepo, times(1)).getAllReports(any(DomainCallback.class));
    }

    @Test
    public void onReportClicked_postsNavigationEvent() {
        viewModel.onReportClicked("report-42");

        assertEquals("report-42", viewModel.getNavigateToDetailEvent().getValue());
    }

    @Test
    public void consumeNavigateToDetail_clearsNavigationEvent() {
        viewModel.onReportClicked("report-99");

        viewModel.consumeNavigateToDetail();

        assertNull(viewModel.getNavigateToDetailEvent().getValue());
    }

    @Test
    public void onReportClicked_twice_keepsLatestEventValue() {
        viewModel.onReportClicked("r-old");
        viewModel.onReportClicked("r-new");

        assertEquals("r-new", viewModel.getNavigateToDetailEvent().getValue());
    }

    @Test
    public void consumeNavigateToDetail_whenAlreadyNull_staysNull() {
        assertNull(viewModel.getNavigateToDetailEvent().getValue());

        viewModel.consumeNavigateToDetail();

        assertNull(viewModel.getNavigateToDetailEvent().getValue());
    }

    private static AdminReport report(String id, AdminReport.Status status) {
        return new AdminReport(
                id,
                "session-1",
                "reporter-1",
                "Reporter",
                "reported-1",
                "Reported",
                AdminReport.Reason.OTHER,
                null,
                status,
                -5,
                "2026-05-08T09:00:00Z",
                null,
                null,
                null
        );
    }
}
