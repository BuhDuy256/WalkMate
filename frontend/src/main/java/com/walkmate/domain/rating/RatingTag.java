package com.walkmate.domain.rating;


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

    public String toDisplayName() {
        return switch (this) {
            case FRIENDLY -> "Friendly";
            case PUNCTUAL -> "On-time";
            case GOOD_CONVERSATION -> "Good conversation";
            case RESPECTFUL -> "Respectful";
            case LATE -> "Late";
            case RUDE -> "Rude";
            case UNCOMFORTABLE -> "Uncomfortable";
        };
    }
}
