package com.walkmate.application.chatroom;

/**
 * Issued by the WalkSession domain service when its session reaches any terminal state.
 * The caller is responsible for including this command in the same JDBC transaction
 * as the WalkSession terminal transition (§9.6 Invariant 1).
 */
public record CloseChatRoomBySessionCommand(
        String sessionId
) {
}
