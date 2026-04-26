package com.walkmate.presentation.dto.response.social;

/**
 * Enriched representation of a pending friend request, with resolved sender/receiver profile data.
 *
 * For incoming requests  : senderName / senderAvatarUrl → requester's profile.
 * For outgoing requests  : senderName / senderAvatarUrl → addressee's profile
 *                          (adapter convention: "show the other person's name via senderName").
 */
public record FriendRequestResponse(
        String requestId,
        String senderId,
        String senderName,
        String senderAvatarUrl,
        String receiverId,
        String status,
        String createdAt
) {}
