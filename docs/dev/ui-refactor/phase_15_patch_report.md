# Phase 15 — Patch Report
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Scope:** Two defects identified in Phase 15 post-review; both resolved.

---

```markdown
## Fix 1 — Assumed Data Schema Made Explicit (Maintenance Risk)

### Root Cause
`ChatRepositoryImpl.parseMessage()` decoded inbound WebSocket frames using bare
`org.json.JSONObject.optString("message_id", ...)` calls. The JSON key strings
("message_id", "sender_id", "content", "timestamp", etc.) existed only as
scattered string literals inside a private method — invisible to any reviewer
looking at the domain model, and with no link to a backend contract.

### Risk
If the backend uses different field names (e.g. `"id"` instead of `"message_id"`,
`"createdAt"` instead of `"timestamp"`), the parser silently returns empty/default
values. No compile error, no runtime exception — messages arrive as blank bubbles.

### Fix Applied

**New file:** `data/datasource/remote/dto/response/chat/ChatMessageDto.java`

```java
// TODO: CRITICAL — Verify these @SerializedName keys against the final
// Backend API contract (Swagger / Postman) before release.
public class ChatMessageDto {
    @SerializedName("message_id")  public String messageId;
    @SerializedName("session_id")  public String sessionId;
    @SerializedName("sender_id")   public String senderId;
    @SerializedName("sender_name") public String senderName;
    @SerializedName("content")     public String content;
    @SerializedName("timestamp")   public long   timestamp;
}
```

All six JSON field names now live in **one place** with `@SerializedName` annotations —
consistent with every other remote DTO in the project (e.g. `WalkSessionResponse`,
`UserProfileResponse`).

**`ChatRepositoryImpl.parseMessage()`** updated from manual `JSONObject` parsing to:
```java
ChatMessageDto dto = GSON.fromJson(text, ChatMessageDto.class);
```
No bare key strings remain in the parsing logic.

**`ChatMessage.java`** — added visible contract header comment:
```java
// TODO: CRITICAL - Verify these @SerializedName keys against the final
// Backend API contract (Swagger / Postman) before release.
// JSON field names are defined in: ChatMessageDto.java
```

**Files changed:**
| File | Change |
|---|---|
| `ChatMessageDto.java` (NEW) | DTO with `@SerializedName` for all 6 fields + CRITICAL TODO |
| `ChatRepositoryImpl.java` | `parseMessage()` replaced `JSONObject` with `GSON.fromJson(text, ChatMessageDto.class)` |
| `ChatMessage.java` | Added schema contract warning comment |

---

## Fix 2 — Thread Safety: `postValue()` vs `setValue()` Enforced

### Root Cause
The Phase 15 implementation was already **technically correct** — every
`MutableLiveData` update inside `WebSocketListener` callbacks used `postValue()`.
However, there were **zero comments** explaining why. A future maintainer who
"simplifies" `postValue()` to `setValue()` inside `onMessage()`, `onOpen()`,
`onFailure()`, or `onClosed()` would cause an immediate crash:

```
android.view.ViewRootImpl$CalledFromWrongThreadException:
Only the original thread that created a view hierarchy can touch its views.
```

OkHttp dispatches all WebSocketListener callbacks on its own internal thread pool —
never the Android main thread.

### Fix Applied

A class-level threading model comment was added to `ChatRepositoryImpl`:

```java
// THREADING MODEL:
// OkHttp's WebSocketListener callbacks (onOpen, onMessage, onFailure, onClosed)
// are invoked on OkHttp's internal background thread — NEVER on the main thread.
// Therefore ALL MutableLiveData updates inside those callbacks MUST use postValue(),
// which schedules delivery on the main thread via a Handler.
// Using setValue() from a background thread throws CalledFromWrongThreadException.
```

Per-call-site comments were also added at **every** `postValue()` inside the listener:

```java
@Override public void onOpen(...) {
    // OkHttp background thread — postValue() is mandatory here.
    // setValue() would throw CalledFromWrongThreadException.
    connectionStateLiveData.postValue(CONNECTED);
}

@Override public void onMessage(...) {
    // OkHttp background thread — appendMessage() internally uses postValue().
    ...
}

@Override public void onFailure(...) {
    // OkHttp background thread — postValue() is mandatory here.
    ...
}

@Override public void onClosed(...) {
    // OkHttp background thread — postValue() is mandatory here.
    ...
}
```

`appendMessage()` also received a Javadoc + inline comment:
```java
/**
 * Called from OkHttp's background thread — MUST use postValue(), never setValue().
 */
// postValue() — thread-safe; schedules UI delivery on the main thread.
messagesLiveData.postValue(updated);
```

**Files changed:**
| File | Change |
|---|---|
| `ChatRepositoryImpl.java` | Added class-level threading model comment + per-call-site `postValue()` enforcement comments |

---

## Summary

| # | Time Bomb | Status |
|---|---|---|
| 1 | Bare JSON key strings — silent misparse if backend renames fields | ✅ Defused — `ChatMessageDto` with `@SerializedName` is now the single source of truth |
| 2 | No guard against accidental `setValue()` on background thread | ✅ Defused — class-level and per-call-site comments lock in `postValue()` intent |
```
