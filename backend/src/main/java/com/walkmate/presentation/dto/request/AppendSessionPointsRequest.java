package com.walkmate.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record AppendSessionPointsRequest(
    @NotEmpty List<@Valid RoutePointDto> points,
    @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal totalDistance,
    @Min(0) long totalDuration) {
  public record RoutePointDto(
      @Min(0) int pointOrder,
      @NotNull Double lat,
      @NotNull Double lng,
      @Min(0) long time) {
  }
}
