package com.walkmate.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.walkmate.data.datasource.remote.dto.response.chat.ChatMessageDto;
import com.walkmate.domain.chat.ChatMessage;
import com.walkmate.domain.chat.ChatRepository;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ChatRepositoryImpl implements ChatRepository {

    private static final String TAG = "ChatRepositoryImpl";
    private static final int MAX_RETRIES = 5;

    // THREADING MODEL:
    // OkHttp's WebSocketListener callbacks (onOpen, onMessage, onFailure, onClosed)
    // are invoked on OkHttp's internal background thread — NEVER on the main thread.
    // Therefore ALL MutableLiveData updates inside those callbacks MUST use postValue(),
    // which schedules delivery on the main thread via a Handler.
    // Using setValue() from a background thread throws CalledFromWrongThreadException.
    private static final Gson GSON = new Gson();

    private final OkHttpClient okHttpClient;
    private final String baseWsUrl;   // e.g. "ws://192.168.x.x:8080/api/v1/sessions/"

    private WebSocket activeWebSocket;
    private String currentSessionId;
    private String currentUserId;
    private int retryCount = 0;

    private final MutableLiveData<List<ChatMessage>> messagesLiveData =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ChatRepository.ConnectionState> connectionStateLiveData =
            new MutableLiveData<>(ChatRepository.ConnectionState.DISCONNECTED);

    public ChatRepositoryImpl(OkHttpClient okHttpClient, String baseWsUrl) {
        this.okHttpClient = okHttpClient;
        this.baseWsUrl    = baseWsUrl;
    }

    // ── ChatRepository ────────────────────────────────────────────────────────

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
        // Build JSON payload: {"type":"CHAT_MESSAGE","content":"..."}
        String json = "{\"type\":\"CHAT_MESSAGE\",\"content\":\""
                + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        activeWebSocket.send(json);
    }

    @Override
    public void disconnect() {
        if (activeWebSocket != null) {
            activeWebSocket.close(1000, "Session ended");
            activeWebSocket = null;
        }
        currentSessionId = null;
        messagesLiveData.postValue(new ArrayList<>());
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
        // postValue() — called before newWebSocket(); still on whichever thread connect() was
        // invoked from. Safe: postValue() is always thread-safe.
        connectionStateLiveData.postValue(ChatRepository.ConnectionState.CONNECTING);
        Request request = new Request.Builder()
                .url(baseWsUrl + sessionId + "/chat")
                .build();
        activeWebSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                // OkHttp background thread — postValue() is mandatory here.
                // setValue() would throw CalledFromWrongThreadException.
                retryCount = 0;
                connectionStateLiveData.postValue(ChatRepository.ConnectionState.CONNECTED);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                // OkHttp background thread — appendMessage() internally uses postValue().
                ChatMessage msg = parseMessage(text, currentUserId);
                if (msg != null) appendMessage(msg);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                // OkHttp background thread — postValue() is mandatory here.
                Log.w(TAG, "WebSocket failure: " + t.getMessage());
                connectionStateLiveData.postValue(ChatRepository.ConnectionState.RECONNECTING);
                scheduleReconnect(sessionId);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                // OkHttp background thread — postValue() is mandatory here.
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

    /**
     * Parses an inbound WebSocket text frame into a {@link ChatMessage}.
     *
     * Uses {@link ChatMessageDto} (Gson, @SerializedName) as the single source of
     * truth for JSON field names — no bare string literals here.
     *
     * TODO: CRITICAL — If the backend field names differ from those declared in
     * ChatMessageDto's @SerializedName annotations, update them there before release.
     */
    private ChatMessage parseMessage(String text, String userId) {
        try {
            ChatMessageDto dto = GSON.fromJson(text, ChatMessageDto.class);
            if (dto == null) return null;

            String messageId  = dto.messageId  != null ? dto.messageId  : "";
            String sessId     = dto.sessionId   != null ? dto.sessionId  : (currentSessionId != null ? currentSessionId : "");
            String senderId   = dto.senderId    != null ? dto.senderId   : "";
            String senderName = dto.senderName; // already null if absent
            String content    = dto.content     != null ? dto.content    : "";
            long   timestamp  = dto.timestamp  != 0    ? dto.timestamp  : System.currentTimeMillis();
            boolean isFromMe  = senderId.equals(userId);

            return new ChatMessage(messageId, sessId, senderId, senderName, content, timestamp, isFromMe);
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Failed to parse chat message: " + text, e);
            return null;
        }
    }

    /**
     * Appends a message to the live list.
     * Called from OkHttp's background thread — MUST use postValue(), never setValue().
     */
    private void appendMessage(ChatMessage msg) {
        List<ChatMessage> current = messagesLiveData.getValue();
        List<ChatMessage> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
        updated.add(msg);
        // postValue() — thread-safe; schedules UI delivery on the main thread.
        messagesLiveData.postValue(updated);
    }
}
