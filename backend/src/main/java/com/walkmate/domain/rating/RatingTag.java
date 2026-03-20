package com.walkmate.domain.rating;

/**
 * Value Object for rating tags
 * Maps to DB enum: review_tag_type
 */
public enum RatingTag {
    FRIENDLY,
    PUNCTUAL,
    GOOD_CONVERSATION,
    RESPECTFUL,
    LATE,
    RUDE,
    UNCOMFORTABLE;

    public static RatingTag fromCode(String code) {
        try {
            return RatingTag.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid rating tag: " + code);
        }
    }

    public String toCode() {
        return this.name();
    }
}
