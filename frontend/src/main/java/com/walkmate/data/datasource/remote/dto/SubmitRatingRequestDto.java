package com.walkmate.data.datasource.remote.dto;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for submitting rating
 */
public class SubmitRatingRequestDto {
    private UUID userId;
    private UUID sessionId;
    private UUID revieweeId;
    private Integer score;
    private List<String> tags;
    private String note;

    public SubmitRatingRequestDto() {
    }

    public SubmitRatingRequestDto(UUID userId, UUID sessionId, UUID revieweeId, Integer score, List<String> tags, String note) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.revieweeId = revieweeId;
        this.score = score;
        this.tags = tags;
        this.note = note;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getRevieweeId() {
        return revieweeId;
    }

    public void setRevieweeId(UUID revieweeId) {
        this.revieweeId = revieweeId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
