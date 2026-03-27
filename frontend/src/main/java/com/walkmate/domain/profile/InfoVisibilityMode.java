package com.walkmate.domain.profile;

public enum InfoVisibilityMode {
    PUBLIC,
    PRIVATE;

    public static InfoVisibilityMode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return PRIVATE;
        }
        return InfoVisibilityMode.valueOf(code.trim().toUpperCase());
    }
}
