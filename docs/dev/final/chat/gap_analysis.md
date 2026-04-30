# Chat System — Gap Analysis

**Date:** 2026-04-30  
**Scope:** End-to-end Chat feature tied to Walk Session lifecycle  
**Target UI:** `docs/ui/new/CHAT/` (Figma Make output)

---

## TL;DR — Direct Answers to Your Questions

| Question | Answer |
|---|---|
| Does the current backend support Realtime Chat? | **NO.** Zero WebSocket infrastructure exists. The backend only manages room lifecycle (open/close). |
| Do I need a MongoDB migration? | **NO.** MongoDB is schema-less. No Flyway migration needed. Collections auto-create on first insert. Only programmatic index declaration is needed (explained below). |
| Is the frontend chat code ready to connect? | **Structurally yes, but it will always fail** — the WebSocket endpoint it targets does not exist on the server. |

---

## 1. What Already Exists

### 1.1 Backend

| File | Status | What it does |
|---|---|---|
| `domain/chat/ChatRoomRepository.java` | ✅ Done | Port interface: `initRoom(sessionId)`, `closeRoom(sessionId)` |
| `infrastructure/repository/chat/document/ChatRoomDocument.java` | ✅ Done | MongoDB `@Document` — `chat_rooms` collection (sessionId, status OPEN/CLOSED, timestamps) |
| `infrastructure/repository/chat/MongoChatRoomRepository.java` | ✅ Done | MongoDB adapter, upsert-idempotent write, dispatched via afterCommit hooks |
| `application/proposal/MatchingCommandService.java` (line 235) | ✅ Done | Calls `initRoom()` when a session is created from a matched proposal |
| `application/session/SessionCommandService.java` (lines 110, 170, 237, 259) | ✅ Done | Calls `closeRoom()` on cancel, complete, and auto-expire |
| `build.gradle` | ✅ Done | `spring-boot-starter-data-mongodb` dependency present |
| `application.properties` | ✅ Done | `spring.data.mongodb.uri=${MONGODB_URI}` configured |
| `db/migration/V101__remove_chat.sql` | ✅ Done | Legacy PostgreSQL chat tables already removed |
| `SecurityConfig.java` | ✅ Done | `/api/v1/sessions/**` requires JWT — WebSocket upgrade will be authenticated |

**What is NOT there:** No WebSocket server (`WebSocketConfig`, `WebSocketHandler`, `@EnableWebSocket`), no `chat_messages` collection/document/repository, no message history endpoint, no broadcast logic.

### 1.2 Frontend

| File | Status | What it does |
|---|---|---|
| `data/repository/ChatRepositoryImpl.java` | ✅ Done | OkHttp WebSocket client → `ws://.../api/v1/sessions/{id}/chat`, exponential back-off retry (×5) |
| `domain/chat/ChatRepository.java` | ✅ Done | Port with `connect()`, `sendMessage()`, `disconnect()`, `getMessages()`, `getConnectionState()` |
| `domain/chat/ChatMessage.java` | ✅ Done | Immutable domain model |
| `domain/chat/ChatUiState.java` | ✅ Done | UI state (exists but unused in the current `ChatFragment`) |
| `data/datasource/remote/dto/response/chat/ChatMessageDto.java` | ✅ Done | Gson DTO — maps `message_id`, `session_id`, `sender_id`, `sender_name`, `content`, `timestamp` |
| `ui/chat/ChatFragment.java` | ✅ Done (functional skeleton) | Connects, observes LiveData, sends messages |
| `ui/chat/ChatViewModel.java` + Factory | ✅ Done | Delegates to `ChatRepository`, disconnects `onCleared()` |
| `ui/chat/ChatAdapter.java` | ✅ Done | Two view types (mine/theirs), HH:mm timestamp |
| `res/layout/fragment_chat.xml` | ⚠️ Outdated | Functional but does not match Figma UI (see F-gaps below) |
| `res/layout/item_chat_mine.xml` | ⚠️ Partial | Bubble + timestamp, missing read receipt (✓✓) |
| `res/layout/item_chat_theirs.xml` | ✅ Close | Bubble + optional sender name + timestamp |
| `res/drawable/bg_bubble_mine.xml` | ✅ Done | Orange gradient, `18px 18px 4px 18px` radius |
| `res/drawable/bg_bubble_theirs.xml` | ✅ Done | White + gray stroke, `18px 18px 18px 4px` radius |
| `WalkMateApplication.getChatRepository()` | ✅ Done | Singleton, reuses `OkHttpClient` + derives `baseWsUrl` from `BuildConfig.BASE_URL` |
| `nav_graph.xml` — `chatFragment` | ✅ Done | Navigation entry exists |
| `SessionFragment` (trigger) | ✅ Done | Passes `ARG_SESSION_ID` + `ARG_CURRENT_USER_ID` on Chat click |

---

## 2. Gap Analysis

### 2.1 Backend Gaps (Blockers)

---

#### B-1 — CRITICAL: WebSocket server endpoint does not exist

**Problem:** `ChatRepositoryImpl` connects to `ws://{host}/api/v1/sessions/{sessionId}/chat`. There is no Spring WebSocket handler registered at that path. The server has no `spring-boot-starter-websocket` dependency, no `@EnableWebSocket`, and no `WebSocketConfigurer` bean.

**Effect:** Every connection attempt from the Android client fails immediately. `onFailure()` fires → exponential back-off → after 5 retries, `ConnectionState.ERROR`.

**What needs to be built:**
1. Add `spring-boot-starter-websocket` to `build.gradle`.
2. Create `infrastructure/config/WebSocketConfig.java` implementing `WebSocketConfigurer` — register a handler at `/api/v1/sessions/{sessionId}/chat`.
3. Create `infrastructure/websocket/ChatWebSocketHandler.java` extending `TextWebSocketHandler`:
   - `afterConnectionEstablished` → validate JWT from upgrade header, check user is a participant in the session, register connection in a session → set-of-sockets map.
   - `handleTextMessage` → parse JSON `{"type":"CHAT_MESSAGE","content":"..."}`, persist to `chat_messages`, broadcast to all other sockets in the session.
   - `afterConnectionClosed` / `handleTransportError` → remove from map.
4. Update `SecurityConfig` to allow WebSocket upgrade path through Spring Security's filter chain (see B-4).

> **Note on Architecture:** Because the project is pure Java (no Kotlin coroutines/reactive), use a `ConcurrentHashMap<String, Set<WebSocketSession>>` as the in-memory session registry. Thread-safety is provided by `CopyOnWriteArraySet` per session bucket.

---

#### B-2 — CRITICAL: Chat messages are not persisted

**Problem:** MongoDB currently only stores **chat room metadata** (`chat_rooms` collection: sessionId, status, timestamps). There is no `chat_messages` collection, no `ChatMessageDocument`, and no write/read repository for actual messages.

**Effect:** Every message sent through the WebSocket handler (once built) would have nowhere to be stored. There is also no way to load message history.

**What needs to be built:**
1. Create `infrastructure/repository/chat/document/ChatMessageDocument.java`:
   ```
   @Document(collection = "chat_messages")
   - messageId (UUID, @Id)
   - sessionId (String, indexed)
   - senderId (String)
   - senderName (String)
   - content (String)
   - sentAt (Instant, indexed for sorting)
   ```
2. Create `domain/chat/ChatMessageRepository.java` (domain port):
   - `void save(ChatMessage message)`
   - `List<ChatMessage> findBySessionId(String sessionId, int limit)`
3. Create `infrastructure/repository/chat/MongoChatMessageRepository.java` implementing the port.
4. Declare a compound index `{sessionId: 1, sentAt: -1}` programmatically via `@CompoundIndex` on `ChatMessageDocument`.

---

#### B-3 — CRITICAL: No message history REST endpoint

**Problem:** When a user opens the chat screen, there is no way to load past messages. The current codebase has no `GET /api/v1/sessions/{sessionId}/chat/messages` endpoint.

**Effect:** The chat screen is always blank on first open even if the conversation had 50 prior messages.

**What needs to be built:**
1. Create `application/chat/ChatQueryService.java` with method `findRecentMessages(String sessionId, String callerId, int limit)` — verifies user is a participant, queries MongoDB.
2. Create `presentation/controller/chat/ChatController.java` with:
   - `GET /api/v1/sessions/{sessionId}/chat/messages?limit=50` → returns `ApiResponse<List<ChatMessageResponse>>`
3. Create `presentation/dto/response/chat/ChatMessageResponse.java`.
4. Frontend `ChatRepositoryImpl` needs a new method `loadHistory(sessionId, callback)` that calls this REST endpoint before opening the WebSocket.

---

#### B-4 — HIGH: Spring Security must allow WebSocket upgrades

**Problem:** `SecurityConfig` currently protects `/api/v1/sessions/**` requiring JWT via `oauth2ResourceServer`. Spring's standard JWT filter validates the `Authorization: Bearer` header on the HTTP upgrade request — this works in theory, but `WebSocketConfigurer` routes are registered differently and may conflict with the `HttpSecurity` filter chain.

**Details:**
- OkHttp's `AuthInterceptor` already adds the `Authorization: Bearer {token}` header to WebSocket upgrade requests (interceptors run on WS handshakes).
- Spring Security's `SecurityFilterChain` processes the HTTP 101 upgrade and will validate the JWT before the handler is called — **this part works**.
- The gap is the `WebSocketConfig` must call `setAllowedOrigins("*")` (or restrict appropriately) and must **not** use STOMP (the project uses raw `TextWebSocketHandler`).
- After upgrade, messages on the wire are not re-authenticated — the security context from the upgrade handshake must be captured and stored on the `WebSocketSession` attributes (`session.getAttributes().put("userId", ...)`).

**Action required:** Add WebSocket-specific `SecurityConfig` allowance if needed, and store the authenticated userId in `WebSocketSession` attributes during `afterConnectionEstablished`.

---

#### B-5 — MEDIUM: MongoDB indexes must be declared (no migration needed)

**Question answered:** MongoDB is **schema-less** — no Flyway migration script is needed. Collections (`chat_rooms`, `chat_messages`) auto-create on first document insert. Flyway only manages PostgreSQL.

**However,** without an index, `findBySessionId` on a large `chat_messages` collection will do a full collection scan.

**Action required:**
- Add `@CompoundIndex(def = "{'sessionId': 1, 'sentAt': -1}")` on `ChatMessageDocument`.
- This causes Spring Data MongoDB to create the index automatically on startup via `spring.data.mongodb.auto-index-creation=true` (which is the default).
- No Flyway file needed.

---

### 2.2 Frontend UI Gaps

---

#### F-1 — HIGH: Chat header does not match Figma design

**Current (`fragment_chat.xml`):**
- `ImageButton btnBack` (40×40dp, `bg_white_circle`) + `TextView "Chat"` (static)

**Figma design shows:**
- Back button (40×40dp, `#F5F5F4` fill, 12dp corners — matches `bg_btn_back` drawable)
- Partner avatar: 42×42dp colored circle with initials + online green dot (11dp)
- Partner name: 16sp bold, `#1C1917`, truncated
- Location row: `MapPin` icon (orange) + session location text (11sp, `#A8A29E`)

**Gap:** The layout needs a redesigned header row. `AvatarInitialView` can render the partner avatar. The online dot needs a separate `View` overlay.

**Args gap:** `SessionFragment` currently only passes `ARG_SESSION_ID` and `ARG_CURRENT_USER_ID`. It must also pass:
- `ARG_PARTNER_NAME` (from `session.getPartnerName()`)
- `ARG_MEETING_LOCATION` (from the hotspot/intent location name, or the lat/lng formatted string)

---

#### F-2 — HIGH: Input bar does not match Figma design

**Current layout:**
- `EditText` (plain `bg_input_field`) + `ImageButton btnSend` (`bg_gradient_orange_pill`)
- No attachment button
- Send button is always orange regardless of input state

**Figma design:**
- Attachment button (42×42dp, `#F5F5F4`, 13dp corners, `ImagePlus` icon, `#A8A29E`)
- Input pill: `#F5F5F4` background, `22dp` radius, border `1.5dp transparent` → `1.5dp #F97316` when text is typed (animated transition)
- Send button: gray (`#F3F2F0`) with gray icon when empty, orange gradient with shadow when text typed

**Action required:**
- Add attachment `ImageButton` to `inputRow` in `fragment_chat.xml` (can be no-op for this phase — image sending is out of scope).
- Update `EditText` drawable to pill shape with dynamic border via `TextWatcher`.
- Control send button background via `TextWatcher` in `ChatFragment`.

---

#### F-3 — MEDIUM: Date chip "Today · Walk Session" is missing

**Current:** No date chip in the layout.

**Figma:** A centered `#F5F5F4` pill chip with text `"Today · Walk Session"` (11sp bold, `#A8A29E`) appears between the header and the first message.

**Action required:** Add a centered date chip `TextView` between the header and `RecyclerView` in `fragment_chat.xml`. Text should be static ("Today · Walk Session") for this phase.

---

#### F-4 — MEDIUM: Read receipt (✓✓) missing from sent messages

**Current (`item_chat_mine.xml`):** Shows timestamp (`txtTime`) below the bubble — no read receipt indicator.

**Figma:** Shows timestamp + `✓✓` in orange (`#F97316`) after the timestamp on the same line, for "me" messages.

**Action required:**
- Add a `TextView txtReadReceipt` next to `txtTime` in `item_chat_mine.xml`.
- Initially show `✓` (sent) — switch to `✓✓` (read) when acknowledgement comes from backend.
- For V1 (this phase), always show `✓✓` as a static indicator that the message was delivered (no real read-receipt protocol yet).

---

#### F-5 — LOW: Timestamp layout position differs from Figma

**Current:** Timestamp is **below** the bubble (separate `TextView`).

**Figma:** Timestamp is **outside** the bubble to the left/right on the **same horizontal row as the bubble** (inline with bubble's bottom edge).

**Action required:** Change both `item_chat_mine.xml` and `item_chat_theirs.xml` to use a horizontal `LinearLayout` or `ConstraintLayout` so the timestamp sits inline with the bubble bottom, not below it.

---

#### F-6 — LOW: `ChatUiState` exists but `ChatFragment` bypasses it

**Observation:** `ChatUiState.java` exists (wraps `isLoading`, `messages`, `connectionState`, `error`) but `ChatFragment` directly observes `chatViewModel.getMessages()` and `chatViewModel.getConnectionState()` as separate `LiveData`. This is a minor architecture inconsistency.

**Decision:** Keep the current direct-LiveData approach in `ChatFragment` — it's simpler and within the MVVM-lite spec. `ChatUiState` can be removed or kept as dead code. **Not a blocker.**

---

## 3. Summary Table

| ID | Layer | Severity | What's Missing | Blocker? |
|---|---|---|---|---|
| B-1 | Backend | 🔴 CRITICAL | WebSocket server endpoint (`TextWebSocketHandler` + config + dependency) | Yes |
| B-2 | Backend | 🔴 CRITICAL | `chat_messages` MongoDB collection, document, port, adapter | Yes |
| B-3 | Backend | 🔴 CRITICAL | `GET /sessions/{id}/chat/messages` history endpoint | Yes |
| B-4 | Backend | 🟠 HIGH | Spring Security configuration for WebSocket upgrade + userId in session attributes | Yes |
| B-5 | Backend | 🟡 MEDIUM | MongoDB compound index on `chat_messages` | No (perf) |
| F-1 | Frontend | 🟠 HIGH | Header redesign (partner avatar, name, location) + new Bundle args | No (UI only) |
| F-2 | Frontend | 🟠 HIGH | Input bar redesign (attachment button, dynamic border, conditional send button state) | No (UI only) |
| F-3 | Frontend | 🟡 MEDIUM | Date chip "Today · Walk Session" | No (UI only) |
| F-4 | Frontend | 🟡 MEDIUM | Read receipt ✓✓ on sent messages | No (UI only) |
| F-5 | Frontend | 🟢 LOW | Timestamp inline with bubble (not below) | No (UI only) |
| F-6 | Frontend | 🟢 LOW | `ChatUiState` unused in `ChatFragment` | No |

---

## 4. Implementation Order

```
Phase 1 — Backend Core (unblock everything)
  1. Add spring-boot-starter-websocket to build.gradle
  2. Create ChatMessageDocument + ChatMessageRepository port + MongoChatMessageRepository
  3. Create ChatWebSocketHandler (connect/broadcast/persist/disconnect)
  4. Create WebSocketConfig registering handler at /api/v1/sessions/{sessionId}/chat
  5. Handle JWT auth in afterConnectionEstablished, store userId in session attributes
  6. Create ChatController + ChatQueryService + GET history endpoint

Phase 2 — Frontend wiring
  7. Add loadHistory() call in ChatViewModel.startChat() before connect()
  8. Merge history messages into the LiveData list before WebSocket messages arrive

Phase 3 — UI polish (can be done in parallel with Phase 2)
  9. Redesign fragment_chat.xml header (AvatarInitialView + partner name + location)
  10. Add ARG_PARTNER_NAME + ARG_MEETING_LOCATION to SessionFragment navigation call
  11. Redesign input bar (attachment button, dynamic pill border, conditional send state)
  12. Add date chip to layout
  13. Update item_chat_mine.xml for inline timestamp + read receipt
  14. Update item_chat_theirs.xml for inline timestamp
```

---

## 5. Architecture Decisions

**Why `TextWebSocketHandler` over STOMP/SockJS?**
The project bans RxJava and external messaging libs. OkHttp's raw WebSocket is already wired on the client. `TextWebSocketHandler` is the matching raw-WebSocket counterpart on the server — no STOMP broker, no SockJS fallback, no extra dependency surface.

**Why in-memory session registry?**
Single-server deployment. `ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>>` is sufficient. If horizontal scaling is needed in the future, replace with Redis Pub/Sub — but that's out of scope.

**Why MongoDB for messages instead of PostgreSQL?**
This decision is already made (V101 migration removed legacy chat tables). MongoDB's document model fits variable-length chat payloads and high-frequency inserts better than a normalized relational table.

**Message flow once built:**
```
Android sends JSON frame {"type":"CHAT_MESSAGE","content":"..."}
→ ChatWebSocketHandler.handleTextMessage()
  → validate room is OPEN (check WalkSession status in PostgreSQL via SessionQueryService)
  → persist ChatMessageDocument to MongoDB chat_messages
  → broadcast ChatMessageDocument as JSON to all WebSocketSessions in the session bucket
→ Partner's Android receives frame
  → ChatRepositoryImpl.onMessage() → parseMessage() → appendMessage() → LiveData update → UI
```
