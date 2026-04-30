package com.walkmate.data.datasource.remote.dto.request.walksession;

public class VerifyPartnerQrRequest {
    private final String partnerQrToken;

    public VerifyPartnerQrRequest(String partnerQrToken) {
        this.partnerQrToken = partnerQrToken;
    }

    public String getPartnerQrToken() { return partnerQrToken; }
}
