package com.walkmate.data.repository;

import com.walkmate.data.mapper.ChatMessageDtoToDomainMapper;
import com.walkmate.data.mapper.ChatRoomDtoToDomainMapper;
import com.walkmate.data.datasource.remote.dto.response.chatroom.ChatMessageDto;
import com.walkmate.data.datasource.remote.dto.response.chatroom.ChatRoomDto;
import com.walkmate.domain.chatroom.ChatMessage;
import com.walkmate.domain.chatroom.ChatRoom;
import com.walkmate.domain.chatroom.ChatRoomRepository;
import com.walkmate.domain.chatroom.ChatRoomStatus;
import com.walkmate.domain.shared.DomainCallback;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MOCK implementation — returns hardcoded data with a simulated 1-second network delay.
 * Replace each method body with real Retrofit calls (via ChatRoomApiService) when the
 * backend endpoint is ready.
 */
public class ChatRoomRepositoryImpl implements ChatRoomRepository {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // ---------------------------------------------------------------------------
    // Mock data
    // ---------------------------------------------------------------------------

    private static ChatRoomDto buildMockRoom(String sessionId) {
        ChatRoomDto room = new ChatRoomDto();
        room.chatRoomId = "chatroom-" + sessionId;
        room.sessionId = sessionId;
        room.participantA = "current-user";
        room.participantB = "partner-user";
        room.status = "OPEN";

        ChatMessageDto sys = new ChatMessageDto();
        sys.messageId = "msg-001";
        sys.senderId = "system";
        sys.content = "You've been matched! Say hello 👋";
        sys.timestampMs = System.currentTimeMillis() - 60_000;

        ChatMessageDto partner = new ChatMessageDto();
        partner.messageId = "msg-002";
        partner.senderId = "partner-user";
        partner.content = "Hey! Ready to walk?";
        partner.timestampMs = System.currentTimeMillis() - 30_000;

        room.messages = Arrays.asList(sys, partner);
        return room;
    }

    // ---------------------------------------------------------------------------
    // Interface methods
    // ---------------------------------------------------------------------------

    @Override
    public void getRoom(String sessionId, DomainCallback<ChatRoom> callback) {
        executor.execute(() -> {
            sleep();
            callback.onSuccess(ChatRoomDtoToDomainMapper.toDomain(buildMockRoom(sessionId)));
        });
    }

    @Override
    public void sendMessage(String sessionId, String content, DomainCallback<ChatMessage> callback) {
        executor.execute(() -> {
            sleep();
            ChatMessage sent = new ChatMessage(
                    "msg-" + System.currentTimeMillis(),
                    "current-user",
                    content,
                    System.currentTimeMillis()
            );
            callback.onSuccess(sent);
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
