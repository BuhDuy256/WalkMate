package com.walkmate.domain.profile;

public enum InfoVisibilityMode {
    PUBLIC,
    PRIVATE;

    public static InfoVisibilityMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Info visibility mode is required");
        }
        return InfoVisibilityMode.valueOf(code.trim().toUpperCase());
    }
}
