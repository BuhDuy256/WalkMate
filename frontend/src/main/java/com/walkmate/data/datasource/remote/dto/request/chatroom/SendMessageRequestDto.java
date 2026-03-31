package com.walkmate.data.datasource.remote.dto.request.chatroom;

import com.google.gson.annotations.SerializedName;

/**
 * Request body DTO for POST sessions/{sessionId}/chatroom/messages.
 */
public class SendMessageRequestDto {

    @SerializedName("content")
    public final String content;

    public SendMessageRequestDto(String content) {
        this.content = content;
    }
}
