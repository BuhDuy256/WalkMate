package com.walkmate.domain.rating;

/**
 * Value Object for rating comment
 */
public class RatingComment {
    private static final int MAX_LENGTH = 500;
    private final String value;

    public RatingComment(String value) {
        if (value != null && value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Comment cannot exceed " + MAX_LENGTH + " characters");
        }
        this.value = value == null ? "" : value.trim();
    }

    public String getValue() {
        return value;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }
}
