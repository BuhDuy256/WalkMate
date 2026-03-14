package com.walkmate.presentation.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CompleteSessionRequest(
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal distance,
    @Min(0) long duration) {
}
