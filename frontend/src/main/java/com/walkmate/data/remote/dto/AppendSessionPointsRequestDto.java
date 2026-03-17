package com.walkmate.data.remote.dto;

import java.util.List;

public class AppendSessionPointsRequestDto {
    public List<AppendPointItemDto> points;
    public double totalDistance;
    public long totalDuration;

    public AppendSessionPointsRequestDto(List<AppendPointItemDto> points, double totalDistance, long totalDuration) {
        this.points = points;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
    }
}
