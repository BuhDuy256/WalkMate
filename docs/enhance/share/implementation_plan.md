# Implementation Plan — Walk Result Post Feature

WalkMate · Android Native (Java) + Spring Boot

---

## Reconciliation Summary

| # | Issue | Resolution |
|---|---|---|
| A | Endpoint prefix was `/api/v1/walk-sessions/` | Changed to `/api/v1/sessions/` to match existing session API |
| B | `canPost` rule missing finalized-metrics check and participant check | Final rule: `callerStatus == COMPLETED && !hasPosted && caller is participant && callerEndedAt non-null && sessionStatus != CANCELLED [&& != FAILED only if FAILED exists in enum]` |
| C | Chat included in MVP scope | Deferred entirely; removed `canChat`, `btnChat`, Chat QA items |
| D | `currentUserReviewId` required a new `WalkReviewRepository` method | **Not added in MVP.** Existing `ReviewSnapshot` used to render View Review. If insufficient, show "Reviewed" state only and defer. |
| E | Application service returned `WalkPostResponse` DTO | Services return domain `WalkPost`; controller maps to DTO (never crosses application boundary) |
| F | Session postability logic inside `WalkPost.validatePostable()` | Moved to `WalkSession.canUserPost()` or dedicated `WalkPostPolicy` |
| G | DDL indexes were single-column only | Changed to composite: `(author_id, created_at DESC)` and `(author_id, visibility, created_at DESC)` |
| H | `PostVisibility.from()` behavior was conflated across layers | **Backend** (domain): throws `WALK_POST_INVALID_VISIBILITY` for invalid/null input — invalid request is rejected. **Frontend** (domain): maps unknown/null response visibility to `PRIVATE` and logs a warning — unknown response must not widen access. |
| I | Duplicate post error assumed HTTP 409 | `GlobalExceptionHandler` maps all `DomainException` → 400; frontend reloads History on `WALK_POST_DUPLICATED` |
| J | Hard delete caveat (repost allowed) not stated | After deletion, UNIQUE constraint no longer applies; user may repost the same session |
| K | Route preview treated as a real asset URL | `routePreviewUrl` column kept nullable; MVP renders static local placeholder drawable only |
| L | Blocked visibility rule ambiguous | Either party blocking the other → return empty list |
| M | `CreateWalkPostFragment` args missing stats primitives | Pass `distanceKm`, `durationSeconds`, `hotspotName` as Bundle primitives to avoid re-fetch |
| N | ACTIVE + caller COMPLETED: no loading state needed | Frontend hides "Post My Walk" until `canPost=true`. No waiting/loading state. Backend-enforced finalization gates `canPost`. |
| O | Cancelled "View Details" deferred | CANCELLED cards show **Report only** in MVP. "View Details" deferred until RouteReplayActivity safety for sessions with no GPS data is confirmed. |
| P | FAILED in canPost rule | Only exclude CANCELLED by name. FAILED excluded only if it exists in the `WalkSession` status enum. NO_SHOW self-enforces via `callerPersonalStatus != COMPLETED`. |
| Q | Visibility request vs. response conflated | Backend rejects invalid request visibility (400 `WALK_POST_INVALID_VISIBILITY`). Frontend `PostVisibility.from()` falls back to `PRIVATE` for unknown response values and logs a warning. |
| R | Frontend domain repository accepted a DTO param | `domain/walkpost/WalkPostRepository` (frontend) must not reference `CreateWalkPostRequest`. `createPost()` takes domain-typed params. `WalkPostRepositoryImpl` constructs the DTO internally. |
| S | `GET /profiles/{userId}/posts` null viewerId not handled | If viewerId is null (unauthenticated), return PUBLIC posts only. Skip friendship/block checks entirely with null viewerId. |
| T | "View Post" deep-link/highlight not clarified | "View Post" opens `WalkActivityFragment` at the top in MVP. Scrolling to / highlighting the specific post is deferred. |
| U | Caption normalization order not specified | Trim caption first, then check length (max 150). If blank after trim, store null or empty per existing project convention. |

---

## 1. Implementation Principles

### Backend (Backend_VI.md)

- `domain/walkpost/` contains only Rich Domain Model logic. `WalkPost` validates its own invariants and throws `DomainException`.
- `application/walkpost/` contains use-case coordination only — no business logic, no SQL, no HTTP concepts. **Application services return domain objects (`WalkPost`), not DTOs. DTOs are constructed in the controller layer only.**
- `infrastructure/repository/walkpost/` implements `WalkPostRepository` interface using JDBC/jOOQ. Never leaks Web annotations.
- `presentation/controller/walkpost/` validates HTTP input with `@Valid`, converts to Command objects, delegates to Application services, **maps returned domain object to `WalkPostResponse` DTO**. No `try-catch`.
- All exceptions bubble to `GlobalExceptionHandler`. No controller catches exceptions. **`GlobalExceptionHandler` maps all `DomainException` to HTTP 400 — not 409.**
- DTO (`WalkPostRequest` / `WalkPostResponse`) lives exclusively in `presentation/dto/`. Never reaches `domain/` or `application/`.
- Technology suffix rule: `WalkPostJdbcRepository`, not `WalkPostRepository` for the impl class.
- **Session postability logic lives in `WalkSession` (a `canUserPost(String userId)` method) or a dedicated `WalkPostPolicy` — not inside `WalkPost`.**

### Frontend (Frontend_VI.md)

- MVVM: Fragment observes `LiveData<UiState>`. Clicks call ViewModel methods directly.
- No RxJava, no Coroutines. Use `ExecutorService` for background tasks.
- Manual DI via `WalkMateApplication`. Register `WalkPostRepository` singleton there.
- DTOs (`WalkPostResponse`, `CreateWalkPostRequest`) live in `data/datasource/remote/dto/`. Never exposed to `ui/` or `domain/`. **`CreateWalkPostRequest` is constructed inside `WalkPostRepositoryImpl` from the domain params passed to `createPost()` — it must not appear in the `domain/walkpost/WalkPostRepository` interface.**
- Domain model (`WalkPost`, `PostVisibility`) lives in `domain/walkpost/`. ViewModel uses this model.
- Mapper (`WalkPostMapper`) lives in `data/mapper/`. Repository uses it to convert DTO → domain before `DomainCallback`.
- Adapter and Fragment are "thin". All conditional button logic in adapter uses pre-computed model fields (`canPost`, `hasPosted`, etc.) rather than re-deriving status.
- Do not add MVI (`UiEvent` / `UiEffect`). Use direct method calls.
- Custom views for any UI pattern appearing in ≥ 3 places or containing internal state.

---

## 2. Proposed Data Model

### 2.1 New table: `walk_post`

```sql
CREATE TABLE public.walk_post (
  post_id       uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id    uuid NOT NULL,
  author_id     uuid NOT NULL,

  caption       text,
  visibility    character varying NOT NULL DEFAULT 'PUBLIC',

  show_companion  boolean NOT NULL DEFAULT true,
  show_route_map  boolean NOT NULL DEFAULT false,
  show_stats      boolean NOT NULL DEFAULT true,

  distance_km        numeric NOT NULL DEFAULT 0 CHECK (distance_km >= 0),
  duration_seconds   bigint  NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
  points_earned      integer NOT NULL DEFAULT 0 CHECK (points_earned >= 0),

  route_preview_url  text,

  created_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT walk_post_pkey PRIMARY KEY (post_id),
  CONSTRAINT walk_post_session_fkey
    FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT walk_post_author_fkey
    FOREIGN KEY (author_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_post_unique_author_session
    UNIQUE (session_id, author_id),
  CONSTRAINT walk_post_visibility_check
    CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);

-- Composite indexes to support ORDER BY in common queries
CREATE INDEX idx_walk_post_author_created
    ON public.walk_post (author_id, created_at DESC);
CREATE INDEX idx_walk_post_author_visibility_created
    ON public.walk_post (author_id, visibility, created_at DESC);
```

### 2.2 Hard delete vs. soft delete

Use **hard delete** for MVP. The `walk_session` record retains all stats. A deleted post is simply gone. No soft-delete pattern exists in the currently inspected domain files.

**Repost allowed**: After a post is deleted, the `UNIQUE (session_id, author_id)` constraint no longer applies. The user may post the same session again. Backend must treat re-creation as a fresh post, not a duplicate.

If the team later wants an audit trail, add a `status character varying NOT NULL DEFAULT 'PUBLISHED'` column via a subsequent migration. Do not add it now.

### 2.3 Changes to existing tables

No existing table requires schema changes. The `walk_session` table already has all per-user stats needed to snapshot into `walk_post`.

### 2.4 `points_earned` handling

`walk_session` does not store a per-session point delta. For MVP:

- Default `points_earned = 0` in `walk_post`. Do not display it in the post card UI for MVP.
- The column is retained for future use once per-session delta is available from `GamificationCommandService`.

### 2.5 Migration order

| Step | File | Description |
|---|---|---|
| 1 | `V{N}__create_walk_post_table.sql` | Creates `walk_post`, UNIQUE constraint, composite indexes |
| — | No further schema changes for MVP | — |

Rollback: `DROP TABLE public.walk_post CASCADE;` — safe since no existing table references it.

---

## 3. Backend Implementation Plan

### Phase 1 — Migration

**File**: `src/main/resources/db/migration/V{N}__create_walk_post_table.sql`

Content: full DDL from section 2.1.

### Phase 2 — Domain Model

**Package**: `domain/walkpost/`

**`PostVisibility.java`** (enum):
```java
public enum PostVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    public static PostVisibility from(String raw) {
        for (PostVisibility v : values()) {
            if (v.name().equals(raw)) return v;
        }
        throw new DomainException(WalkPostErrorCode.WALK_POST_INVALID_VISIBILITY);
    }
}
```

**`WalkPost.java`** (Rich Domain Entity):
- Fields: `postId`, `sessionId`, `authorId`, `caption`, `visibility`, `showCompanion`, `showRouteMap`, `showStats`, `distanceKm`, `durationSeconds`, `pointsEarned`, `routePreviewUrl`, `createdAt`, `updatedAt`
- Factory: `WalkPost.create(String sessionId, String authorId, String caption, PostVisibility visibility, boolean showCompanion, boolean showRouteMap, boolean showStats, double distanceKm, long durationSeconds)` — trims caption first, then rejects if trimmed length > 150 (throws `WALK_POST_CAPTION_TOO_LONG`). If blank after trim, store as `null` or `""` following the existing project convention for optional text fields (check other domain entities for precedent). Sets defaults for remaining fields.
- Domain behavior: `changeVisibility(PostVisibility newVisibility, String requesterId)` — throws `WALK_POST_FORBIDDEN` if `requesterId != authorId`.

**Session postability** lives in `WalkSession` (a `canUserPost(String userId)` method) or a dedicated `WalkPostPolicy`. The application service calls this before creating a post — not `WalkPost` itself.

**`WalkPostRepository.java`** (interface):
```java
public interface WalkPostRepository {
    WalkPost save(WalkPost post);
    Optional<WalkPost> findById(String postId);
    boolean existsBySessionAndAuthor(String sessionId, String authorId);
    Optional<String> findPostIdBySessionAndAuthor(String sessionId, String authorId);
    List<WalkPost> findByAuthor(String authorId);
    List<WalkPost> findVisibleByAuthor(String authorId, boolean isFriend);
    Map<String, Boolean> findExistenceMapBySessionIdsAndAuthor(Set<String> sessionIds, String authorId);
    void deleteById(String postId);
}
```

**`WalkPostErrorCode.java`**:
```java
public enum WalkPostErrorCode implements ErrorCode {
    WALK_POST_NOT_FOUND,
    WALK_POST_SESSION_NOT_FOUND,
    WALK_POST_AUTHOR_NOT_PARTICIPANT,
    WALK_POST_PERSONAL_STATUS_NOT_COMPLETED,
    WALK_POST_METRICS_NOT_FINALIZED,   // thrown when caller finalized-metrics field is null
    WALK_POST_SESSION_NOT_POSTABLE,    // thrown for CANCELLED (and FAILED if that enum value exists)
    WALK_POST_DUPLICATED,
    WALK_POST_INVALID_VISIBILITY,
    WALK_POST_CAPTION_TOO_LONG,
    WALK_POST_FORBIDDEN
}
```

### Phase 3 — Repository Interface (covered in Phase 2)

### Phase 4 — Repository Implementation

**Package**: `infrastructure/repository/walkpost/`

**`WalkPostJdbcRepository.java`** implements `WalkPostRepository`:
- `save()`: INSERT via `NamedParameterJdbcTemplate` or jOOQ (match existing pattern). Re-query on `post_id` to return the saved record.
- `findById()`: `SELECT * FROM walk_post WHERE post_id = :postId`
- `existsBySessionAndAuthor()`: `SELECT EXISTS(SELECT 1 FROM walk_post WHERE session_id=:sid AND author_id=:aid)`
- `findPostIdBySessionAndAuthor()`: `SELECT post_id FROM walk_post WHERE session_id=:sid AND author_id=:aid`
- `findByAuthor()`: `SELECT * FROM walk_post WHERE author_id=:aid ORDER BY created_at DESC`
- `findVisibleByAuthor(authorId, isFriend)`:
  - If `isFriend`: `WHERE author_id=:aid AND visibility IN ('PUBLIC','FRIENDS') ORDER BY created_at DESC`
  - If not: `WHERE author_id=:aid AND visibility='PUBLIC' ORDER BY created_at DESC`
- `findExistenceMapBySessionIdsAndAuthor()`: `SELECT session_id FROM walk_post WHERE session_id IN (:sids) AND author_id=:aid` — return as `Map<String, Boolean>` keyed by sessionId (present = true).
- `deleteById()`: `DELETE FROM walk_post WHERE post_id=:postId`

### Phase 5 — Command Service

**Package**: `application/walkpost/`

**`CreateWalkPostCommand.java`** (record):
```java
public record CreateWalkPostCommand(
    String sessionId, String authorId,
    String caption, String visibility,
    boolean showCompanion, boolean showRouteMap, boolean showStats
) {}
```

**`UpdateWalkPostVisibilityCommand.java`** (record):
```java
public record UpdateWalkPostVisibilityCommand(
    String postId, String requesterId, String newVisibility
) {}
```

**`WalkPostCommandService.java`**:

`createPost(CreateWalkPostCommand command)` → returns domain `WalkPost`:
1. Load `WalkSession` from `WalkSessionRepository`. Throw `WALK_POST_SESSION_NOT_FOUND` if missing.
2. Verify caller is participant (`userIdA` or `userIdB`). Throw `WALK_POST_AUTHOR_NOT_PARTICIPANT`.
3. Derive caller personal status. Throw `WALK_POST_PERSONAL_STATUS_NOT_COMPLETED` if not `COMPLETED`.
4. Verify caller metrics are finalized using the actual per-user ended-at or finalized-metrics field on `WalkSession`. **Check the real field name in `WalkSession.java`; do not assume `getUserAEndedAt()` exists.** Throw `WALK_POST_METRICS_NOT_FINALIZED` if not finalized.
5. Check `session.status == CANCELLED`. Throw `WALK_POST_SESSION_NOT_POSTABLE`. Also exclude `FAILED` only if `FAILED` exists in the `WalkSession` status enum — verify this before coding.
6. Check `walkPostRepository.existsBySessionAndAuthor(sessionId, authorId)`. Throw `WALK_POST_DUPLICATED`.
7. Resolve `distanceKm` and `durationSeconds` for caller from `WalkSession`.
8. Call `WalkPost.create(...)` — domain validates caption, visibility.
9. Persist via `walkPostRepository.save(post)`.
10. **Return the saved `WalkPost` domain object. Controller maps it to `WalkPostResponse` DTO.**

`updateVisibility(UpdateWalkPostVisibilityCommand command)` → returns domain `WalkPost`:
1. Load `WalkPost` by `postId`. Throw `WALK_POST_NOT_FOUND` if missing.
2. Call `post.changeVisibility(PostVisibility.from(newVisibility), requesterId)` — domain throws `WALK_POST_FORBIDDEN` if not author.
3. Save and return updated post.

`deletePost(String postId, String requesterId)`:
1. Load `WalkPost`. Throw `WALK_POST_NOT_FOUND` if missing.
2. Check `post.getAuthorId().equals(requesterId)`. Throw `WALK_POST_FORBIDDEN`.
3. `walkPostRepository.deleteById(postId)`.
4. **After deletion, user may re-create a post for the same session (hard delete removes the UNIQUE row).**

### Phase 6 — Query Service

**`WalkPostQueryService.java`**:

`getMyPosts(String authorId)` → `List<WalkPost>`:
- `walkPostRepository.findByAuthor(authorId)` — returns all visibility levels for the owner.

`getUserPosts(String authorId, String viewerId)` → `List<WalkPost>`:
1. If `viewerId == null` → unauthenticated caller. Return PUBLIC posts only via `walkPostRepository.findVisibleByAuthor(authorId, false)`. Skip all friendship/block checks.
2. If `viewerId.equals(authorId)` → return all posts (same as `getMyPosts`).
3. `isBlocked = socialRepository.isBlocked(authorId, viewerId) || socialRepository.isBlocked(viewerId, authorId)` — **either direction** → return empty list.
4. `isFriend = socialRepository.areAcceptedFriends(UUID.fromString(viewerId), UUID.fromString(authorId))`.
5. `walkPostRepository.findVisibleByAuthor(authorId, isFriend)`.

### Phase 7 — Controller and DTOs

**Package**: `presentation/controller/walkpost/`

**`WalkPostController.java`** (`@RequestMapping("/api/v1")`):

```java
// POST /api/v1/sessions/{sessionId}/posts
@PostMapping("/sessions/{sessionId}/posts")
public ResponseEntity<ApiResponse<WalkPostResponse>> createPost(
    @AuthenticationPrincipal UserPrincipal principal,
    @PathVariable String sessionId,
    @Valid @RequestBody CreateWalkPostRequest request)

// PATCH /api/v1/walk-posts/{postId}/visibility
@PatchMapping("/walk-posts/{postId}/visibility")
public ResponseEntity<ApiResponse<WalkPostResponse>> updateVisibility(
    @AuthenticationPrincipal UserPrincipal principal,
    @PathVariable String postId,
    @Valid @RequestBody UpdateWalkPostVisibilityRequest request)

// DELETE /api/v1/walk-posts/{postId}
@DeleteMapping("/walk-posts/{postId}")
public ResponseEntity<ApiResponse<Void>> deletePost(
    @AuthenticationPrincipal UserPrincipal principal,
    @PathVariable String postId)
```

**Package**: `presentation/controller/profile/`:

```java
// GET /api/v1/profiles/me/posts
@GetMapping("/profiles/me/posts")
public ResponseEntity<ApiResponse<List<WalkPostResponse>>> getMyPosts(
    @AuthenticationPrincipal UserPrincipal principal)

// GET /api/v1/profiles/{userId}/posts
@GetMapping("/profiles/{userId}/posts")
public ResponseEntity<ApiResponse<List<WalkPostResponse>>> getUserPosts(
    @AuthenticationPrincipal UserPrincipal principal,
    @PathVariable String userId)
```

**`CreateWalkPostRequest.java`**:
```java
public record CreateWalkPostRequest(
    @Size(max = 150) String caption,
    @NotBlank String visibility,
    boolean showCompanion,
    boolean showRouteMap,
    boolean showStats
) {}
```

**`UpdateWalkPostVisibilityRequest.java`**:
```java
public record UpdateWalkPostVisibilityRequest(
    @NotBlank String visibility
) {}
```

### Phase 8 — Update History API

**`SessionHistoryQueryService`**: extend `toSummary()` method.

Add to `SessionSummaryResponse` record:
```java
@JsonProperty("current_user_personal_status") String currentUserPersonalStatus,
@JsonProperty("partner_personal_status")      String partnerPersonalStatus,
@JsonProperty("current_user_has_posted")      boolean currentUserHasPosted,
@JsonProperty("current_user_post_id")         String currentUserPostId,  // nullable
@JsonProperty("can_post")                     boolean canPost,
@JsonProperty("can_review")                   boolean canReview,
@JsonProperty("can_report")                   boolean canReport
```

`current_user_review_id` is **not added in MVP**. The existing `review_snapshot` field already carries the review data needed to render "View Review".

In `toSummary()`:
- Derive `callerPersonalStatus`: caller's `userStatus` from the participant list.
- Derive `callerMetricsFinalized`: check the actual per-user ended-at or finalized-metrics field on `WalkSession`. **Verify the exact field name in `WalkSession.java`** (e.g. `userAEndedAt` / `userBEndedAt`); if the field does not exist, derive finalization from another domain invariant or add it. Do not assume `getUserAEndedAt()` exists.
- Derive `partnerPersonalStatus`: inverse participant status.
- Query `walkPostRepository.existsBySessionAndAuthor(sessionId, callerId)` → `hasPosted`.
- Query `walkPostRepository.findPostIdBySessionAndAuthor(sessionId, callerId)` → `postId` (nullable).
- Compute `canPost`:
  ```
  callerPersonalStatus == COMPLETED
  && !hasPosted
  && caller is participant of the session
  && callerMetricsFinalized == true
  && sessionStatus != CANCELLED
  // Also exclude FAILED only if FAILED exists in the WalkSession status enum.
  // NO_SHOW is self-excluding (callerPersonalStatus != COMPLETED).
  ```
- Compute `canReview = !isReviewed && callerPersonalStatus != NO_SHOW && partnerPersonalStatus == COMPLETED`.
- Compute `canReport = !isReported && callerPersonalStatus != NO_SHOW`.

**Performance**: Add `walkPostRepository.findExistenceMapBySessionIdsAndAuthor(Set<String> sessionIds, String authorId)` to batch `hasPosted` across the full history list and avoid N+1. Follow the same pattern used for existing profile snapshot batching.

---

## 4. Backend API Contract

### 4.1 POST /api/v1/sessions/{sessionId}/posts

**Auth**: Bearer token required.

**Request**:
```json
{
  "caption": "Great morning walk!",
  "visibility": "PUBLIC",
  "showCompanion": true,
  "showRouteMap": false,
  "showStats": true
}
```

**Response 200**:
```json
{
  "success": true,
  "data": {
    "post_id": "uuid",
    "session_id": "uuid",
    "author_id": "uuid",
    "author_name": "Luân Trần",
    "author_avatar_url": "https://...",
    "caption": "Great morning walk!",
    "visibility": "PUBLIC",
    "hotspot_name": "Tao Dan Park",
    "distance_km": 2.4,
    "duration_seconds": 1680,
    "points_earned": 0,
    "show_companion": true,
    "show_route_map": false,
    "show_stats": true,
    "companion_name": "Nguyen Minh",
    "route_preview_url": null,
    "created_at": "2026-05-08T09:41:00"
  }
}
```

**Domain validations** (all `DomainException` → **400** via `GlobalExceptionHandler`):
- Session not found → `WALK_POST_SESSION_NOT_FOUND`
- Caller not participant → `WALK_POST_AUTHOR_NOT_PARTICIPANT`
- Caller personal status != COMPLETED → `WALK_POST_PERSONAL_STATUS_NOT_COMPLETED`
- Caller `endedAt` is null → `WALK_POST_METRICS_NOT_FINALIZED`
- Session is CANCELLED → `WALK_POST_SESSION_NOT_POSTABLE`
- Session is FAILED (only if FAILED exists in `WalkSession` enum) → `WALK_POST_SESSION_NOT_POSTABLE`
- Caption > 150 chars → `WALK_POST_CAPTION_TOO_LONG`
- Visibility not in (PUBLIC, FRIENDS, PRIVATE) → `WALK_POST_INVALID_VISIBILITY`
- Duplicate post → `WALK_POST_DUPLICATED` (**400**, not 409)

### 4.2 GET /api/v1/profiles/me/posts

**Auth**: Bearer token required.

**Response 200**: All visibility levels returned (PUBLIC + FRIENDS + PRIVATE). Frontend filters locally on Walk Activity screen.

### 4.3 GET /api/v1/profiles/{userId}/posts

**Auth**: Optional. Unauthenticated callers are treated as strangers — PUBLIC posts only. Do not call friendship or block checks when viewerId is null.

**Response 200**: Filtered by viewer relationship:
- Unauthenticated / stranger: PUBLIC only
- Owner: all posts
- Accepted friend: PUBLIC + FRIENDS
- Blocked — **either direction** (viewer blocks author OR author blocks viewer): empty list

### 4.4 PATCH /api/v1/walk-posts/{postId}/visibility

**Request**: `{ "visibility": "FRIENDS" }`

**Response 200**: Updated `WalkPostResponse`.

**Error cases** (all 400): `WALK_POST_NOT_FOUND`, `WALK_POST_FORBIDDEN`, `WALK_POST_INVALID_VISIBILITY`.

### 4.5 DELETE /api/v1/walk-posts/{postId}

**Response 200**: `{ "success": true, "data": null }`

**Error cases**: `WALK_POST_NOT_FOUND`, `WALK_POST_FORBIDDEN`.

**Post-delete**: User may create a new post for the same session (hard delete removes the UNIQUE constraint row).

### 4.6 GET /api/v1/sessions/history (updated)

Adds to each item: `current_user_personal_status`, `partner_personal_status`, `current_user_has_posted`, `current_user_post_id` (nullable), `can_post`, `can_review`, `can_report`.

`current_user_review_id` is not added in MVP. The existing `review_snapshot` field is used for View Review rendering.

No breaking changes to existing fields.

---

## 5. Frontend Implementation Plan

### Phase 5A — Data DTOs

**`WalkPostResponse.java`** — `data/datasource/remote/dto/response/walkpost/`:
```java
public class WalkPostResponse {
    @SerializedName("post_id")           private String postId;
    @SerializedName("session_id")        private String sessionId;
    @SerializedName("author_id")         private String authorId;
    @SerializedName("author_name")       private String authorName;
    @SerializedName("author_avatar_url") private String authorAvatarUrl;
    @SerializedName("caption")           private String caption;
    @SerializedName("visibility")        private String visibility;
    @SerializedName("hotspot_name")      private String hotspotName;
    @SerializedName("distance_km")       private double distanceKm;
    @SerializedName("duration_seconds")  private long durationSeconds;
    @SerializedName("points_earned")     private int pointsEarned;
    @SerializedName("show_companion")    private boolean showCompanion;
    @SerializedName("show_route_map")    private boolean showRouteMap;
    @SerializedName("show_stats")        private boolean showStats;
    @SerializedName("companion_name")    private String companionName;
    @SerializedName("route_preview_url") private String routePreviewUrl;
    @SerializedName("created_at")        private String createdAt;
    // getters only
}
```

**`CreateWalkPostRequest.java`** — `data/datasource/remote/dto/request/walkpost/`:
```java
public class CreateWalkPostRequest {
    @SerializedName("caption")        private final String caption;
    @SerializedName("visibility")     private final String visibility;
    @SerializedName("show_companion") private final boolean showCompanion;
    @SerializedName("show_route_map") private final boolean showRouteMap;
    @SerializedName("show_stats")     private final boolean showStats;
    // constructor + getters
}
```

**`UpdateWalkPostVisibilityRequest.java`** — same package:
```java
public class UpdateWalkPostVisibilityRequest {
    @SerializedName("visibility") private final String visibility;
    // constructor + getter
}
```

Update `SessionSummaryResponse.java` (frontend DTO) to add:
```java
@SerializedName("current_user_personal_status") private String currentUserPersonalStatus;
@SerializedName("partner_personal_status")      private String partnerPersonalStatus;
@SerializedName("current_user_has_posted")      private boolean currentUserHasPosted;
@SerializedName("current_user_post_id")         private String currentUserPostId; // nullable
@SerializedName("can_post")                     private boolean canPost;
@SerializedName("can_review")                   private boolean canReview;
@SerializedName("can_report")                   private boolean canReport;
// current_user_review_id is NOT added in MVP — use existing review_snapshot field
```

### Phase 5B — API Interface

**`WalkPostApiService.java`** — `data/datasource/remote/api/`:
```java
@POST("sessions/{sessionId}/posts")
Call<ApiResponse<WalkPostResponse>> createPost(
    @Path("sessionId") String sessionId,
    @Body CreateWalkPostRequest body);

@GET("profiles/me/posts")
Call<ApiResponse<List<WalkPostResponse>>> getMyPosts();

@GET("profiles/{userId}/posts")
Call<ApiResponse<List<WalkPostResponse>>> getUserPosts(@Path("userId") String userId);

@PATCH("walk-posts/{postId}/visibility")
Call<ApiResponse<WalkPostResponse>> updateVisibility(
    @Path("postId") String postId,
    @Body UpdateWalkPostVisibilityRequest body);

@DELETE("walk-posts/{postId}")
Call<ApiResponse<Void>> deletePost(@Path("postId") String postId);
```

### Phase 5C — Mapper

**`WalkPostMapper.java`** — `data/mapper/`:
```java
public class WalkPostMapper {
    public static WalkPost toDomain(WalkPostResponse dto) {
        return new WalkPost(
            dto.getPostId(), dto.getSessionId(), dto.getAuthorId(),
            dto.getAuthorName(), dto.getAuthorAvatarUrl(),
            dto.getCaption(), PostVisibility.from(dto.getVisibility()),
            dto.getHotspotName(),
            dto.getDistanceKm(), dto.getDurationSeconds(), dto.getPointsEarned(),
            dto.isShowCompanion(), dto.isShowRouteMap(), dto.isShowStats(),
            dto.getCompanionName(), dto.getRoutePreviewUrl(), dto.getCreatedAt()
        );
    }
    public static List<WalkPost> toDomainList(List<WalkPostResponse> dtos) { ... }
    private WalkPostMapper() {}
}
```

Update `SessionSummaryMapper.toDomain()` to map the new fields from `SessionSummaryResponse` into `SessionSummary`.

### Phase 5D — Repository

**`WalkPostRepository.java`** (interface) — `domain/walkpost/`:
```java
public interface WalkPostRepository {
    // Domain-typed params only — no DTO crosses the domain boundary.
    void createPost(String sessionId, String caption, PostVisibility visibility,
                    boolean showCompanion, boolean showRouteMap, boolean showStats,
                    DomainCallback<WalkPost> callback);
    void getMyPosts(DomainCallback<List<WalkPost>> callback);
    void getUserPosts(String userId, DomainCallback<List<WalkPost>> callback);
    void updateVisibility(String postId, String visibility, DomainCallback<WalkPost> callback);
    void deletePost(String postId, DomainCallback<Void> callback);
}
```

**`WalkPostRepositoryImpl.java`** — `data/repository/`:
- Implements `WalkPostRepository`.
- Uses `ExecutorService` for all network calls (follow pattern of `WalkSessionRepositoryImpl`).
- **Constructs `CreateWalkPostRequest` internally** from the domain params received in `createPost()`, then passes it to `WalkPostApiService`. The DTO never surfaces outside this class.
- Maps `WalkPostResponse` → `WalkPost` via `WalkPostMapper` before calling `callback.onSuccess()`.

### Phase 5E — Domain Model

**`PostVisibility.java`** (enum) — `domain/walkpost/`:
```java
public enum PostVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    public static PostVisibility from(String raw) {
        if (raw == null) return PRIVATE;
        try { return valueOf(raw); } catch (IllegalArgumentException e) { return PRIVATE; }
    }

    public String toDisplayLabel() {
        switch (this) {
            case PUBLIC:  return "Public";
            case FRIENDS: return "Friends";
            case PRIVATE: return "Only me";
            default:      return "Only me";
        }
    }
}
```

Fallback is `PRIVATE` (not `PUBLIC`) for null or unknown strings — applies to **response parsing only** (mapping backend response to domain model). Unknown visibility in a response must not widen access. Log a warning when fallback is triggered. The `VisibilitySelectorView` only exposes PUBLIC / FRIENDS / PRIVATE so invalid values cannot be sent in requests from this client.

Note: the **backend** `PostVisibility.from()` has the opposite behavior — it throws `WALK_POST_INVALID_VISIBILITY` for invalid/null input, because invalid request visibility should be rejected, not silently corrected.

**`WalkPost.java`** (lightweight model, pure getters) — `domain/walkpost/`:
- All fields from `WalkPostResponse` mapped to Java types. No business logic.

Update `SessionSummary.java` — add fields: `currentUserPersonalStatus`, `partnerPersonalStatus`, `hasPosted`, `postId` (nullable), `canPost`, `canReview`, `canReport`.

`reviewId` is **not added in MVP**. The existing `ReviewSnapshot` field on `SessionSummary` already carries the review data needed for "View Review" rendering.

### Phase 6 — CreateWalkPost UI

**Package**: `ui/walkpost/create/`

**`CreateWalkPostUiState.java`**:
```java
public class CreateWalkPostUiState {
    public final boolean isLoading;
    public final boolean isSuccess;
    public final String errorMessage;
    public final String sessionId;
    public final String caption;
    public final PostVisibility selectedVisibility;
    public final boolean showCompanion;
    public final boolean showRouteMap;
    public final boolean showStats;
    // static factory: loading(), ready(...), success(), error(msg)
}
```

**`CreateWalkPostViewModel.java`**:
- Takes `WalkPostRepository` in constructor.
- Methods: `setCaption(String)`, `setVisibility(PostVisibility)`, `toggleShowCompanion()`, `toggleShowRouteMap()`, `toggleShowStats()`, `submit()`.
- `submit()` trims the caption first, then validates trimmed length (≤ 150) client-side before calling `repository.createPost()` via `ExecutorService`. Passes trimmed caption (or null if blank after trim) to the repository.
- On success: post `UiState.success()` and fire a navigate-back event.

**`CreateWalkPostFragment.java`** layout (`fragment_create_walk_post.xml`):
- Header with back button + title "Create Walk Post"
- Caption `EditText` (max 150 chars via `inputFilters`)
- `VisibilitySelectorView` (custom view)
- Three toggle rows: Show companion, Show route map, Show walk stats (`MaterialSwitch`)
- Preview card (`WalkResultPostCard` in "preview" mode)
  - When `showRouteMap = true`, display a **static local placeholder drawable** (e.g. `R.drawable.ic_route_map_placeholder`). Do not load any URL. Real route image generation is deferred.
- Primary button: "Post to Profile" (`WalkMateButton` FILLED style)

**Arguments received from History** (all passed as Bundle primitives to avoid extra network calls):
- `sessionId` (String)
- `partnerName` (String, nullable)
- `myWalkOnly` (boolean)
- `distanceKm` (double)
- `durationSeconds` (long)
- `hotspotName` (String)

**`CreateWalkPostViewModelFactory.java`**: takes `WalkPostRepository` from `WalkMateApplication`.

### Phase 7 — Walk History Integration

**Changes to `SessionSummary.java`**: Add `hasPosted`, `postId` (nullable), `canPost`, `canReview`, `canReport`, `currentUserPersonalStatus`, `partnerPersonalStatus`. Do not add `reviewId` — use existing `ReviewSnapshot` field.

**Changes to `SessionHistoryAdapter`**:

New listener interfaces:
- `OnPostClickListener` — `void onPostClick(String sessionId, String partnerName, boolean myWalkOnly, double distanceKm, long durationSeconds, String hotspotName)`
- `OnViewPostClickListener` — `void onViewPostClick(String postId)`
- `OnViewReviewClickListener` — `void onViewReviewClick(String sessionId)` — passes `sessionId` and data from `ReviewSnapshot`; no `reviewId` in MVP. If `SubmitReviewFragment` cannot load a review from `sessionId` alone, show a "Reviewed" label only and defer the view.

New views in `item_session_history.xml`:
- `txtPostedChip` — `TextView` with purple pill drawable, `text="POSTED"`, `visibility="gone"` by default
- `btnPost` — `MaterialButton`, orange outline style, label set dynamically
- `btnViewPost` — `MaterialButton`, purple outline style, `text="View Post"`
- `btnViewReview` — `MaterialButton`, green outline style, `text="✓ View Review"`

Note: do **not** add `btnViewDetails` for CANCELLED cards in MVP. "View Details" is deferred.

**Revised `onBindViewHolder` logic** (grouped by state variant):

```
Reset: all action buttons GONE, divider GONE, postedChip GONE

// State A: ACTIVE global, caller COMPLETED
// Only shown when backend returns canPost=true (metrics finalized). No frontend loading state.
// Add this as a NEW parallel branch — do not modify the existing COMPLETED branch.
if (global == ACTIVE && summary.canPost):
    show divider
    btnPost.setText("Post My Walk")
    show btnPost → onPostClick(sessionId, partnerName, myWalkOnly=true, distanceKm, durationSeconds, hotspotName)
    show btnReport

// State B/C/D/E: COMPLETED
if (global == COMPLETED):
    show divider

    if (summary.hasPosted):
        show txtPostedChip

    // Review button (left slot)
    if (!summary.canReview && summary.isReviewed):
        show btnViewReview → onViewReviewClick(sessionId)
    else if (summary.canReview):
        show btnReview → onReviewClick(sessionId)

    // Post button (right slot)
    if (!summary.hasPosted && summary.canPost):
        btnPost.setText("Post to Profile")
        show btnPost → onPostClick(sessionId, partnerName, myWalkOnly=false, distanceKm, durationSeconds, hotspotName)
    else if (summary.hasPosted && summary.postId != null):
        show btnViewPost → onViewPostClick(postId)

    show btnReport

// State F: CANCELLED
// MVP: Report only. "View Details" deferred until RouteReplayActivity safety confirmed.
if (global == CANCELLED):
    show divider
    show btnReport
```

**Changes to `SessionHistoryFragment`**:
```java
adapter.setOnPostClickListener((sessionId, partnerName, myWalkOnly, distanceKm, durationSeconds, hotspotName) -> {
    Bundle args = new Bundle();
    args.putString("sessionId", sessionId);
    args.putString("partnerName", partnerName);
    args.putBoolean("myWalkOnly", myWalkOnly);
    args.putDouble("distanceKm", distanceKm);
    args.putLong("durationSeconds", durationSeconds);
    args.putString("hotspotName", hotspotName);
    NavHostFragment.findNavController(this)
        .navigate(R.id.action_sessionHistory_to_createWalkPostFragment, args);
});

adapter.setOnViewPostClickListener(postId -> {
    NavHostFragment.findNavController(this)
        .navigate(R.id.action_sessionHistory_to_walkActivityFragment);
});
```

### Phase 8 — Walk Activity Screen

**Package**: `ui/profile/activity/`

**`WalkActivityUiState.java`**:
```java
public class WalkActivityUiState {
    public final boolean isLoading;
    public final String errorMessage;
    public final PostVisibility selectedFilter;  // null = ALL
    public final List<WalkPost> posts;
}
```

**`WalkActivityViewModel.java`**:
- Takes `WalkPostRepository` in constructor.
- `loadPosts()` → calls `repository.getMyPosts()` via `ExecutorService`.
- `selectFilter(PostVisibility filter)` → posts `UiState` with new filter, no new API call.
- `changeVisibility(String postId, String newVisibility)` → calls `repository.updateVisibility()`.
- `deletePost(String postId)` → calls `repository.deletePost()`.

**`WalkActivityFragment.java`** layout (`fragment_walk_activity.xml`):
- Header with back + "Walk Activity"
- Filter chip row: All / Public / Friends / Only me (single-selection `ChipGroup`)
- Post count `TextView`
- `RecyclerView` with `WalkResultPostCard` in "owner" variant
- Empty state with "Go to History" button

**Changes to `ProfileFragment`**:
- Add `menuWalkActivity = root.findViewById(R.id.menuWalkActivity)` in `bindViews()`
- Add `menuWalkActivity.setOnClickListener(v -> viewModel.onWalkActivityClicked())` in `setupClickListeners()`
- Add observer for `viewModel.getNavigateToWalkActivityEvent()` → navigate to `action_profile_to_walkActivityFragment`

**Changes to `ProfileViewModel`**:
- Add `onWalkActivityClicked()`, `navigateToWalkActivity` LiveData, getter, consumer.

**Changes to `WalkMateApplication`**:
```java
private WalkPostRepository walkPostRepository;

public WalkPostRepository getWalkPostRepository() {
    if (walkPostRepository == null) {
        walkPostRepository = new WalkPostRepositoryImpl(
            ApiClient.getInstance(this).create(WalkPostApiService.class),
            executorService);
    }
    return walkPostRepository;
}
```

### Phase 9 — Public Profile Recent Walks Integration

**Changes to `PublicProfileFragment`**:
- Add `LinearLayout layoutRecentWalks` below the reviews section in `fragment_public_profile.xml`
- Add `TextView txtNoRecentWalks`
- Inflate at most 5 `item_walk_post_preview.xml` views (no RecyclerView — avoids nested scroll issues)
- Load via `PublicProfileViewModel.loadUserPosts(userId)`

**Changes to `PublicProfileViewModel`**:
- Takes `WalkPostRepository` in constructor (via `PublicProfileViewModelFactory`)
- `loadProfile(userId)`: load profile data + load posts in parallel via separate `ExecutorService` tasks
- Posts filtered by backend — no client-side visibility re-filtering needed

---

## 6. UI Integration Strategy

### History Item Card — Action Area

The action area is split into two rows:

**Row 1** (primary buttons):
- Left slot: Review button (Leave a Review / View Review / hidden)
- Right slot: Post button (Post to Profile / Post My Walk / View Post / hidden)

**Row 2** (secondary):
- Right-aligned: Report button (ghost link style, small)

**POSTED chip** appears in the card header row when `hasPosted == true`. Style: purple pill (`bg_chip_posted.xml`: fill `#F5F3FF`, stroke `#DDD6FE` 1.5dp, corner radius 100dp, text color `#7C3AED`, 10sp bold).

### Post to Profile / Post My Walk / View Post rendering

| Condition | Button label | Style |
|---|---|---|
| `canPost && !hasPosted && !myWalkOnly` | "Post to Profile" | Orange outline |
| `canPost && !hasPosted && myWalkOnly` | "Post My Walk" | Orange outline |
| `hasPosted && postId != null` | "View Post" | Purple outline |

"View Post" navigates to `WalkActivityFragment` (owner's own Walk Activity screen). The screen opens at the top in MVP — scrolling to or highlighting the specific `postId` is deferred.

### Cancelled / No-show card actions

**MVP**: Show **Report only** → `ReportIncidentFragment`. Do NOT show View Details, Review, Post, or POSTED chip.

"View Details" (→ `RouteReplayActivity`) is deferred until it is confirmed that `RouteReplayActivity` handles sessions with no GPS data safely. Add it in a follow-up once that is verified.

### Walk Activity filters

Filters are a `ChipGroup` in single-selection mode: All / Public / Friends / Only me.

- Filter change updates `WalkActivityUiState.selectedFilter` via `viewModel.selectFilter()`.
- Fragment derives `filteredPosts` locally — no API call on filter change.

### Owner vs. public viewer post card

`WalkResultPostCard` Custom View supports two variants via `setVariant(Variant variant)`:

**Owner variant** (`Variant.OWNER`): visibility chip + overflow menu (Change Visibility / Delete).

**Viewer variant** (`Variant.VIEWER`): no visibility chip, no overflow menu.

---

## 7. State and Permission Rules

### canPost

Final rule (backend-enforced, pre-computed in history response):
```
callerPersonalStatus == COMPLETED
&& !hasPosted
&& caller is participant of the session
&& caller metrics are finalized
   (check the actual per-user ended-at / finalized-metrics field on WalkSession;
    verify field name before coding — do not assume getUserAEndedAt() exists)
&& sessionStatus != CANCELLED
   [&& sessionStatus != FAILED — only if FAILED exists in the WalkSession status enum;
    NO_SHOW self-enforces via callerPersonalStatus != COMPLETED]
```

The frontend simply shows/hides the Post button based on the backend-returned `canPost` flag. No frontend loading or waiting state is introduced. When `canPost=false` the button is GONE; when `canPost=true` it is shown.

### canViewPost

- `visibility == PUBLIC` → always visible
- `visibility == FRIENDS` → viewer is an accepted friend
- `visibility == PRIVATE` → viewer is the author
- **Either party** blocked the other → post is never shown

### canUpdateVisibility / canDeletePost

```
requesterId == post.authorId
```

After deletion, user may repost the same session (hard delete removes the UNIQUE row).

### Visibility rules by viewer relationship

| Viewer | PUBLIC | FRIENDS | PRIVATE |
|---|---|---|---|
| Owner (self) | Show | Show | Show |
| Accepted friend | Show | Show | Hide |
| Stranger | Show | Hide | Hide |
| Blocked (either direction) | Hide | Hide | Hide |

### Current user completed, partner still walking

Global status = `ACTIVE`. Caller personal status = `COMPLETED`. Caller `endedAt` is non-null.

- `canPost = true`, `canReview = false`, `canReport = true`
- Button label: "Post My Walk"
- Only caller's distance/duration are included in the post (partner's stats are 0 or partial)

### Duplicate post prevention

- Backend: UNIQUE constraint + pre-check in `WalkPostCommandService`.
- Frontend: `canPost = false` in history response prevents button from showing for posted sessions.
- On **400** `WALK_POST_DUPLICATED` from backend: reload the History list. The refreshed response returns `hasPosted=true` / `canPost=false`, updating the card to show "View Post" + POSTED chip automatically.

### Cancelled / No-show handling

- `sessionStatus == CANCELLED`: `canPost = false`. MVP shows **Report only**. "View Details" deferred.
- `FAILED` status: excluded from `canPost` only if `FAILED` exists in the `WalkSession` status enum. If it does, show Report only (same as CANCELLED). Verify enum values before coding.
- `callerStatus == NO_SHOW`: all action buttons GONE (consistent with current adapter behavior). `canPost` is self-excluded because `callerPersonalStatus != COMPLETED`.

---

## 8. Testing Plan

### Backend unit tests

| Test | Class under test |
|---|---|
| `WalkPost.create()` trims caption before checking length | `WalkPost` |
| `WalkPost.create()` rejects caption > 150 chars after trim | `WalkPost` |
| `WalkPost.create()` stores null (or empty per convention) for blank-after-trim caption | `WalkPost` |
| `WalkPost.changeVisibility()` throws WALK_POST_FORBIDDEN for non-author | `WalkPost` |
| `WalkPostCommandService.createPost()` throws NOT_PARTICIPANT when caller not in session | `WalkPostCommandService` |
| `WalkPostCommandService.createPost()` throws PERSONAL_STATUS_NOT_COMPLETED when caller != COMPLETED | `WalkPostCommandService` |
| `WalkPostCommandService.createPost()` throws METRICS_NOT_FINALIZED when callerEndedAt is null | `WalkPostCommandService` |
| `WalkPostCommandService.createPost()` throws SESSION_NOT_POSTABLE for CANCELLED session | `WalkPostCommandService` |
| `WalkPostCommandService.createPost()` throws SESSION_NOT_POSTABLE for FAILED session (only if FAILED exists in enum) | `WalkPostCommandService` |
| `WalkPostCommandService.createPost()` succeeds when caller COMPLETED and partner still ACTIVE | `WalkPostCommandService` |
| `WalkPostCommandService.createPost()` throws DUPLICATED when post exists | `WalkPostCommandService` |
| `WalkPostCommandService.deletePost()` then createPost succeeds (repost allowed after hard delete) | `WalkPostCommandService` |
| `WalkPostQueryService.getUserPosts()` returns only PUBLIC when viewerId is null (unauthenticated) | `WalkPostQueryService` |
| `WalkPostQueryService.getUserPosts()` returns only PUBLIC for stranger | `WalkPostQueryService` |
| `WalkPostQueryService.getUserPosts()` returns PUBLIC + FRIENDS for accepted friend | `WalkPostQueryService` |
| `WalkPostQueryService.getUserPosts()` returns empty when viewer is blocked by author | `WalkPostQueryService` |
| `WalkPostQueryService.getUserPosts()` returns empty when viewer has blocked author | `WalkPostQueryService` |
| `PostVisibility.from()` throws `WALK_POST_INVALID_VISIBILITY` for null input | `PostVisibility` (backend) |
| `PostVisibility.from()` throws `WALK_POST_INVALID_VISIBILITY` for unknown string | `PostVisibility` (backend) |
| `SessionHistoryQueryService.toSummary()` sets canPost=true when no post and caller metrics finalized | `SessionHistoryQueryService` |
| `SessionHistoryQueryService.toSummary()` sets canPost=false when caller metrics not yet finalized | `SessionHistoryQueryService` |
| `SessionHistoryQueryService.toSummary()` sets canPost=false when post already exists | `SessionHistoryQueryService` |

### Backend integration / API tests

| Scenario | Endpoint |
|---|---|
| POST with valid completed session → 200 + WalkPostResponse | `POST /api/v1/sessions/{id}/posts` |
| POST with CANCELLED session → 400 WALK_POST_SESSION_NOT_POSTABLE | `POST /api/v1/sessions/{id}/posts` |
| POST with FAILED session (if enum exists) → 400 WALK_POST_SESSION_NOT_POSTABLE | `POST /api/v1/sessions/{id}/posts` |
| POST with non-participant caller → 400 WALK_POST_AUTHOR_NOT_PARTICIPANT | `POST /api/v1/sessions/{id}/posts` |
| POST with callerEndedAt null → 400 WALK_POST_METRICS_NOT_FINALIZED | `POST /api/v1/sessions/{id}/posts` |
| POST duplicate → 400 WALK_POST_DUPLICATED | `POST /api/v1/sessions/{id}/posts` |
| POST after DELETE → 200 (repost allowed) | `POST /api/v1/sessions/{id}/posts` |
| POST with invalid visibility → 400 WALK_POST_INVALID_VISIBILITY | `POST /api/v1/sessions/{id}/posts` |
| GET my posts → all visibility levels | `GET /profiles/me/posts` |
| GET user posts unauthenticated → PUBLIC only, no block/friend checks | `GET /profiles/{userId}/posts` |
| GET user posts as stranger → PUBLIC only | `GET /profiles/{userId}/posts` |
| GET user posts as friend → PUBLIC + FRIENDS | `GET /profiles/{userId}/posts` |
| GET user posts when viewer blocked by author → empty | `GET /profiles/{userId}/posts` |
| GET user posts when viewer has blocked author → empty | `GET /profiles/{userId}/posts` |
| PATCH visibility as non-author → 400 WALK_POST_FORBIDDEN | `PATCH /walk-posts/{id}/visibility` |
| DELETE as author → 200 | `DELETE /walk-posts/{id}` |
| GET /sessions/history includes hasPosted, canPost fields | `GET /sessions/history` |

### Android ViewModel tests

| Test | ViewModel |
|---|---|
| `CreateWalkPostViewModel.submit()` posts loading then success state | `CreateWalkPostViewModel` |
| `CreateWalkPostViewModel.submit()` trims caption before length check | `CreateWalkPostViewModel` |
| `CreateWalkPostViewModel.submit()` with caption > 150 chars after trim posts error without network call | `CreateWalkPostViewModel` |
| `WalkActivityViewModel.selectFilter()` updates filteredPosts correctly | `WalkActivityViewModel` |
| `WalkActivityViewModel.deletePost()` posts updated list after success | `WalkActivityViewModel` |
| `PostVisibility.from()` returns PRIVATE for null | `PostVisibility` (frontend) |
| `PostVisibility.from()` returns PRIVATE for unknown string | `PostVisibility` (frontend) |

### Manual QA checklist

```
[ ] COMPLETED card (not reviewed, not posted): Leave a Review + Post to Profile + Report
[ ] COMPLETED card (reviewed, not posted): View Review + Post to Profile + Report
[ ] COMPLETED card (not reviewed, posted): Leave a Review + View Post + POSTED chip + Report
[ ] COMPLETED card (reviewed and posted): View Review + View Post + POSTED chip + Report
[ ] ACTIVE card (backend returns canPost=true): Post My Walk + Report visible
[ ] ACTIVE card (backend returns canPost=false): no Post button shown (no loading state)
[ ] ACTIVE card (both still walking / canPost=false): no action buttons
[ ] CANCELLED card: Report only (no Post, no View Details, no POSTED chip)
[ ] FAILED card (if FAILED enum exists): Report only (no Post, no View Details)
[ ] "Post to Profile" opens Create Walk Post with correct sessionId and pre-filled stats
[ ] "Post My Walk" opens Create Walk Post with myWalkOnly=true
[ ] Caption input respects 150 char limit
[ ] Visibility selector cycles Public / Friends / Only me
[ ] Show companion toggle hides/shows companion chip in preview
[ ] Show route map toggle shows local placeholder drawable (not a URL-loaded image)
[ ] "Post to Profile" button triggers loading state
[ ] Successful post navigates back; History card shows "View Post" + POSTED chip
[ ] "View Post" opens Walk Activity screen at the top (no scroll-to-post in MVP)
[ ] Walk Activity shows filter chips: All / Public / Friends / Only me
[ ] Walk Activity filter correctly hides/shows posts by visibility
[ ] Walk Activity empty state shows "Go to History" button
[ ] Overflow menu shows: Change Visibility / Delete
[ ] Change Visibility updates chip immediately
[ ] Delete post removes card from list
[ ] After deleting a post, History card shows "Post to Profile" again (canPost=true)
[ ] Public Profile "Recent Walks" visible when profile has public posts
[ ] Public Profile "Recent Walks" empty when all posts are PRIVATE
[ ] Friend viewing profile sees PUBLIC + FRIENDS posts
[ ] Stranger viewing profile sees PUBLIC posts only
[ ] Blocked user (either direction) sees empty Recent Walks
[ ] Invalid visibility returned in response → frontend shows post with PRIVATE chip (no crash, warning logged)
```

### Regression checklist

```
[ ] Walk Complete screen has no Share / Review / Post section
[ ] Walk History "Leave a Review" still works for COMPLETED sessions
[ ] Walk History "Report" still works for COMPLETED sessions
[ ] Walk History card click still opens RouteReplayActivity
[ ] Profile Walk History menu still navigates to SessionHistoryFragment
[ ] Profile My Badges menu still navigates to BadgeFragment
[ ] Profile Friends menu still navigates to FriendsFragment
[ ] Profile Security menu still navigates to SecurityFragment
[ ] Admin Dashboard card still shows for admin users
[ ] Public Profile friendship actions still work (Add, Accept, Decline, Remove)
[ ] Public Profile Block User overflow still works
[ ] Review submission (SubmitReviewFragment) still works end-to-end
[ ] Report submission (ReportIncidentFragment) still works end-to-end
```

---

## 9. Step-by-step Implementation Order

| Step | Work | Risk |
|---|---|---|
| **1** | DB migration: create `walk_post` table with constraints and composite indexes | Low |
| **2** | Backend domain: `WalkPost`, `PostVisibility`, `WalkPostErrorCode`, `WalkPostRepository` interface | Low |
| **3** | Backend domain: add `canUserPost(String userId)` to `WalkSession` or create `WalkPostPolicy` | Medium |
| **4** | Backend infra: `WalkPostJdbcRepository` — save, findById, exists, findByAuthor, findVisible, findExistenceMap, delete | Medium |
| **5** | Backend app: `WalkPostCommandService` — createPost, updateVisibility, deletePost (services return domain objects) | Medium |
| **6** | Backend app: `WalkPostQueryService` — getMyPosts, getUserPosts with blocked-either-direction rule | Medium |
| **7** | Backend presentation: `WalkPostController`, `ProfilePostController`, DTOs, mapper (controller maps domain → DTO) | Low |
| **8** | Backend: extend `SessionHistoryQueryService.toSummary()` with new fields. Verify actual per-user finalized-metrics field name in `WalkSession.java` before coding the `canPost` derivation. Add batch existence query. | Medium |
| **9** | Backend: update `SessionSummaryResponse` record with new fields | Low |
| **10** | Frontend: update `SessionSummaryResponse` DTO, `SessionSummary` domain model, `SessionSummaryMapper` | Low |
| **11** | Frontend data layer: `WalkPostResponse`, request DTOs, `WalkPostApiService`, `WalkPostMapper`, `WalkPostRepositoryImpl` | Low |
| **12** | Frontend domain: `WalkPost`, `WalkPostRepository` interface, `PostVisibility` (PRIVATE fallback) | Low |
| **13** | Frontend `WalkMateApplication`: register `WalkPostRepository` singleton | Low |
| **14** | Custom views: `WalkResultPostCard` (owner + viewer variants), `VisibilityChipView`, `VisibilitySelectorView` | Medium |
| **15** | New layout files: `view_walk_result_post_card.xml`, `view_visibility_chip.xml`, `view_visibility_selector.xml` | Low |
| **16** | `CreateWalkPostFragment` + VM + UiState + Factory + layout (with static placeholder for route map) | Medium |
| **17** | Update `item_session_history.xml`: add btnPost, btnViewPost, btnViewReview, txtPostedChip | Medium |
| **18** | Update `SessionHistoryAdapter`: new listeners (with stats args), revised `onBindViewHolder` for all state variants | High |
| **19** | Update `SessionHistoryFragment`: wire new listeners, add nav actions with all Bundle args | Low |
| **20** | `WalkActivityFragment` + VM + UiState + Factory + layout | Medium |
| **21** | Update `fragment_profile.xml`: add Walk Activity menu row | Low |
| **22** | Update `ProfileFragment` + `ProfileViewModel`: wire Walk Activity navigation | Low |
| **23** | Update `fragment_public_profile.xml`: add Recent Walks section | Medium |
| **24** | Update `PublicProfileFragment` + `PublicProfileViewModel`: load and render user posts | Medium |
| **25** | Update nav graph (`nav_graph.xml`): add all new destinations and actions | Low |
| **26** | QA regression pass: Review, Report, Walk Complete, Profile, Public Profile | High |

---

## 10. Non-goals for MVP

- Like / dislike on posts
- Comment system on posts
- App-wide walk activity feed
- Share to external apps (Android share intent)
- Upload custom images for a post
- AI-generated captions or suggestions
- Complex achievement / reward system tied to posting
- Report system for posts (reporting a post to admins)
- Edit caption after posting
- Notification when someone else views a post
- Route map preview image generation (static placeholder only; real generation deferred)
- Points-per-session tracking (default `points_earned = 0`, column kept for future)
- Chat button on History cards (deferred until canChat model is defined)
- "View Details" on CANCELLED cards (deferred until `RouteReplayActivity` safety for sessions with no GPS data is confirmed)
- `currentUserReviewId` in History response (use existing `ReviewSnapshot` instead)
- Pagination on Walk Activity or Recent Walks
- "See all" deep-link from Public Profile Recent Walks (max 5 shown, no "See all" in MVP)
- Deep-link / scroll-to / highlight a specific post from "View Post" (Walk Activity opens at the top)
