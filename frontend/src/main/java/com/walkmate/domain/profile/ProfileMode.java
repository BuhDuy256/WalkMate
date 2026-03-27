package com.walkmate.domain.profile;

public enum ProfileMode {
    PUBLIC,
    PRIVATE;

    public static ProfileMode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return PUBLIC;
        }
        return ProfileMode.valueOf(code.trim().toUpperCase());
    }
}
