package com.walkmate.ui.chatroom;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.chatroom.ChatMessage;
import com.walkmate.domain.chatroom.ChatRoom;
import com.walkmate.domain.chatroom.ChatRoomService;
import com.walkmate.domain.shared.DomainCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for ChatroomActivity.
 *
 * Coordinates user events → ChatRoomService → UiState.
 * Contains no Android framework dependencies except LiveData.
 */
public class ChatroomViewModel extends ViewModel {

    private static final String CURRENT_USER_ID = "current-user"; // replaced when auth is real

    private final ChatRoomService chatRoomService;
    private final MutableLiveData<ChatroomUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<ChatroomUiEffect> uiEffect = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String sessionId;
    private ChatroomViewData viewData;

    public ChatroomViewModel(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<ChatroomUiState> getUiState()   { return uiState; }
    public LiveData<ChatroomUiEffect> getUiEffect() { return uiEffect; }

    /**
     * Called once from Activity.onCreate() with data extracted from the Intent.
     * Triggers the initial room load.
     */
    public void init(ChatroomViewData data) {
        if (this.viewData != null) return; // already initialised (config change)
        this.viewData = data;
        this.sessionId = data.getSessionId();
        loadRoom();
    }

    /**
     * Sends a message. Posts an optimistic outgoing bubble immediately, then
     * reloads the full room on success (server may append additional messages).
     */
    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;

        // Optimistic: append the outgoing message to the current list immediately
        ChatroomUiState current = uiState.getValue();
        if (current != null) {
            List<ChatroomUiState.MessageSnapshot> optimistic = new ArrayList<>();
            if (current.getMessages() != null) optimistic.addAll(current.getMessages());
            optimistic.add(buildOptimisticSnapshot(content));
            uiState.postValue(new ChatroomUiState(
                    false,
                    current.isChatOpen(),
                    current.isShowMatchBanner(),
                    current.getCountdownEndEpochMs(),
                    current.getPartner(),
                    optimistic,
                    null
            ));
            uiEffect.postValue(ChatroomUiEffect.scrollToBottom());
        }

        executor.execute(() ->
            chatRoomService.sendMessage(sessionId, content.trim(), new DomainCallback<ChatMessage>() {
                @Override
                public void onSuccess(ChatMessage result) {
                    // Reload to get the authoritative server state
                    loadRoom();
                }

                @Override
                public void onError(Exception error) {
                    uiEffect.postValue(ChatroomUiEffect.showError(error.getMessage()));
                }
            })
        );
    }

    /** Clears the current error so it is not re-shown after a config change. */
    public void consumeError() {
        ChatroomUiState current = uiState.getValue();
        if (current == null || current.getError() == null) return;
        uiState.postValue(new ChatroomUiState(
                current.isLoading(),
                current.isChatOpen(),
                current.isShowMatchBanner(),
                current.getCountdownEndEpochMs(),
                current.getPartner(),
                current.getMessages(),
                null
        ));
    }

    /** Clears the one-time effect so it is not re-delivered after a config change. */
    public void consumeEffect() {
        uiEffect.postValue(null);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void loadRoom() {
        uiState.postValue(ChatroomUiState.loading());

        executor.execute(() ->
            chatRoomService.loadRoom(sessionId, new DomainCallback<ChatRoom>() {
                @Override
                public void onSuccess(ChatRoom room) {
                    uiState.postValue(buildState(room));
                    uiEffect.postValue(ChatroomUiEffect.scrollToBottom());
                }

                @Override
                public void onError(Exception error) {
                    uiState.postValue(new ChatroomUiState(
                            false, true, false, 0L, null, null, error.getMessage()));
                }
            })
        );
    }

    private ChatroomUiState buildState(ChatRoom room) {
        ChatroomUiState.PartnerSnapshot partner = new ChatroomUiState.PartnerSnapshot(
                room.getParticipantB(),
                viewData.getPartnerName(),
                viewData.getPartnerAvatarUrl(),
                true // isOnline: real status comes from presence API when available
        );

        List<ChatroomUiState.MessageSnapshot> snapshots = new ArrayList<>();
        for (ChatMessage msg : room.getMessages()) {
            snapshots.add(toSnapshot(msg));
        }

        long countdown = viewData.isSessionPending() ? viewData.getScheduledTimeEpochMs() : 0L;

        return new ChatroomUiState(
                false,
                room.isOpen(),
                viewData.isSessionPending(),
                countdown,
                partner,
                snapshots,
                null
        );
    }

    private ChatroomUiState.MessageSnapshot toSnapshot(ChatMessage msg) {
        boolean isSystem = "system".equalsIgnoreCase(msg.getSenderId());
        boolean isOwn    = CURRENT_USER_ID.equals(msg.getSenderId());

        String senderName = isSystem ? "System"
                : isOwn     ? "You"
                : viewData.getPartnerName();

        String avatarUrl = isOwn ? null : viewData.getPartnerAvatarUrl();

        return new ChatroomUiState.MessageSnapshot(
                msg.getMessageId(),
                msg.getSenderId(),
                senderName,
                avatarUrl,
                msg.getContent(),
                formatTime(msg.getTimestampMs()),
                isOwn,
                isSystem
        );
    }

    private ChatroomUiState.MessageSnapshot buildOptimisticSnapshot(String content) {
        return new ChatroomUiState.MessageSnapshot(
                "optimistic-" + System.currentTimeMillis(),
                CURRENT_USER_ID,
                "You",
                null,
                content,
                formatTime(System.currentTimeMillis()),
                true,
                false
        );
    }

    private static String formatTime(long epochMs) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(epochMs));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
