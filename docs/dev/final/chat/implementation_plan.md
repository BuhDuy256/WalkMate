# Chat System — Implementation Plan (Realtime)

**Date:** 2026-04-30  
**Stack:** Spring Boot (Java) + OkHttp WebSocket + MongoDB Atlas  
**Precondition:** Read `gap_analysis.md` first. This plan resolves every gap identified there.

---

## Pre-flight Checks (Before Writing Any Code)

| Check | Finding |
|---|---|
| Frontend `WalkSession.getHotspotName()` | ✅ Exists — use directly for chat header |
| Frontend `WalkSession.getPartnerName()` | ✅ Exists — use directly for chat header |
| Frontend `WalkSession.getPartnerAvatar()` | ✅ Exists — use for `AvatarInitialView` |
| Backend `UserQueryService.getDisplayName(UUID userId)` | ✅ Exists — use for sender name on WS connect |
| Backend `DomainException(ErrorCode errorCode)` | ✅ Standard constructor |
| Backend `ErrorCode` interface methods | `getCode()` + `getMessage()` |
| Backend `WalkSession.getUserIdA()` / `getUserIdB()` | ✅ Exists — for participant check |
| `/api/v1/sessions/**` already authenticated in SecurityConfig | ✅ Covers the WS upgrade path automatically |
| No MongoDB migration needed | ✅ Schema-less — collections auto-create |

---

## Phase 1 — Backend: Foundation (Resolve B-1, B-2, B-4, B-5)

---

### Step 1.1 — Add WebSocket Dependency

**File:** `backend/build.gradle`

```
Add inside dependencies { }:
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

---

### Step 1.2 — Create ChatErrorCode

**New file:** `backend/src/main/java/com/walkmate/domain/chat/ChatErrorCode.java`

Implement `ErrorCode` interface with two constants:

| Constant | `getCode()` | `getMessage()` |
|---|---|---|
| `CHAT_UNAUTHORIZED` | `"CHAT_UNAUTHORIZED"` | `"User is not a participant in this session"` |
| `CHAT_ROOM_CLOSED` | `"CHAT_ROOM_CLOSED"` | `"This chat room is no longer accepting messages"` |

---

### Step 1.3 — Create ChatMessage Domain Entity (backend)

**New file:** `backend/src/main/java/com/walkmate/domain/chat/ChatMessage.java`

Plain Java class (no MongoDB annotations — those belong in infrastructure):

```
Fields (all final):
  - String messageId
  - String sessionId
  - String senderId
  - String senderName   (denormalized snapshot — avoids lookup per message)
  - String content
  - Instant sentAt

Static factory: ChatMessage.create(sessionId, senderId, senderName, content)
  → generates messageId = UUID.randomUUID().toString()
  → sets sentAt = Instant.now()

All-args constructor + Lombok @Getter (or manual getters)
```

---

### Step 1.4 — Create ChatMessageRepository Domain Port

**New file:** `backend/src/main/java/com/walkmate/domain/chat/ChatMessageRepository.java`

```java
public interface ChatMessageRepository {
    ChatMessage save(ChatMessage message);
    List<ChatMessage> findLatestBySessionId(String sessionId, int limit);
}
```

---

### Step 1.5 — Create ChatMessageDocument

**New file:** `backend/src/main/java/com/walkmate/infrastructure/repository/chat/document/ChatMessageDocument.java`

```
@Document(collection = "chat_messages")
@CompoundIndex(def = "{'sessionId': 1, 'sentAt': -1}")
@Getter
class ChatMessageDocument {
    @Id  String messageId
    String sessionId
    String senderId
    String senderName
    String content
    Instant sentAt
}

Static factory: ChatMessageDocument.from(ChatMessage domain)
  → maps all fields directly (both use same names)
```

---

### Step 1.6 — Create MongoChatMessageRepository

**New file:** `backend/src/main/java/com/walkmate/infrastructure/repository/chat/MongoChatMessageRepository.java`

```
@Slf4j @Repository @RequiredArgsConstructor
Implements ChatMessageRepository

Dependency: MongoTemplate mongoTemplate

save(ChatMessage message):
  - Convert to ChatMessageDocument.from(message)
  - mongoTemplate.insert(doc, "chat_messages")
  - Return back the domain message (already has messageId)

findLatestBySessionId(String sessionId, int limit):
  - Query: Criteria.where("sessionId").is(sessionId)
  - Sort: Sort.by(DESC, "sentAt")
  - limit: limit
  - Map results: ChatMessageDocument → ChatMessage (new ChatMessage(doc fields))
  - Reverse the list (so oldest-first for UI display)
  - Return List<ChatMessage>
```

---

### Step 1.7 — Create ChatCommandService

**New file:** `backend/src/main/java/com/walkmate/application/chat/ChatCommandService.java`

```
@Service @RequiredArgsConstructor
Dependencies:
  - WalkSessionRepository sessionRepository
  - ChatMessageRepository messageRepository
  - UserQueryService userQueryService

Method: validateParticipant(String sessionId, String userId)
  - Load session: sessionRepository.findById(UUID.fromString(sessionId))
    → if empty: throw DomainException(SessionErrorCode.SESSION_NOT_FOUND)
  - Check participant: getUserIdA() or getUserIdB() equals userId
    → if not: throw DomainException(ChatErrorCode.CHAT_UNAUTHORIZED)
  - Check not closed: status != CANCELLED and status != COMPLETED
    → if closed: throw DomainException(ChatErrorCode.CHAT_ROOM_CLOSED)

Method: String getSenderName(String userId)
  - userQueryService.getDisplayName(UUID.fromString(userId))
  - Return the name (or "User" as fallback if null)

Method: ChatMessage processMessage(String sessionId, String senderId, String senderName, String content)
  - Re-check session is not closed (same status check as above — defend against race)
    → throw DomainException(ChatErrorCode.CHAT_ROOM_CLOSED) if closed
  - Create: ChatMessage.create(sessionId, senderId, senderName, content)
  - Persist: messageRepository.save(message)
  - Return saved message
```

---

### Step 1.8 — Create ChatWebSocketHandler

**New file:** `backend/src/main/java/com/walkmate/infrastructure/websocket/ChatWebSocketHandler.java`

```
@Slf4j @Component @RequiredArgsConstructor
Extends TextWebSocketHandler

Dependency: ChatCommandService chatCommandService
            Gson gson = new Gson() (or inject a shared bean)

Session registry:
  ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> registry

─── afterConnectionEstablished(WebSocketSession session) ───
1. Extract sessionId from URI path:
     path = session.getUri().getPath()
     // "/api/v1/sessions/{sessionId}/chat"
     String[] parts = path.split("/");
     String sessionId = parts[parts.length - 2];

2. Get authenticated userId (set by Spring Security during HTTP upgrade):
     String userId = session.getPrincipal().getName();
     // Principal is JwtAuthenticationToken; getName() returns the 'sub' claim (user UUID string)

3. Validate participant — if rejected, close and return:
     try {
         chatCommandService.validateParticipant(sessionId, userId);
     } catch (DomainException e) {
         session.close(CloseStatus.NOT_ACCEPTABLE);
         return;
     }

4. Resolve and cache sender name (avoids per-message DB lookup):
     String senderName = chatCommandService.getSenderName(userId);
     session.getAttributes().put("sessionId", sessionId);
     session.getAttributes().put("userId", userId);
     session.getAttributes().put("senderName", senderName);

5. Register in session bucket:
     registry.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
     log.debug("WS connected: session={} user={}", sessionId, userId);

─── handleTextMessage(WebSocketSession session, TextMessage textMessage) ───
1. Read attributes:
     String sessionId  = (String) session.getAttributes().get("sessionId");
     String senderId   = (String) session.getAttributes().get("userId");
     String senderName = (String) session.getAttributes().get("senderName");

2. Parse inbound JSON (inner static class InboundFrame { String type; String content; }):
     InboundFrame frame = gson.fromJson(textMessage.getPayload(), InboundFrame.class);
     Guard: if frame is null, type != "CHAT_MESSAGE", or content is blank → return silently

3. Persist and get saved message:
     ChatMessage saved;
     try {
         saved = chatCommandService.processMessage(sessionId, senderId, senderName, frame.content);
     } catch (DomainException e) {
         // Chat room closed — notify sender only
         session.sendMessage(new TextMessage("{\"error\":\"" + e.getErrorCode().getCode() + "\"}"));
         return;
     }

4. Build outbound JSON (inner static class OutboundFrame mirrors ChatMessageDto fields):
     OutboundFrame out = new OutboundFrame();
     out.message_id  = saved.getMessageId();
     out.session_id  = saved.getSessionId();
     out.sender_id   = saved.getSenderId();
     out.sender_name = saved.getSenderName();
     out.content     = saved.getContent();
     out.timestamp   = saved.getSentAt().toEpochMilli();   // matches frontend ChatMessageDto
     TextMessage outbound = new TextMessage(gson.toJson(out));

5. Broadcast to ALL participants in session (including sender — client uses sender_id to distinguish):
     Set<WebSocketSession> bucket = registry.getOrDefault(sessionId, Collections.emptySet());
     for (WebSocketSession peer : bucket) {
         try {
             if (peer.isOpen()) peer.sendMessage(outbound);
         } catch (IOException e) {
             log.warn("Broadcast failed to peer {}: {}", peer.getId(), e.getMessage());
         }
     }

─── afterConnectionClosed(WebSocketSession session, CloseStatus status) ───
     String sessionId = (String) session.getAttributes().get("sessionId");
     if (sessionId != null) {
         Set<WebSocketSession> bucket = registry.get(sessionId);
         if (bucket != null) {
             bucket.remove(session);
             if (bucket.isEmpty()) registry.remove(sessionId);
         }
     }
     log.debug("WS closed: session={}", sessionId);

─── handleTransportError(WebSocketSession session, Throwable ex) ───
     log.warn("WS transport error: {}", ex.getMessage());
     afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
```

**Why broadcast sender's own message back to them:**  
The client sends the message and immediately shows it. BUT, by echoing the persisted version back to the sender (with the server-assigned `messageId` and server `timestamp`), the client can update the local message with canonical server data and deduplicate on reconnect via `messageId`.

---

### Step 1.9 — Create WebSocketConfig

**New file:** `backend/src/main/java/com/walkmate/infrastructure/config/WebSocketConfig.java`

```
@Configuration
@EnableWebSocket
Implements WebSocketConfigurer

Dependency: @Autowired ChatWebSocketHandler chatWebSocketHandler

registerWebSocketHandlers(WebSocketHandlerRegistry registry):
    registry
        .addHandler(chatWebSocketHandler, "/api/v1/sessions/*/chat")
        .setAllowedOriginPatterns("*");
```

**Why `*` path wildcard:** Spring's `WebSocketHandlerRegistry` supports Ant-style patterns. The `*` matches any single segment — the sessionId UUID. The handler extracts it from the URI.

**Why SecurityConfig needs no changes:**  
`/api/v1/sessions/**` is already marked `.authenticated()` in `SecurityConfig`. Spring's `BearerTokenAuthenticationFilter` runs on the HTTP upgrade request and validates the JWT before the WebSocket handshake completes. The resulting `Principal` is available in `session.getPrincipal()` inside the handler.

---

## Phase 2 — Backend: REST History Endpoint (Resolve B-3)

---

### Step 2.1 — Create ChatQueryService

**New file:** `backend/src/main/java/com/walkmate/application/chat/ChatQueryService.java`

```
@Service @RequiredArgsConstructor

Dependencies:
  - ChatMessageRepository messageRepository
  - WalkSessionRepository sessionRepository

Method: List<ChatMessage> findRecentMessages(String sessionId, String requesterId, int limit)
  @Transactional(readOnly = true)

  1. Load session:
       WalkSession session = sessionRepository.findById(UUID.fromString(sessionId))
           .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

  2. Verify requester is a participant:
       if (!session.getUserIdA().equals(requesterId) && !session.getUserIdB().equals(requesterId))
           throw new DomainException(ChatErrorCode.CHAT_UNAUTHORIZED);

  3. Fetch and return:
       return messageRepository.findLatestBySessionId(sessionId, limit);
       // already sorted oldest-first by MongoChatMessageRepository
```

---

### Step 2.2 — Create ChatMessageResponse DTO

**New file:** `backend/src/main/java/com/walkmate/presentation/dto/response/chat/ChatMessageResponse.java`

```
@Getter @Builder (or public fields with Lombok)

Fields:
  @JsonProperty("message_id")   String messageId
  @JsonProperty("session_id")   String sessionId
  @JsonProperty("sender_id")    String senderId
  @JsonProperty("sender_name")  String senderName
  @JsonProperty("content")      String content
  @JsonProperty("timestamp")    long timestamp   // epoch milliseconds

Static factory: from(ChatMessage domain)
  → timestamp = domain.getSentAt().toEpochMilli()
  → all other fields direct mapping
```

**Field naming must match `ChatMessageDto` on the Android client (`@SerializedName` annotations).**

---

### Step 2.3 — Create ChatController

**New file:** `backend/src/main/java/com/walkmate/presentation/controller/chat/ChatController.java`

```
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor

Dependency: ChatQueryService chatQueryService

@GetMapping("/{sessionId}/chat/messages")
ApiResponse<List<ChatMessageResponse>> getChatHistory(
    @PathVariable String sessionId,
    @RequestParam(defaultValue = "50") int limit,
    @AuthenticationPrincipal Jwt jwt
)
  1. String requesterId = jwt.getSubject();
  2. List<ChatMessage> messages = chatQueryService.findRecentMessages(sessionId, requesterId, limit);
  3. List<ChatMessageResponse> dtos = messages.stream()
         .map(ChatMessageResponse::from)
         .toList();
  4. return ApiResponse.success(dtos);
```

---

## Phase 3 — Frontend: Wire History Load (Resolve gap F-0)

---

### Step 3.1 — Add History API Call to ChatRepositoryImpl

**Modified file:** `frontend/src/main/java/com/walkmate/data/repository/ChatRepositoryImpl.java`

Add a dependency: `Retrofit`/`ApiService` for HTTP, OR reuse `OkHttpClient` for a plain HTTP call.

Since other repositories in the project use Retrofit (via `WalkMateApplication`), add a reference to the existing `ApiService` or create a minimal interface:

```java
// In ChatRepositoryImpl constructor, add: ApiService apiService
// (passed from WalkMateApplication.getChatRepository())

public void loadHistory(String sessionId, DomainCallback<List<ChatMessage>> callback) {
    // Run on background thread (consistent with other repositories)
    executor.execute(() -> {
        try {
            Response<ApiResponse<List<ChatMessageDto>>> response =
                apiService.getChatHistory(sessionId, 50).execute();

            if (response.isSuccessful() && response.body() != null
                    && response.body().getData() != null) {

                List<ChatMessage> messages = response.body().getData().stream()
                    .map(dto -> new ChatMessage(
                        dto.messageId != null ? dto.messageId : "",
                        dto.sessionId != null ? dto.sessionId : sessionId,
                        dto.senderId  != null ? dto.senderId  : "",
                        dto.senderName,
                        dto.content   != null ? dto.content   : "",
                        dto.timestamp != 0    ? dto.timestamp : System.currentTimeMillis(),
                        dto.senderId != null && dto.senderId.equals(currentUserId)
                    ))
                    .collect(Collectors.toList());

                callback.onSuccess(messages);
            } else {
                callback.onSuccess(Collections.emptyList()); // history unavailable — not fatal
            }
        } catch (Exception e) {
            callback.onSuccess(Collections.emptyList()); // fail silently; live chat still works
        }
    });
}
```

Add the endpoint to the existing `ApiService` interface:
```java
@GET("api/v1/sessions/{sessionId}/chat/messages")
Call<ApiResponse<List<ChatMessageDto>>> getChatHistory(
    @Path("sessionId") String sessionId,
    @Query("limit") int limit
);
```

Add an `ExecutorService` to `ChatRepositoryImpl` (for the history HTTP call):
```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();
// Shut down in disconnect() via executor.shutdown()
```

---

### Step 3.2 — Update ChatViewModel.startChat()

**Modified file:** `frontend/src/main/java/com/walkmate/ui/chat/ChatViewModel.java`

Change `startChat(sessionId, userId)` to:
```
1. Call chatRepository.loadHistory(sessionId, new DomainCallback<List<ChatMessage>>() {
       onSuccess(messages):
           // Seed the LiveData with history before connecting WebSocket
           // Use postValue (called from executor thread in loadHistory)
           // Store history in a field so appendMessage() can deduplicate
           historyMessageIds = messages.stream()
               .map(ChatMessage::getMessageId)
               .collect(Collectors.toSet());
           messagesLiveData.postValue(messages);

           // Then open WebSocket — new messages append to the history list
           chatRepository.connect(sessionId, userId);

       onError(e):
           // Connect anyway — history is non-blocking
           chatRepository.connect(sessionId, userId);
   });
```

**Deduplication in ChatRepositoryImpl.appendMessage():**  
Update `appendMessage()` to skip messages whose `messageId` is already in the list:
```java
private void appendMessage(ChatMessage msg) {
    List<ChatMessage> current = messagesLiveData.getValue();
    List<ChatMessage> updated = new ArrayList<>(current != null ? current : new ArrayList<>());
    // Deduplicate: server echoes sender's own message back with canonical data
    boolean alreadyPresent = updated.stream()
        .anyMatch(m -> m.getMessageId().equals(msg.getMessageId()));
    if (!alreadyPresent) updated.add(msg);
    messagesLiveData.postValue(updated);
}
```

---

### Step 3.3 — Update WalkMateApplication.getChatRepository()

**Modified file:** `frontend/src/main/java/com/walkmate/WalkMateApplication.java`

Pass the `ApiService` instance to `ChatRepositoryImpl`:
```java
public ChatRepository getChatRepository() {
    if (chatRepository == null) {
        chatRepository = new ChatRepositoryImpl(getOkHttpClient(), getBaseWsUrl(), getApiService());
    }
    return chatRepository;
}
```

---

## Phase 4 — Frontend: UI Redesign (Resolve F-1 through F-5)

---

### Step 4.1 — Redesign fragment_chat.xml Header

**Modified file:** `frontend/src/main/res/layout/fragment_chat.xml`

Replace the current header (plain back button + "Chat" title) with:

```xml
<!-- Header bar — white, 4dp bottom shadow -->
<LinearLayout
    android:id="@+id/headerBar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="12dp"
    android:paddingEnd="16dp"
    android:paddingTop="12dp"
    android:paddingBottom="12dp"
    android:background="@color/bg_white"
    android:elevation="4dp"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent">

    <!-- Back button: 40×40dp, bg_btn_back (already exists), ic_back -->
    <ImageButton
        android:id="@+id/btnBack"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="@drawable/bg_btn_back"
        android:src="@drawable/ic_back"
        android:padding="8dp" />

    <!-- 12dp gap -->
    <Space android:layout_width="12dp" android:layout_height="0dp" />

    <!-- Partner avatar: 42×42dp, AvatarInitialView, 14dp radius via XML attr or code -->
    <com.walkmate.core.designsystem.view.AvatarInitialView
        android:id="@+id/avatarPartner"
        android:layout_width="42dp"
        android:layout_height="42dp"
        app:wm_avatarName=""
        app:wm_showOnlineStatus="true" />

    <!-- 10dp gap -->
    <Space android:layout_width="10dp" android:layout_height="0dp" />

    <!-- Name + location column -->
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/txtPartnerName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="@color/text_dark"
            android:maxLines="1"
            android:ellipsize="end"
            android:letterSpacing="-0.012" />

        <!-- Location row: MapPin icon + hotspot name -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginTop="2dp">

            <ImageView
                android:layout_width="10dp"
                android:layout_height="10dp"
                android:src="@drawable/ic_hotspot_pin"
                android:tint="@color/orange_primary" />

            <TextView
                android:id="@+id/txtSessionLocation"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="3dp"
                android:textSize="11sp"
                android:textColor="@color/text_muted" />
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
```

Anchor `txtConnecting` / `txtError` to `headerBar` bottom (not `btnBack` bottom — the header is now taller).  
Anchor `recyclerChat` top to `txtConnecting` bottom (same as before — the chain still works).

---

### Step 4.2 — Update SessionFragment to Pass New Args

**Modified file:** `frontend/src/main/java/com/walkmate/ui/matches/session/SessionFragment.java`

In the `onChatClickListener` block (around line 83-95):
```java
adapter.setOnChatClickListener(session -> {
    if (session.getStatus() == WalkSession.Status.ACTIVE ||
            session.getStatus() == WalkSession.Status.PENDING) {
        ...
        args.putString(ChatFragment.ARG_SESSION_ID,      session.getSessionId());
        args.putString(ChatFragment.ARG_CURRENT_USER_ID, currentUserId);
        args.putString(ChatFragment.ARG_PARTNER_NAME,    session.getPartnerName());   // ADD
        args.putString(ChatFragment.ARG_PARTNER_AVATAR,  session.getPartnerAvatar()); // ADD
        args.putString(ChatFragment.ARG_HOTSPOT_NAME,    session.getHotspotName());   // ADD
        Navigation.findNavController(...).navigate(R.id.chatFragment, args);
    }
});
```

---

### Step 4.3 — Update ChatFragment to Use New Header Args

**Modified file:** `frontend/src/main/java/com/walkmate/ui/chat/ChatFragment.java`

Add new ARG constants:
```java
public static final String ARG_PARTNER_NAME   = "PARTNER_NAME";
public static final String ARG_PARTNER_AVATAR = "PARTNER_AVATAR";
public static final String ARG_HOTSPOT_NAME   = "HOTSPOT_NAME";
```

In `onViewCreated()`, after reading args:
```java
String partnerName   = args.getString(ARG_PARTNER_NAME,   "");
String partnerAvatar = args.getString(ARG_PARTNER_AVATAR, "");
String hotspotName   = args.getString(ARG_HOTSPOT_NAME,   "Walk Session");

// Bind header
AvatarInitialView avatarPartner = view.findViewById(R.id.avatarPartner);
avatarPartner.bind(partnerName, partnerAvatar);

((TextView) view.findViewById(R.id.txtPartnerName)).setText(partnerName);
((TextView) view.findViewById(R.id.txtSessionLocation)).setText(hotspotName);
```

The `AvatarInitialView.bind(name, avatarUrl)` already handles:
- Loading photo via Glide if URL is non-null
- Falling back to initials with a deterministic color derived from the name

---

### Step 4.4 — Redesign Input Bar in fragment_chat.xml

**Modified file:** `frontend/src/main/res/layout/fragment_chat.xml`

Replace `<LinearLayout android:id="@+id/inputRow">` with:

```xml
<LinearLayout
    android:id="@+id/inputRow"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="12dp"
    android:paddingEnd="12dp"
    android:paddingTop="10dp"
    android:paddingBottom="20dp"
    android:background="@color/bg_white"
    android:elevation="4dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent">

    <!-- Attachment (no-op for this phase) -->
    <ImageButton
        android:id="@+id/btnAttach"
        android:layout_width="42dp"
        android:layout_height="42dp"
        android:background="@drawable/bg_icon_container_orange"
        android:src="@drawable/ic_attachment"
        android:padding="10dp"
        android:contentDescription="Attach" />

    <!-- 10dp gap -->
    <Space android:layout_width="10dp" android:layout_height="0dp" />

    <!-- Input pill — dynamic orange border via TextWatcher in code -->
    <EditText
        android:id="@+id/inputMessage"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:hint="Type a message…"
        android:textSize="14sp"
        android:maxLines="4"
        android:inputType="textMultiLine|textCapSentences"
        android:background="@drawable/bg_chat_input_idle"
        android:paddingStart="14dp"
        android:paddingEnd="14dp"
        android:paddingTop="11dp"
        android:paddingBottom="11dp" />

    <!-- 10dp gap -->
    <Space android:layout_width="10dp" android:layout_height="0dp" />

    <!-- Send button — state controlled by TextWatcher -->
    <ImageButton
        android:id="@+id/btnSend"
        android:layout_width="42dp"
        android:layout_height="42dp"
        android:background="@drawable/bg_send_idle"
        android:src="@drawable/ic_send"
        android:padding="10dp"
        android:contentDescription="Send" />
</LinearLayout>
```

**New drawables required:**

| Drawable | Spec |
|---|---|
| `bg_chat_input_idle` | `#F5F5F4` fill, `22dp` radius, `1.5dp transparent` stroke |
| `bg_chat_input_active` | `#F5F5F4` fill, `22dp` radius, `1.5dp #F97316` stroke |
| `bg_send_idle` | `#F3F2F0` fill, `13dp` radius |
| `bg_send_active` | Orange gradient `135°` (`#F97316` → `#FB923C`), `13dp` radius |
| `ic_attachment` | `ImagePlus` icon equivalent, 18dp, `#A8A29E` (vector drawable) |
| `ic_send` | Paper-plane / send icon, 17dp (may already exist as `ic_send` or similar) |

**TextWatcher in ChatFragment.onViewCreated():**
```java
messageInput.addTextChangedListener(new TextWatcher() {
    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        boolean hasText = s.length() > 0;
        messageInput.setBackgroundResource(
            hasText ? R.drawable.bg_chat_input_active : R.drawable.bg_chat_input_idle);
        btnSend.setBackgroundResource(
            hasText ? R.drawable.bg_send_active : R.drawable.bg_send_idle);
        btnSend.setImageTintList(ContextCompat.getColorStateList(requireContext(),
            hasText ? android.R.color.white : R.color.text_muted));
        btnSend.setEnabled(hasText);
    }
});
```

---

### Step 4.5 — Add Date Chip to fragment_chat.xml

**Modified file:** `frontend/src/main/res/layout/fragment_chat.xml`

Add between the header block and `recyclerChat`:

```xml
<TextView
    android:id="@+id/txtDateChip"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center_horizontal"
    android:background="@drawable/bg_chip_inactive"
    android:paddingStart="14dp"
    android:paddingEnd="14dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:text="Today · Walk Session"
    android:textSize="11sp"
    android:textStyle="bold"
    android:textColor="@color/text_muted"
    app:layout_constraintTop_toBottomOf="@id/txtConnecting"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    android:layout_marginTop="12dp"
    android:layout_marginBottom="6dp" />
```

`bg_chip_inactive` already exists (`#F5EDE4` fill, `999dp` radius). Use it directly.  
Anchor `recyclerChat` top to `txtDateChip` bottom.

---

### Step 4.6 — Update item_chat_mine.xml (Inline Timestamp + Read Receipt)

**Modified file:** `frontend/src/main/res/layout/item_chat_mine.xml`

Change layout to `ConstraintLayout` so timestamp sits inline with bubble bottom-right:

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:paddingStart="64dp"
    android:paddingEnd="12dp">

    <!-- Message bubble -->
    <TextView
        android:id="@+id/txtContent"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_bubble_mine"
        android:paddingStart="15dp"
        android:paddingEnd="15dp"
        android:paddingTop="11dp"
        android:paddingBottom="11dp"
        android:textColor="@android:color/white"
        android:textSize="14sp"
        android:fontFamily="sans-serif-medium"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- Timestamp row (time + ✓✓) — right-aligned, below bubble -->
    <LinearLayout
        android:id="@+id/timeRow"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginTop="3dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toBottomOf="@id/txtContent">

        <TextView
            android:id="@+id/txtTime"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="10sp"
            android:textColor="@color/text_muted" />

        <!-- Read receipt: ✓✓ in orange, always visible (V1 = always delivered) -->
        <TextView
            android:id="@+id/txtReadReceipt"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="3dp"
            android:text="✓✓"
            android:textSize="10sp"
            android:textColor="@color/orange_primary" />
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

### Step 4.7 — Update item_chat_theirs.xml (Inline Timestamp)

**Modified file:** `frontend/src/main/res/layout/item_chat_theirs.xml`

Same structure as mine but left-aligned, no read receipt:

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:paddingStart="12dp"
    android:paddingEnd="64dp">

    <!-- Optional sender name (hidden in 1:1 chat, visible in future group chat) -->
    <TextView
        android:id="@+id/txtSenderName"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/text_muted"
        android:textSize="11sp"
        android:textStyle="bold"
        android:layout_marginBottom="2dp"
        android:visibility="gone"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- Message bubble -->
    <TextView
        android:id="@+id/txtContent"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_bubble_theirs"
        android:paddingStart="15dp"
        android:paddingEnd="15dp"
        android:paddingTop="11dp"
        android:paddingBottom="11dp"
        android:textColor="@color/text_dark"
        android:textSize="14sp"
        android:fontFamily="sans-serif-medium"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/txtSenderName" />

    <!-- Timestamp — left-aligned, below bubble -->
    <TextView
        android:id="@+id/txtTime"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:layout_marginStart="4dp"
        android:textSize="10sp"
        android:textColor="@color/text_muted"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/txtContent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

### Step 4.8 — Update ChatAdapter

**Modified file:** `frontend/src/main/java/com/walkmate/ui/chat/ChatAdapter.java`

No structural changes needed (already has two ViewHolder types for mine/theirs).  
The new XML IDs (`txtReadReceipt`) are the only addition — bind it in `MineViewHolder.bind()`:
```java
// txtReadReceipt is always visible (V1: always ✓✓)
// Future: holder.txtReadReceipt.setVisibility(message.isRead() ? View.VISIBLE : View.GONE)
```

---

## Summary of All Files to Create / Modify

### Backend — New Files

| File | Phase |
|---|---|
| `domain/chat/ChatErrorCode.java` | 1.2 |
| `domain/chat/ChatMessage.java` (backend domain entity) | 1.3 |
| `domain/chat/ChatMessageRepository.java` | 1.4 |
| `infrastructure/repository/chat/document/ChatMessageDocument.java` | 1.5 |
| `infrastructure/repository/chat/MongoChatMessageRepository.java` | 1.6 |
| `application/chat/ChatCommandService.java` | 1.7 |
| `infrastructure/websocket/ChatWebSocketHandler.java` | 1.8 |
| `infrastructure/config/WebSocketConfig.java` | 1.9 |
| `application/chat/ChatQueryService.java` | 2.1 |
| `presentation/dto/response/chat/ChatMessageResponse.java` | 2.2 |
| `presentation/controller/chat/ChatController.java` | 2.3 |

### Backend — Modified Files

| File | Change |
|---|---|
| `build.gradle` | Add `spring-boot-starter-websocket` |

### Frontend — Modified Files

| File | Change |
|---|---|
| `data/datasource/remote/api/ApiService.java` | Add `getChatHistory()` endpoint |
| `data/repository/ChatRepositoryImpl.java` | Add `loadHistory()`, deduplication in `appendMessage()`, `ExecutorService` |
| `ui/chat/ChatViewModel.java` | Chain history load → connect |
| `ui/chat/ChatFragment.java` | New ARG constants, bind header views, TextWatcher for input |
| `ui/matches/session/SessionFragment.java` | Pass 3 new Bundle args on chat click |
| `WalkMateApplication.java` | Pass `ApiService` to `ChatRepositoryImpl` constructor |
| `res/layout/fragment_chat.xml` | Full redesign: new header, date chip, input bar |
| `res/layout/item_chat_mine.xml` | ConstraintLayout, inline timestamp + read receipt |
| `res/layout/item_chat_theirs.xml` | ConstraintLayout, inline timestamp |
| `ui/chat/ChatAdapter.java` | Bind `txtReadReceipt` |

### Frontend — New Drawables

| Drawable | Spec |
|---|---|
| `bg_chat_input_idle` | `#F5F5F4` fill, `22dp` radius, transparent border |
| `bg_chat_input_active` | `#F5F5F4` fill, `22dp` radius, `1.5dp #F97316` border |
| `bg_send_idle` | `#F3F2F0` fill, `13dp` radius |
| `bg_send_active` | Orange gradient `135°`, `13dp` radius |
| `ic_attachment` | `ImagePlus` vector, 18dp, `#A8A29E` |

---

## Data Flow Once Everything is Built

```
User types message → presses Send
→ ChatFragment extracts text → calls chatViewModel.sendMessage(text)
→ ChatViewModel → chatRepository.sendMessage(content)
→ ChatRepositoryImpl.sendMessage():
    WS frame: {"type":"CHAT_MESSAGE","content":"Hello"}
    → sent to ws://host/api/v1/sessions/{id}/chat

Backend ChatWebSocketHandler.handleTextMessage():
  → parse frame
  → chatCommandService.processMessage()
      → validate session not closed (PostgreSQL check)
      → ChatMessage.create(sessionId, senderId, senderName, content)
      → messageRepository.save(message) → MongoDB chat_messages
      → return ChatMessage
  → build OutboundFrame JSON
  → broadcast to all WebSocketSessions in session bucket (both users)

Both clients receive frame:
  → ChatRepositoryImpl.onMessage() → parseMessage() → appendMessage() (deduplicates by messageId)
  → messagesLiveData.postValue(updatedList)
  → ChatFragment.observe() → chatAdapter.submitList() → RecyclerView scrolls to bottom
```
