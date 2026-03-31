package com.walkmate.data.datasource.remote.dto.response.chatroom;

import com.google.gson.annotations.SerializedName;

/**
 * Remote DTO for a single chat message returned by the backend.
 * Mapped to domain model via ChatMessageDtoToDomainMapper.
 */
public class ChatMessageDto {

    @SerializedName("messageId")
    public String messageId;

    @SerializedName("senderId")
    public String senderId;

    @SerializedName("content")
    public String content;

    @SerializedName("timestampMs")
    public long timestampMs;
}
