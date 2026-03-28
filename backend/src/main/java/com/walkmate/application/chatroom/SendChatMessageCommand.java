package com.walkmate.application.chatroom;

public record SendChatMessageCommand(
        String chatRoomId,
        String senderId,
        String content
) {
}
