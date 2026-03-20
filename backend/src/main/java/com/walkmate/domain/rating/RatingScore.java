package com.walkmate.domain.rating;

/**
 * Value Object for rating score (1-5)
 */
public class RatingScore {
    private final int value;

    public RatingScore(int value) {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("Rating score must be between 1 and 5");
        }
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RatingScore)) return false;
        return value == ((RatingScore) obj).value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
