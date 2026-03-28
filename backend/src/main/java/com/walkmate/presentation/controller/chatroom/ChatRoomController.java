package com.walkmate.presentation.controller.chatroom;

import com.walkmate.application.chatroom.ChatRoomCommandService;
import com.walkmate.application.chatroom.MarkChatMessagesReadCommand;
import com.walkmate.application.chatroom.SendChatMessageCommand;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.presentation.dto.request.chatroom.MarkChatMessagesReadRequest;
import com.walkmate.presentation.dto.request.chatroom.SendChatMessageRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomCommandService chatRoomCommandService;

    /**
     * POST /api/v1/chat/rooms/{roomId}/messages
     * Sends a message from the authenticated user to the specified chat room.
     * senderId is resolved from the JWT — never trusted from the request body.
     */
    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse<Void>> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String roomId,
            @Valid @RequestBody SendChatMessageRequest request) {
        chatRoomCommandService.sendMessage(
                new SendChatMessageCommand(roomId, principal.userId(), request.content())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    /**
     * POST /api/v1/chat/rooms/{roomId}/read
     * Marks all messages up to and including lastReadMessageId as read by the caller.
     * readerId is resolved from the JWT — only the recipient may mark messages as read.
     */
    @PostMapping("/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markMessagesAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String roomId,
            @Valid @RequestBody MarkChatMessagesReadRequest request) {
        chatRoomCommandService.markMessagesAsRead(
                new MarkChatMessagesReadCommand(roomId, principal.userId(), request.lastReadMessageId())
        );
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
