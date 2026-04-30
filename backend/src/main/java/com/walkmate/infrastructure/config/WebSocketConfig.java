package com.walkmate.infrastructure.config;

import com.walkmate.infrastructure.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw WebSocket endpoint for session-scoped chat.
 *
 * No STOMP or SockJS — clients connect with a plain ws:// upgrade.
 *
 * Authentication: handled entirely by Spring Security's BearerTokenAuthenticationFilter
 * on the HTTP upgrade request. SecurityConfig already requires authentication for
 * /api/v1/sessions/**, so any upgrade without a valid JWT is rejected at 401
 * before this handler is reached.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(chatWebSocketHandler, "/api/v1/sessions/*/chat")
                .setAllowedOriginPatterns("*");
    }
}
