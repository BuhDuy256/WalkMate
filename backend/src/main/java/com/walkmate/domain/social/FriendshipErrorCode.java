package com.walkmate.domain.social;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum FriendshipErrorCode implements ErrorCode {
    FRIEND_REQUEST_SELF_FORBIDDEN("Cannot send a friend request to yourself"),
    FRIEND_REQUEST_ALREADY_FRIENDS("You are already friends with this user"),
    FRIEND_REQUEST_ALREADY_PENDING("A friend request is already pending"),
    FRIEND_REQUEST_BLOCKED("Cannot send a friend request to a blocked user"),
    FRIEND_REQUEST_NOT_FOUND("Friend request not found"),
    FRIEND_REQUEST_NOT_PARTICIPANT("You are not a participant of this friend request"),
    FRIEND_REQUEST_ALREADY_RESOLVED("This friend request has already been resolved"),
    FRIEND_REMOVE_NOT_FRIENDS("You are not friends with this user");

    private final String message;

    FriendshipErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
