package com.walkmate.application.chatroom;

public record MarkChatMessagesReadCommand(
        String chatRoomId,
        String readerId,
        String lastReadMessageId
) {
}
