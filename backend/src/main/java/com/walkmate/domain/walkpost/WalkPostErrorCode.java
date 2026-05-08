package com.walkmate.domain.walkpost;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum WalkPostErrorCode implements ErrorCode {

    WALK_POST_NOT_FOUND("WALK_POST_NOT_FOUND",
            "Walk post not found"),
    WALK_POST_SESSION_NOT_FOUND("WALK_POST_SESSION_NOT_FOUND",
            "Walk session not found"),
    WALK_POST_AUTHOR_NOT_PARTICIPANT("WALK_POST_AUTHOR_NOT_PARTICIPANT",
            "You were not a participant in this session"),
    WALK_POST_PERSONAL_STATUS_NOT_COMPLETED("WALK_POST_PERSONAL_STATUS_NOT_COMPLETED",
            "You can only post after completing your walk"),
    WALK_POST_METRICS_NOT_FINALIZED("WALK_POST_METRICS_NOT_FINALIZED",
            "Your walk metrics have not been finalized yet"),
    WALK_POST_SESSION_NOT_POSTABLE("WALK_POST_SESSION_NOT_POSTABLE",
            "This session cannot be posted"),
    WALK_POST_DUPLICATED("WALK_POST_DUPLICATED",
            "You have already posted for this session"),
    WALK_POST_INVALID_VISIBILITY("WALK_POST_INVALID_VISIBILITY",
            "Invalid visibility value"),
    WALK_POST_CAPTION_TOO_LONG("WALK_POST_CAPTION_TOO_LONG",
            "Caption must not exceed 150 characters"),
    WALK_POST_FORBIDDEN("WALK_POST_FORBIDDEN",
            "You are not allowed to perform this action on this post");

    private final String code;
    private final String message;

    WalkPostErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
