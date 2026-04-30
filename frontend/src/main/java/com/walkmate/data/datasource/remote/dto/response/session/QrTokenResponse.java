package com.walkmate.data.datasource.remote.dto.response.session;

public class QrTokenResponse {
    private String qrToken;
    private long   expiresInSeconds;

    public String getQrToken()         { return qrToken; }
    public long   getExpiresInSeconds(){ return expiresInSeconds; }
}
