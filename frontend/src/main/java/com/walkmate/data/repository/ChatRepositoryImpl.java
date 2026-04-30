package com.walkmate.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.walkmate.BuildConfig;
import com.walkmate.data.datasource.remote.api.ChatApiService;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.chat.ChatMessageDto;
import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChatRepositoryImpl implements ChatRepository {

    private static final String TAG         = "ChatRepositoryImpl";
    private static final int    MAX_RETRIES = 5;

    // THREADING MODEL:
    // OkHttp's WebSocketListener callbacks run on OkHttp's internal background
    // thread — NEVER on the main thread. All MutableLiveData updates inside those
    // callbacks MUST use postValue(), which schedules delivery on the main thread.
    // The executor used for history HTTP calls is similarly a background thread;
    // postValue() is mandatory there too.
    private static final Gson GSON = new Gson();

    private final OkHttpClient   okHttpClient;
    private final String         baseWsUrl;      // e.g. "ws://host/api/v1/sessions/"
    private final ChatApiService chatApiService;
    private final ExecutorService executor       = Executors.newSingleThreadExecutor();

    private WebSocket activeWebSocket;
    private String    currentSessionId;
    private String    currentUserId;
    private int       retryCount = 0;

    private final MutableLiveData<List<ChatMessage>>        messagesLiveData =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ChatRepository.ConnectionState> connectionStateLiveData =
            new MutableLiveData<>(ChatRepository.ConnectionState.DISCONNECTED);

    public ChatRepositoryImpl(OkHttpClient okHttpClient, String baseWsUrl) {
        this.okHttpClient = okHttpClient;
        this.baseWsUrl    = baseWsUrl;

        // Build Retrofit on top of the already-authenticated OkHttpClient so we
        // reuse the same connection pool and auth interceptor — no second client.
        String httpBase = BuildConfig.BASE_URL;
        if (!httpBase.endsWith("/")) httpBase += "/";
        this.chatApiService = new Retrofit.Builder()
                .baseUrl(httpBase)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ChatApiService.class);
    }

    // ── ChatRepository ────────────────────────────────────────────────────────

    @Override
    public void loadHistory(String sessionId, String currentUserId,
                            DomainCallback<List<ChatMessage>> callback) {
        executor.execute(() -> {
            try {
                retrofit2.Response<ApiResponse<List<ChatMessageDto>>> response =
                        chatApiService.getChatHistory(sessionId, 50).execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().getData() != null) {

                    List<ChatMessage> messages = new ArrayList<>();
                    for (ChatMessageDto dto : response.body().getData()) {
                        ChatMessage msg = dtoToMessage(dto, sessionId, currentUserId);
                        if (msg != null) messages.add(msg);
                    }

                    // Seed LiveData with history before the WebSocket opens.
                    // postValue() is thread-safe; delivery to observers is async.
                    messagesLiveData.postValue(messages);
                    callback.onSuccess(messages);

                } else {
                    // No history or non-2xx — start with an empty list.
                    messagesLiveData.postValue(new ArrayList<>());
                    callback.onSuccess(new ArrayList<>());
                }

            } catch (Exception e) {
                Log.w(TAG, "History load failed: " + e.getMessage());
                messagesLiveData.postValue(new ArrayList<>());
                callback.onError(e);
            }
        });
    }

    @Override
    public void connect(String sessionId, String currentUserId) {
        if (activeWebSocket != null && sessionId.equals(currentSessionId)) return;
        disconnect();
        this.currentSessionId = sessionId;
        this.currentUserId    = currentUserId;
        this.retryCount       = 0;
        openWebSocket(sessionId);
    }

    @Override
    public void sendMessage(String content) {
        if (activeWebSocket == null) return;
        String json = "{\"type\":\"CHAT_MESSAGE\",\"content\":\""
                + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        activeWebSocket.send(json);
    }

    @Override
    public void disconnect() {
        if (activeWebSocket != null) {
            activeWebSocket.close(1000, "Session ended");
            activeWebSocket = null;
            // Only wipe the message list when tearing down a real connection.
            // If called internally by connect() with no active socket (the first
            // open, or after a reconnect), the history already seeded by
            // loadHistory() must be preserved — otherwise the user sees an empty
            // chat even though history arrived correctly.
            messagesLiveData.postValue(new ArrayList<>());
        }
        currentSessionId = null;
        connectionStateLiveData.postValue(ChatRepository.ConnectionState.DISCONNECTED);
    }

    @Override
    public LiveData<List<ChatMessage>> getMessages() {
        return messagesLiveData;
    }

    @Override
    public LiveData<ChatRepository.ConnectionState> getConnectionState() {
        return connectionStateLiveData;
    }

    // ── WebSocket internals ───────────────────────────────────────────────────

    private void openWebSocket(String sessionId) {
        connectionStateLiveData.postValue(ChatRepository.ConnectionState.CONNECTING);
        Request request = new Request.Builder()
                .url(baseWsUrl + sessionId + "/chat")
                .build();
        activeWebSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                retryCount = 0;
                connectionStateLiveData.postValue(ChatRepository.ConnectionState.CONNECTED);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                ChatMessage msg = parseMessage(text, currentUserId);
                if (msg != null) appendMessage(msg);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.w(TAG, "WebSocket failure: " + t.getMessage());
                connectionStateLiveData.postValue(ChatRepository.ConnectionState.RECONNECTING);
                scheduleReconnect(sessionId);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                connectionStateLiveData.postValue(ChatRepository.ConnectionState.DISCONNECTED);
            }
        });
    }

    private void scheduleReconnect(String sessionId) {
        if (retryCount >= MAX_RETRIES) {
            connectionStateLiveData.postValue(ChatRepository.ConnectionState.ERROR);
            return;
        }
        long delayMs = Math.min((long) Math.pow(2, retryCount) * 1_000L, 30_000L);
        retryCount++;
        new Handler(Looper.getMainLooper()).postDelayed(() -> openWebSocket(sessionId), delayMs);
    }

    // ── Message parsing ───────────────────────────────────────────────────────

    /**
     * Parses an inbound WebSocket frame. Uses {@link ChatMessageDto} as the
     * single source of truth for JSON field names — no bare string literals.
     */
    private ChatMessage parseMessage(String text, String userId) {
        try {
            ChatMessageDto dto = GSON.fromJson(text, ChatMessageDto.class);
            if (dto == null) return null;
            return dtoToMessage(dto, currentSessionId, userId);
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Failed to parse chat message: " + text, e);
            return null;
        }
    }

    private ChatMessage dtoToMessage(ChatMessageDto dto, String fallbackSessionId, String userId) {
        if (dto == null) return null;
        String messageId  = dto.messageId  != null ? dto.messageId  : "";
        String sessId     = dto.sessionId  != null ? dto.sessionId  : (fallbackSessionId != null ? fallbackSessionId : "");
        String senderId   = dto.senderId   != null ? dto.senderId   : "";
        String senderName = dto.senderName;
        String content    = dto.content    != null ? dto.content    : "";
        long   timestamp  = dto.timestamp  != 0    ? dto.timestamp  : System.currentTimeMillis();
        boolean isFromMe  = senderId.equals(userId);
        return new ChatMessage(messageId, sessId, senderId, senderName, content, timestamp, isFromMe);
    }

    /**
     * Appends a message to the live list, deduplicating by messageId.
     *
     * Dedup is necessary because the server echoes the sender's own message
     * back with the canonical server-assigned messageId. Without this check,
     * a sender would see their message twice (once from optimistic local state
     * and once from the server echo).
     *
     * Called from OkHttp's background thread — MUST use postValue().
     */
    private void appendMessage(ChatMessage msg) {
        List<ChatMessage> current = messagesLiveData.getValue();
        List<ChatMessage> updated = new ArrayList<>(current != null ? current : Collections.emptyList());

        for (ChatMessage existing : updated) {
            if (existing.getMessageId().equals(msg.getMessageId())) {
                return; // already in the list — skip
            }
        }

        updated.add(msg);
        messagesLiveData.postValue(updated);
    }
}
