package com.walkmate.data.datasource.remote.dto.response.chatroom;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Remote DTO for a ChatRoom returned by the backend.
 * Mapped to domain model via ChatRoomDtoToDomainMapper.
 */
public class ChatRoomDto {

    @SerializedName("chatRoomId")
    public String chatRoomId;

    @SerializedName("sessionId")
    public String sessionId;

    @SerializedName("participantA")
    public String participantA;

    @SerializedName("participantB")
    public String participantB;

    /** "OPEN" or "CLOSED" */
    @SerializedName("status")
    public String status;

    @SerializedName("messages")
    public List<ChatMessageDto> messages;
}
