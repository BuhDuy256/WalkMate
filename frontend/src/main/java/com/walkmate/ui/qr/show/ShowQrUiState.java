package com.walkmate.ui.qr.show;

public class ShowQrUiState {
    private final boolean isLoading;
    private final String  qrToken;
    private final String  error;

    public ShowQrUiState(boolean isLoading, String qrToken, String error) {
        this.isLoading = isLoading;
        this.qrToken   = qrToken;
        this.error     = error;
    }

    public static ShowQrUiState loading() {
        return new ShowQrUiState(true, null, null);
    }

    public static ShowQrUiState success(String qrToken) {
        return new ShowQrUiState(false, qrToken, null);
    }

    public static ShowQrUiState error(String error) {
        return new ShowQrUiState(false, null, error);
    }

    public boolean isLoading() { return isLoading; }
    public String  getQrToken(){ return qrToken;   }
    public String  getError()  { return error;     }
}
