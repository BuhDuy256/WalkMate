package com.walkmate.domain.profile;

public enum ProfileMode {
    PUBLIC,
    PRIVATE;

    public static ProfileMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Profile mode is required");
        }
        return ProfileMode.valueOf(code.trim().toUpperCase());
    }
}
