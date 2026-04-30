package com.walkmate.presentation.dto.response.session;

public record QrTokenResponse(String qrToken, long expiresInSeconds) {}
