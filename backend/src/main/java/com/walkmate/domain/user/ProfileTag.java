package com.walkmate.domain.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/** Immutable value type — one row in the profile_tag table. */
@Getter
@RequiredArgsConstructor
public class ProfileTag {
    private final UUID   userId;
    private final String tagName;
}
