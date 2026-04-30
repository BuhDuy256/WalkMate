package com.walkmate.presentation.dto.request.session;

import jakarta.validation.constraints.NotBlank;

public record VerifyPartnerQrRequest(@NotBlank String partnerQrToken) {}
