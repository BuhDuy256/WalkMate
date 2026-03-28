package com.walkmate.presentation.dto.request.chatroom;

import jakarta.validation.constraints.NotBlank;

public record SendChatMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        String content
) {
}
