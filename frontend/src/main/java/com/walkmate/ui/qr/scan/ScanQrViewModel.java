package com.walkmate.ui.qr.scan;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSessionRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScanQrViewModel extends ViewModel {

    private final WalkSessionRepository         repository;
    private final MutableLiveData<ScanQrUiState> uiState = new MutableLiveData<>();

    private String sessionId;
    private String pendingToken;    // cached raw QR string — survives ERROR phase for Retry

    public ScanQrViewModel(WalkSessionRepository repository) {
        this.repository = repository;
        uiState.setValue(ScanQrUiState.idle());
    }

    public LiveData<ScanQrUiState> getUiState() { return uiState; }

    public void init(String sessionId) {
        this.sessionId = sessionId;
    }

    /** Transitions to SCANNING phase when the camera starts (before a QR is detected). */
    public void startScanning() {
        uiState.postValue(ScanQrUiState.scanning(pendingToken));
    }

    /** Called by ScanQrFragment when ML Kit successfully reads a QR string from the camera. */
    public void onQrDetected(String rawValue) {
        pendingToken = rawValue;
        callVerifyApi(rawValue);
    }

    /** Retries verification using the cached scanned token — no camera re-scan needed. */
    public void retryVerification() {
        if (pendingToken != null) callVerifyApi(pendingToken);
    }

    /** Resets to IDLE and clears the cached token — called from "Scan Again" secondary link. */
    public void resetToIdle() {
        pendingToken = null;
        uiState.setValue(ScanQrUiState.idle());
    }

    /** Called after the user taps "Continue Walk →" to clear internal state. */
    public void consumeVerification() {
        pendingToken = null;
    }

    private void callVerifyApi(String token) {
        uiState.postValue(ScanQrUiState.scanning(token));
        repository.verifyPartnerQr(sessionId, token, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                pendingToken = null;
                uiState.postValue(ScanQrUiState.verified(time));
            }

            @Override
            public void onError(Exception e) {
                // Keep pendingToken alive so Retry works without re-scanning
                uiState.postValue(ScanQrUiState.error(
                        friendlyMessage(e.getMessage()), pendingToken));
            }
        });
    }

    private static String friendlyMessage(String errorCode) {
        if (errorCode == null) return "Verification failed. Please try again.";
        if ("SESSION_QR_TOKEN_EXPIRED".equals(errorCode))
            return "QR code has expired. Ask your partner to refresh.";
        if ("SESSION_QR_TOKEN_INVALID".equals(errorCode))
            return "Invalid QR code. Make sure you scanned your partner's code.";
        if ("SESSION_QR_SELF_VERIFICATION".equals(errorCode))
            return "You can't scan your own QR code.";
        if ("SESSION_QR_ALREADY_VERIFIED".equals(errorCode))
            return "Partner has already been verified.";
        if ("SESSION_NOT_ACTIVE".equals(errorCode))
            return "Session is not in a verifiable state (already completed or cancelled).";
        return "Verification failed. Please try again.";
    }
}
