package com.walkmate.domain.walkpost;

import com.walkmate.domain.shared.exception.DomainException;

public enum PostVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    public static PostVisibility from(String raw) {
        if (raw == null) {
            throw new DomainException(WalkPostErrorCode.WALK_POST_INVALID_VISIBILITY);
        }
        for (PostVisibility v : values()) {
            if (v.name().equals(raw)) return v;
        }
        throw new DomainException(WalkPostErrorCode.WALK_POST_INVALID_VISIBILITY);
    }
}
