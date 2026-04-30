package com.walkmate.infrastructure.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walkmate.application.chat.ChatCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Raw WebSocket handler for session-scoped chat.
 *
 * Lifecycle:
 *  afterConnectionEstablished — validate participant, cache userId/senderName,
 *                               register session in the in-memory registry.
 *  handleTextMessage          — parse CHAT_MESSAGE frame, persist via
 *                               ChatCommandService, broadcast to all peers.
 *  afterConnectionClosed      — deregister from the registry.
 *
 * Thread safety:
 *  ConcurrentHashMap + CopyOnWriteArraySet ensure safe concurrent access to
 *  the registry. Broadcast sends are individually wrapped in synchronized(peer)
 *  to prevent concurrent writes to the same WebSocketSession from two handler
 *  threads (e.g. both participants sending simultaneously).
 *
 * Authentication:
 *  Spring Security's BearerTokenAuthenticationFilter validates the JWT on the
 *  HTTP upgrade request before this handler is invoked. The resulting Principal
 *  is available via session.getPrincipal() — no manual JWT parsing here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // sessionId → set of active WebSocketSessions for that chat room
    private final Map<String, Set<WebSocketSession>> registry = new ConcurrentHashMap<>();

    private final ChatCommandService chatCommandService;
    private final ObjectMapper       objectMapper;

    // ── Attribute keys stored on the WebSocketSession ─────────────────────────
    private static final String ATTR_SESSION_ID   = "sessionId";
    private static final String ATTR_USER_ID      = "userId";
    private static final String ATTR_SENDER_NAME  = "senderName";

    // ── Connect ───────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        // Guard: Spring Security should have already rejected unauthenticated upgrades,
        // but defend in depth.
        if (session.getPrincipal() == null) {
            log.warn("WS upgrade without principal — closing: uri={}", session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String sessionId = extractSessionId(session);
        String userId    = extractUserId(session);

        try {
            chatCommandService.validateParticipant(sessionId, userId);
        } catch (DomainException e) {
            log.warn("WS connect rejected: sessionId={} userId={} reason={}",
                    sessionId, userId, e.getErrorCode().getCode());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String senderName = chatCommandService.getSenderName(userId);

        session.getAttributes().put(ATTR_SESSION_ID,  sessionId);
        session.getAttributes().put(ATTR_USER_ID,     userId);
        session.getAttributes().put(ATTR_SENDER_NAME, senderName);

        registry.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("WS connected: sessionId={} userId={}", sessionId, userId);
    }

    // ── Inbound message ───────────────────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        String sessionId  = (String) session.getAttributes().get(ATTR_SESSION_ID);
        String senderId   = (String) session.getAttributes().get(ATTR_USER_ID);
        String senderName = (String) session.getAttributes().get(ATTR_SENDER_NAME);

        if (sessionId == null || senderId == null) return; // connection was rejected during open

        // Parse inbound frame: {"type":"CHAT_MESSAGE","content":"..."}
        InboundFrame frame;
        try {
            frame = objectMapper.readValue(textMessage.getPayload(), InboundFrame.class);
        } catch (Exception e) {
            log.debug("Unparseable WS frame: {}", textMessage.getPayload());
            return;
        }

        if (!"CHAT_MESSAGE".equals(frame.type) || frame.content == null || frame.content.isBlank()) {
            return;
        }

        // Persist (also re-validates session is still open)
        ChatMessage saved;
        try {
            saved = chatCommandService.processMessage(sessionId, senderId, senderName, frame.content);
        } catch (DomainException e) {
            // Chat room closed mid-walk — notify sender and let client handle it
            sendError(session, e.getErrorCode().getCode());
            return;
        }

        // Build outbound JSON matching ChatMessageDto @SerializedName keys on Android
        OutboundFrame out = new OutboundFrame();
        out.messageId  = saved.getMessageId();
        out.sessionId  = saved.getSessionId();
        out.senderId   = saved.getSenderId();
        out.senderName = saved.getSenderName();
        out.content    = saved.getContent();
        out.timestamp  = saved.getSentAt().toEpochMilli();

        TextMessage outbound;
        try {
            outbound = new TextMessage(objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            log.error("Failed to serialize outbound frame: {}", e.getMessage());
            return;
        }

        // Broadcast to all connected participants (including sender for canonical echo)
        Set<WebSocketSession> bucket = registry.getOrDefault(sessionId, Collections.emptySet());
        for (WebSocketSession peer : bucket) {
            try {
                synchronized (peer) {
                    if (peer.isOpen()) peer.sendMessage(outbound);
                }
            } catch (IOException e) {
                log.warn("Broadcast failed to peer {}: {}", peer.getId(), e.getMessage());
            }
        }
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = (String) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId != null) {
            Set<WebSocketSession> bucket = registry.get(sessionId);
            if (bucket != null) {
                bucket.remove(session);
                if (bucket.isEmpty()) registry.remove(sessionId);
            }
        }
        log.debug("WS closed: sessionId={} status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS transport error: sessionId={} error={}",
                session.getAttributes().get(ATTR_SESSION_ID), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractSessionId(WebSocketSession session) {
        // URI pattern: /api/v1/sessions/{sessionId}/chat
        String path  = session.getUri().getPath();
        String[] parts = path.split("/");
        // parts: ["", "api", "v1", "sessions", "{sessionId}", "chat"]
        return parts[parts.length - 2];
    }

    /**
     * Extracts the userId from the WebSocket session's principal.
     *
     * WebSocketSession.getPrincipal() returns the Spring Security Authentication
     * (which implements java.security.Principal). UserPrincipal is a plain record
     * and does NOT implement Principal, so Authentication.getName() falls back to
     * UserPrincipal.toString() — giving "UserPrincipal[userId=..., email=...]".
     * We must cast through Authentication to reach the real UserPrincipal.userId().
     */
    private String extractUserId(WebSocketSession session) {
        java.security.Principal p = session.getPrincipal();
        if (p instanceof Authentication auth
                && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.userId();
        }
        return p != null ? p.getName() : null;
    }

    private void sendError(WebSocketSession session, String errorCode) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("error", errorCode));
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("Failed to send error frame: {}", e.getMessage());
        }
    }

    // ── Inner DTOs (Jackson-deserialized) ─────────────────────────────────────

    private static class InboundFrame {
        public String type;
        public String content;
    }

    private static class OutboundFrame {
        @JsonProperty("message_id")  public String messageId;
        @JsonProperty("session_id")  public String sessionId;
        @JsonProperty("sender_id")   public String senderId;
        @JsonProperty("sender_name") public String senderName;
        @JsonProperty("content")     public String content;
        @JsonProperty("timestamp")   public long   timestamp;
    }
}
