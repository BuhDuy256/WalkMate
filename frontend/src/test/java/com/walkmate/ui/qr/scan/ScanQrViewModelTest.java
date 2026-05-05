package com.walkmate.ui.qr.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSessionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ScanQrViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private WalkSessionRepository repository;

    private ScanQrViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        viewModel = new ScanQrViewModel(repository);
        viewModel.init("session-1");
    }

    @Test
    public void startScanning_setsScanningState() {
        viewModel.startScanning();
        ScanQrUiState state = viewModel.getUiState().getValue();
        assertEquals(ScanQrUiState.Kind.SCANNING, state.kind);
    }

    @Test
    public void onQrDetected_success_updatesToVerified() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(2);
            cb.onSuccess(null);
            return null;
        }).when(repository).verifyPartnerQr(anyString(), anyString(), any(DomainCallback.class));

        viewModel.onQrDetected("token-123");
        ScanQrUiState state = viewModel.getUiState().getValue();
        assertEquals(ScanQrUiState.Kind.VERIFIED, state.kind);
        assertNull(viewModel.getUiState().getValue().getPendingToken());
    }

    @Test
    public void onQrDetected_error_keepsPendingTokenAndShowsError() {
        doAnswer(invocation -> {
            DomainCallback<Void> cb = invocation.getArgument(2);
            cb.onError(new Exception("SESSION_QR_TOKEN_INVALID"));
            return null;
        }).when(repository).verifyPartnerQr(anyString(), anyString(), any(DomainCallback.class));

        viewModel.onQrDetected("token-xyz");
        ScanQrUiState state = viewModel.getUiState().getValue();
        assertEquals(ScanQrUiState.Kind.ERROR, state.kind);
        // pending token preserved for retry
        assertEquals("token-xyz", state.getPendingToken());
    }
}
