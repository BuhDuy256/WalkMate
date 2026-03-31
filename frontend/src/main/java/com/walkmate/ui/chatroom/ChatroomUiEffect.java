package com.walkmate.ui.chatroom;

/**
 * One-time side effects emitted by ChatroomViewModel and consumed by ChatroomActivity.
 *
 * Once the Activity handles an effect, it must call viewModel.consumeEffect() so the
 * same effect is not re-delivered on configuration change.
 */
public class ChatroomUiEffect {

    public enum Type {
        SCROLL_TO_BOTTOM,
        SHOW_ERROR
    }

    private final Type type;
    private final String message;  // non-null only for SHOW_ERROR

    private ChatroomUiEffect(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public static ChatroomUiEffect scrollToBottom() {
        return new ChatroomUiEffect(Type.SCROLL_TO_BOTTOM, null);
    }

    public static ChatroomUiEffect showError(String message) {
        return new ChatroomUiEffect(Type.SHOW_ERROR, message);
    }

    public Type getType()    { return type; }
    public String getMessage() { return message; }
}
