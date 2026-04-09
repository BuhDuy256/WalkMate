# Phase 15 Report — WebSocket Chat Integration
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Scope:** Full in-session chat backed by an OkHttp WebSocket connection scoped to session ID. Closes Gap 4.6.

---

## Files Created

| File | Package / Path |
|---|---|
| `domain/chat/ChatMessage.java` | `com.walkmate.domain.chat` |
| `domain/chat/ChatRepository.java` | `com.walkmate.domain.chat` (interface) |
| `domain/chat/ChatUiState.java` | `com.walkmate.domain.chat` |
| `data/repository/ChatRepositoryImpl.java` | `com.walkmate.data.repository` |
| `ui/chat/ChatViewModel.java` | `com.walkmate.ui.chat` |
| `ui/chat/ChatViewModelFactory.java` | `com.walkmate.ui.chat` |
| `ui/chat/ChatFragment.java` | `com.walkmate.ui.chat` |
| `ui/chat/ChatAdapter.java` | `com.walkmate.ui.chat` |
| `res/layout/fragment_chat.xml` | Chat screen layout |
| `res/layout/item_chat_mine.xml` | Right-aligned bubble (sent by me) |
| `res/layout/item_chat_theirs.xml` | Left-aligned bubble (partner) |
| `res/drawable/bg_bubble_mine.xml` | Orange gradient bubble drawable |
| `res/drawable/bg_bubble_theirs.xml` | White outlined bubble drawable |
| `res/drawable/ic_send.xml` | Send icon vector drawable |

---

## Files Modified

| File | Change |
|---|---|
| `WalkMateApplication.java` | Added `sharedOkHttpClient`, `chatRepository` fields; added `getOkHttpClient()`, `getBaseWsUrl()`, `getChatRepository()` |
| `ui/matches/session/SessionFragment.java` | Replaced "coming soon" Toast with NavController navigation to `chatFragment` |
| `res/navigation/nav_graph.xml` | Added `chatFragment` destination with `SESSION_ID` and `CURRENT_USER_ID` string arguments |

---

## "Coming Soon" Toast Removed

Confirmed. The listener previously was:
```java
adapter.setOnChatClickListener(session ->
    Toast.makeText(requireContext(), R.string.session_chat_coming_soon, Toast.LENGTH_SHORT).show());
```

It is now:
```java
adapter.setOnChatClickListener(session -> {
    if (session.getStatus() == WalkSession.Status.ACTIVE ||
            session.getStatus() == WalkSession.Status.PENDING) {
        // ... navigate to chatFragment
    }
});
```

The `session_chat_coming_soon` string resource is left in `strings.xml` (unused) and can be cleaned up in a later pass.

---

## OkHttpClient Reuse

`WalkMateApplication.getOkHttpClient()` returns a lazily-initialized singleton `OkHttpClient` with:
- `HttpLoggingInterceptor` (BASIC level)
- `AuthInterceptor` (Bearer JWT from `SessionManager`)

`getChatRepository()` passes this singleton into `ChatRepositoryImpl`. No second connection pool is created.

---

## WebSocket URL Pattern

```
ws://{host}:{port}/api/v1/sessions/{sessionId}/chat
```

Derived at runtime via `WalkMateApplication.getBaseWsUrl()`:
```java
String wsBase = BuildConfig.BASE_URL
    .replace("https://", "wss://")
    .replace("http://",  "ws://");
// wsBase = "ws://192.168.x.x:8080/"
// Full URL = wsBase + "api/v1/sessions/" + sessionId + "/chat"
```

---

## Reconnect Strategy

| Parameter | Value |
|---|---|
| Max retries | 5 |
| Backoff formula | `min(2^n × 1000 ms, 30 000 ms)` |
| Delays | 1 s → 2 s → 4 s → 8 s → 16 s (capped at 30 s) |
| Terminal state after exhaustion | `ConnectionState.ERROR` |

Reconnect is triggered from `WebSocketListener.onFailure()` only. Normal `onClosed()` does **not** reconnect.

---

## `ChatViewModel.onCleared()` Calls `disconnect()`

Confirmed in `ChatViewModel`:
```java
@Override
protected void onCleared() {
    chatRepository.disconnect();
}
```

This fires when the Fragment's ViewModel is destroyed (back navigation from ChatFragment), ensuring the WebSocket is closed cleanly.

---

## Backend WebSocket Message Schema (Assumed)

Incoming JSON message from backend:
```json
{
  "message_id": "uuid",
  "session_id": "uuid",
  "sender_id":  "uuid",
  "sender_name": "Minh Anh",   // nullable
  "content":    "Hello!",
  "timestamp":  1712650000000  // epoch ms
}
```

Outgoing message to backend (on `sendMessage()`):
```json
{ "type": "CHAT_MESSAGE", "content": "Hello!" }
```

Fields not present in the JSON are defaulted: `message_id` → `""`, `session_id` → current session, `timestamp` → `System.currentTimeMillis()`. The schema is derived from common WebSocket chat patterns; the backend contract should be verified and any field name differences corrected once the backend endpoint is live.

---

## Navigation Strategy

`ChatFragment` is registered as a first-class destination in `nav_graph.xml`. Navigation from `SessionFragment` (which is nested in a `ViewPager2` inside `MatchesFragment`) uses:
```java
Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
    .navigate(R.id.chatFragment, args);
```

This keeps navigation inside the Jetpack NavController's back stack so the system Back button pops back to `MatchesFragment` naturally.
