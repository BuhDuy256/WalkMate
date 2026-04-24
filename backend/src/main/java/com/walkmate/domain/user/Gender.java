package com.walkmate.domain.user;

public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    PREFER_NOT_TO_SAY,
    /** "Flexible / any" — maps from frontend value 'z' during onboarding. */
    ANY
}
