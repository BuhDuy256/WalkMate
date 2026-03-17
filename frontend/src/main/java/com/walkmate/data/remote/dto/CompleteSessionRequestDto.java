package com.walkmate.data.remote.dto;

public class CompleteSessionRequestDto {
    public double distance;
    public long duration;

    public CompleteSessionRequestDto(double distance, long duration) {
        this.distance = distance;
        this.duration = duration;
    }
}
