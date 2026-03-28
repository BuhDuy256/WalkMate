package com.walkmate.presentation.dto.request.chatroom;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record MarkChatMessagesReadRequest(
        @NotBlank(message = "Last read message ID is required")
        @JsonProperty("last_read_message_id")
        String lastReadMessageId
) {
}
