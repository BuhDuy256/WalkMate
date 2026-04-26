package com.walkmate.application.social;

/** Lightweight result of a friendship-status lookup between two users. */
public record FriendshipStatusResult(String status, String pendingRequestId) {}
