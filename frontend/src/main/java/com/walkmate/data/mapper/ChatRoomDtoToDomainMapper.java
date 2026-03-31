package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.chatroom.ChatRoomDto;
import com.walkmate.domain.chatroom.ChatRoom;
import com.walkmate.domain.chatroom.ChatRoomStatus;

/**
 * Maps ChatRoomDto (remote) → ChatRoom (domain).
 *
 * DTOs must never leave the data layer; this mapper enforces that boundary.
 */
public final class ChatRoomDtoToDomainMapper {

    private ChatRoomDtoToDomainMapper() {}

    public static ChatRoom toDomain(ChatRoomDto dto) {
        ChatRoomStatus status = "CLOSED".equalsIgnoreCase(dto.status)
                ? ChatRoomStatus.CLOSED
                : ChatRoomStatus.OPEN;

        return new ChatRoom(
                dto.chatRoomId,
                dto.sessionId,
                dto.participantA,
                dto.participantB,
                status,
                ChatMessageDtoToDomainMapper.toDomainList(dto.messages)
        );
    }
}
