package com.walkmate.data.mapper;

import com.walkmate.data.datasource.remote.dto.response.chatroom.ChatMessageDto;
import com.walkmate.domain.chatroom.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps ChatMessageDto (remote) → ChatMessage (domain).
 *
 * DTOs must never leave the data layer; this mapper enforces that boundary.
 */
public final class ChatMessageDtoToDomainMapper {

    private ChatMessageDtoToDomainMapper() {}

    public static ChatMessage toDomain(ChatMessageDto dto) {
        return new ChatMessage(
                dto.messageId,
                dto.senderId,
                dto.content,
                dto.timestampMs
        );
    }

    public static List<ChatMessage> toDomainList(List<ChatMessageDto> dtos) {
        List<ChatMessage> result = new ArrayList<>();
        if (dtos == null) return result;
        for (ChatMessageDto dto : dtos) {
            result.add(toDomain(dto));
        }
        return result;
    }
}
