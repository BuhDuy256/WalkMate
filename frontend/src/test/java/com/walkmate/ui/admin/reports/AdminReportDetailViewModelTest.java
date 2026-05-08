package com.walkmate.ui.admin.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

public class AdminReportDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private AdminReportRepository reportRepo;

    private AdminReportDetailViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new AdminReportDetailViewModel(reportRepo);
    }

    @Test
    public void loadReport_success_postsLoadedState() {
        AdminReport report = report("r-1", AdminReport.Status.PENDING);

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(1);
            cb.onSuccess(report);
            return null;
        }).when(reportRepo).getReportById(eq("r-1"), any(DomainCallback.class));

        viewModel.loadReport("r-1");

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertFalse(state.isProcessing());
        assertFalse(state.isResolved());
        assertNull(state.getError());
        assertEquals("r-1", state.getReport().getReportId());
    }

    @Test
    public void loadReport_errorWithMessage_postsErrorState() {
        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(1);
            cb.onError(new Exception("REPORT_NOT_FOUND"));
            return null;
        }).when(reportRepo).getReportById(eq("missing"), any(DomainCallback.class));

        viewModel.loadReport("missing");

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertFalse(state.isProcessing());
        assertFalse(state.isResolved());
        assertNull(state.getReport());
        assertEquals("REPORT_NOT_FOUND", state.getError());
    }

    @Test
    public void loadReport_errorWithoutMessage_usesFallbackMessage() {
        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(1);
            cb.onError(new Exception((String) null));
            return null;
        }).when(reportRepo).getReportById(eq("x"), any(DomainCallback.class));

        viewModel.loadReport("x");

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("Failed to load report", state.getError());
    }

    @Test
    public void resolveReport_withCurrentReport_transitionsProcessingThenResolved() {
        AdminReport loaded = report("r-2", AdminReport.Status.PENDING);
        AdminReport resolved = report("r-2", AdminReport.Status.APPROVED);

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(1);
            cb.onSuccess(loaded);
            return null;
        }).when(reportRepo).getReportById(eq("r-2"), any(DomainCallback.class));
        viewModel.loadReport("r-2");

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(3);
            cb.onSuccess(resolved);
            return null;
        }).when(reportRepo).resolveReport(eq("r-2"), eq("APPROVE"), eq("ok"), any(DomainCallback.class));

        viewModel.resolveReport("r-2", "APPROVE", "ok");

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isLoading());
        assertFalse(state.isProcessing());
        assertTrue(state.isResolved());
        assertNull(state.getError());
        assertNotNull(state.getReport());
        assertEquals(AdminReport.Status.APPROVED, state.getReport().getStatus());
    }

    @Test
    public void resolveReport_withoutCurrentReport_stillCallsRepositoryAndResolves() {
        AdminReport resolved = report("r-3", AdminReport.Status.REJECTED);

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(3);
            cb.onSuccess(resolved);
            return null;
        }).when(reportRepo).resolveReport(eq("r-3"), eq("REJECT"), eq("bad behavior"), any(DomainCallback.class));

        viewModel.resolveReport("r-3", "REJECT", "bad behavior");

        verify(reportRepo, times(1))
                .resolveReport(eq("r-3"), eq("REJECT"), eq("bad behavior"), any(DomainCallback.class));

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertTrue(state.isResolved());
        assertEquals(AdminReport.Status.REJECTED, state.getReport().getStatus());
    }

    @Test
    public void resolveReport_errorWithMessage_postsError() {
        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(3);
            cb.onError(new Exception("RESOLVE_FAILED"));
            return null;
        }).when(reportRepo).resolveReport(eq("r-4"), eq("APPROVE"), eq("note"), any(DomainCallback.class));

        viewModel.resolveReport("r-4", "APPROVE", "note");

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.isResolved());
        assertEquals("RESOLVE_FAILED", state.getError());
        assertNull(state.getReport());
    }

    @Test
    public void resolveReport_errorWithoutMessage_usesFallbackMessage() {
        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(3);
            cb.onError(new Exception((String) null));
            return null;
        }).when(reportRepo).resolveReport(eq("r-5"), eq("REJECT"), eq("note"), any(DomainCallback.class));

        viewModel.resolveReport("r-5", "REJECT", "note");

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals("Failed to resolve report", state.getError());
    }

    @Test
    public void consumeError_whenErrorPresent_clearsErrorAndKeepsCurrentReportIfAny() {
        AdminReport loaded = report("r-6", AdminReport.Status.PENDING);

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(1);
            cb.onSuccess(loaded);
            return null;
        }).when(reportRepo).getReportById(eq("r-6"), any(DomainCallback.class));

        viewModel.loadReport("r-6");

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(3);
            cb.onError(new Exception("TEMP_ERROR"));
            return null;
        }).when(reportRepo).resolveReport(eq("r-6"), eq("APPROVE"), eq("n"), any(DomainCallback.class));

        viewModel.resolveReport("r-6", "APPROVE", "n");
        viewModel.consumeError();

        AdminReportDetailUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertNull(state.getError());
    }

    @Test
    public void consumeError_whenNoError_leavesStateUntouched() {
        AdminReport loaded = report("r-7", AdminReport.Status.PENDING);

        doAnswer(invocation -> {
            DomainCallback<AdminReport> cb = invocation.getArgument(1);
            cb.onSuccess(loaded);
            return null;
        }).when(reportRepo).getReportById(eq("r-7"), any(DomainCallback.class));

        viewModel.loadReport("r-7");
        AdminReportDetailUiState before = viewModel.getUiState().getValue();

        viewModel.consumeError();

        AdminReportDetailUiState after = viewModel.getUiState().getValue();
        assertNotNull(after);
        assertEquals(before.getReport().getReportId(), after.getReport().getReportId());
        assertNull(after.getError());
    }

    private static AdminReport report(String id, AdminReport.Status status) {
        return new AdminReport(
                id,
                "session-9",
                "reporter-9",
                "Reporter",
                "reported-9",
                "Reported",
                AdminReport.Reason.SAFETY_CONCERN,
                "https://evidence/" + id,
                status,
                -10,
                "2026-05-08T10:00:00Z",
                status == AdminReport.Status.PENDING ? null : "admin-1",
                status == AdminReport.Status.PENDING ? null : "2026-05-08T10:30:00Z",
                status == AdminReport.Status.PENDING ? null : "handled"
        );
    }
}
