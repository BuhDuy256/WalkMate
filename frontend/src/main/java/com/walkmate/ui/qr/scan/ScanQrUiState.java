package com.walkmate.ui.qr.scan;

public class ScanQrUiState {

    public enum Phase { IDLE, SCANNING, VERIFIED, ERROR }

    private final Phase  phase;
    private final String verifiedAt;    // HH:mm, non-null only on VERIFIED
    private final String error;         // non-null only on ERROR
    private final String scannedToken;  // raw QR string — survives ERROR so Retry works

    private ScanQrUiState(Phase phase, String verifiedAt, String error, String scannedToken) {
        this.phase        = phase;
        this.verifiedAt   = verifiedAt;
        this.error        = error;
        this.scannedToken = scannedToken;
    }

    public static ScanQrUiState idle() {
        return new ScanQrUiState(Phase.IDLE, null, null, null);
    }

    public static ScanQrUiState scanning(String scannedToken) {
        return new ScanQrUiState(Phase.SCANNING, null, null, scannedToken);
    }

    public static ScanQrUiState verified(String verifiedAt) {
        return new ScanQrUiState(Phase.VERIFIED, verifiedAt, null, null);
    }

    public static ScanQrUiState error(String errorMessage, String scannedToken) {
        return new ScanQrUiState(Phase.ERROR, null, errorMessage, scannedToken);
    }

    public Phase  getPhase()        { return phase;        }
    public String getVerifiedAt()   { return verifiedAt;   }
    public String getError()        { return error;        }
    public String getScannedToken() { return scannedToken; }
}
