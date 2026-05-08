# Gap Analysis — Walk Result Post Feature

WalkMate · Android Native (Java) + Spring Boot

---

## Reconciliation Summary

Final decisions applied in this document:

| Decision | Value |
|---|---|
| Primary trigger for post actions | Walk History Item Card |
| Endpoint naming | `POST /api/v1/sessions/{sessionId}/posts` (matches existing session API prefix) |
| Delete policy | Hard delete. After deletion, user may post the same session again. |
| Points | `points_earned` column stays in `walk_post` with default 0. Not displayed in MVP UI. |
| Route preview | `route_preview_url` nullable. Show local/static placeholder when `showRouteMap=true`. Real image generation is deferred. |
| Chat / `canChat` | Deferred from MVP entirely. Existing chat behavior unchanged. |
| Review ID | `currentUserReviewId` is **not added in MVP**. Use existing `ReviewSnapshot` to render "View Review". If `ReviewSnapshot` is insufficient, show "Reviewed" state only and defer detailed view. |
| Cancelled card actions | For MVP, CANCELLED cards show **Report only**. "View Details" is deferred until `RouteReplayActivity` is proven safe for sessions with no GPS data. |
| Public Profile Recent Walks | Shows at most **5 posts**. No "See all" in MVP. |
| Post My Walk visibility | Hidden until backend returns `canPost=true`. No frontend loading/waiting state. |
| FAILED in canPost | Only exclude CANCELLED by name. FAILED is excluded only if the `WalkSession` status enum contains a `FAILED` value; otherwise NO_SHOW self-enforces via `callerPersonalStatus != COMPLETED`. |
| Visibility — request | Backend `PostVisibility.from()` **throws** `WALK_POST_INVALID_VISIBILITY` for invalid/null input. Invalid request is rejected (400). |
| Visibility — response | Frontend `PostVisibility.from()` maps unknown/null response visibility to `PRIVATE` and logs a warning. Never expands to `PUBLIC`. |
| Frontend domain repo boundary | `domain/walkpost/WalkPostRepository` (frontend) must not accept `CreateWalkPostRequest` DTO. `createPost()` takes domain-typed params. `WalkPostRepositoryImpl` constructs the DTO internally. |
| Unauthenticated viewer on `GET /profiles/{userId}/posts` | If viewerId is null, return PUBLIC only. Skip friendship and block checks entirely. |
| "View Post" navigation | Opens `WalkActivityFragment` at the top. Scroll-to / highlight specific post is deferred. |
| Caption normalization | Trim first, then check length (max 150). Blank after trim → store null or empty per existing project convention. |
| Hard delete | Confirmed. After deletion, user may post the same session again. |
| Visibility enforcement | Backend enforces visibility filtering. Frontend does not re-filter. |
| `canPost` final rule | `callerPersonalStatus == COMPLETED && !hasPosted && caller is participant && caller metrics are finalized (per-user ended-at/finalized field non-null — verify actual field name) && sessionStatus != CANCELLED [&& != FAILED if that enum value exists]` |
| ACTIVE + caller COMPLETED | "Post My Walk" is hidden until backend returns `canPost=true`. No frontend loading state. |
| Blocked visibility | Blocked either direction → empty post list |
| Duplicate error code | `WALK_POST_DUPLICATED` maps to 400 via `GlobalExceptionHandler`. Frontend reloads History on this error. |
| Application layer | Command/query services return domain objects. Controller maps to response DTO. |
| Session postability | Validated by `WalkSession` domain method or `WalkPostPolicy`, not by `WalkPost` itself. |

---

## 1. Source Review Summary

### Documents inspected

| Document | Path |
|---|---|
| Frontend architecture | `docs/single-source-of-truth/architecture/Frontend_VI.md` |
| Backend architecture | `docs/single-source-of-truth/architecture/Backend_VI.md` |
| DB schema | `docs/db/current-db.sql` |
| MVP spec | `docs/enhance/share/walk-result-post-mvp-spec.md` |
| Figma — History screen | `docs/enhance/share/figma/new_history_screen/react_code/WalkHistoryScreen.tsx` |
| Figma — Walk Activity screen | `docs/enhance/share/figma/new_walk_activity_screen/react_code/MyWalkActivityScreen.tsx` |

### Codebase areas inspected

| Area | Path found |
|---|---|
| History Fragment | `frontend/.../ui/history/SessionHistoryFragment.java` |
| History Adapter | `frontend/.../ui/history/SessionHistoryAdapter.java` |
| History UiState | `frontend/.../ui/history/SessionHistoryUiState.java` |
| History ViewModel | `frontend/.../ui/history/SessionHistoryViewModel.java` |
| Domain — SessionSummary | `frontend/.../domain/walksession/SessionSummary.java` |
| Domain — ParticipantSummary | `frontend/.../domain/walksession/ParticipantSummary.java` |
| Domain — WalkSessionRepository | `frontend/.../domain/walksession/WalkSessionRepository.java` |
| DTO — SessionSummaryResponse (frontend) | `frontend/.../dto/response/session/SessionSummaryResponse.java` |
| Mapper — SessionSummaryMapper | `frontend/.../mapper/SessionSummaryMapper.java` |
| Profile Fragment | `frontend/.../ui/profile/ProfileFragment.java` |
| Public Profile Fragment | `frontend/.../ui/profile/publicprofile/PublicProfileFragment.java` |
| WalkMateApplication (Service Locator) | `frontend/.../WalkMateApplication.java` |
| Backend — WalkSession domain | `backend/.../domain/session/WalkSession.java` |
| Backend — SessionHistoryQueryService | `backend/.../application/session/SessionHistoryQueryService.java` |
| Backend — SessionHistoryController | `backend/.../presentation/controller/session/SessionHistoryController.java` |
| Backend — SessionSummaryResponse (backend) | `backend/.../presentation/dto/response/session/SessionSummaryResponse.java` |
| Backend — SocialRepository | `backend/.../domain/social/SocialRepository.java` |
| Backend — FriendQueryService | `backend/.../application/social/FriendQueryService.java` |
| Backend — GamificationCommandService | `backend/.../application/gamification/GamificationCommandService.java` |

### Paths that did not exist at expected location

None. All expected paths resolved correctly.

---

## 2. Current History UI Behavior

### Overall structure

`SessionHistoryFragment` inflates `fragment_session_history.xml` and delegates list rendering to `SessionHistoryAdapter` (a `ListAdapter<SessionSummary>`). The ViewModel fetches from `WalkSessionRepository.getSessionHistory()`, which maps to `GET /api/v1/sessions/history`.

### What each card currently shows

Each `item_session_history` layout contains:

- Date (`txtSessionDate`)
- Status badge (`txtSessionStatus`)
- Hotspot name (`txtHotspotName`)
- Partner row: avatar, name, per-user status, distance, duration
- Self row: avatar ("You"), per-user status, distance, duration
- Divider (`dividerAction`) — conditional
- `btnReview` — `MaterialButton`, conditional
- `btnReport` — `MaterialButton`, conditional

### Current action button logic (from `SessionHistoryAdapter.onBindViewHolder`)

All buttons start GONE. Then:

```
if (global == COMPLETED):
    caller = getCallerParticipant(currentUserId)
    partner = getPartnerParticipant(currentUserId)

    if callerStatus != NO_SHOW:
        show dividerAction
        if partnerStatus == COMPLETED:
            show btnReview → navigate to SubmitReviewFragment
        show btnReport → navigate to ReportIncidentFragment
    // callerStatus == NO_SHOW → both stay GONE

if (global == ACTIVE || PENDING || CANCELLED):
    both buttons stay GONE
```

### Current state handling

| Global session status | Caller personal status | Partner personal status | Current UI |
|---|---|---|---|
| ACTIVE | PENDING / ACTIVE | any | Nothing shown |
| ACTIVE | COMPLETED | PENDING / ACTIVE (partner still walking) | Nothing shown — **gap** |
| COMPLETED | COMPLETED | COMPLETED | Review + Report shown |
| COMPLETED | COMPLETED | NO_SHOW | Report only (no Review) |
| COMPLETED | NO_SHOW | any | Nothing shown |
| CANCELLED | any | any | Nothing shown — **gap** |

### Current data available per card

The `SessionSummary` domain model carries:
- `sessionId`, `status`, `scheduledStart`, `terminalAtMs`, `hotspotName`
- `isReviewed` (boolean), `isReported` (boolean)
- `ReviewSnapshot` (stars, comment, tagIds) — if already reviewed
- `ReportSnapshot` (reason, evidenceUrl) — if already reported
- `List<ParticipantSummary>` — each with: `participantId`, `fullName`, `avatarUrl`, `distanceKm`, `durationMinutes`, `userStatus`

### What the current UI would NOT do without changes

- Does not show "Post to Profile" on any card.
- Does not show "Post My Walk" when global is ACTIVE but caller has completed.
- Does not show "View Post" or "POSTED" chip.
- Does not show "View Details" for CANCELLED sessions.
- Does not show "View Review" when already reviewed.
- Does not carry `hasPosted`, `postId` in the model.

---

## 3. Figma Design vs. Current Native Android UI

### What can be reused (concept only — not code)

| Figma concept | Android equivalent |
|---|---|
| Status badge (pill with background + label) | Already implemented via `applyStatusBadge()` in `ViewHolder.bind()` |
| Participant row (avatar + name + status + km + min) | Already implemented — `txtParticipant2*` and `txtParticipant1*` views |
| Action divider | `dividerAction` in `item_session_history` layout |
| Card background, rounded corners | XML `CardView` or shape drawable already used |
| Partner avatar with initials fallback | `AvatarInitialView` custom view already exists |

### What is visual inspiration only

| Figma element | Why it cannot be copied directly |
|---|---|
| React `useState` for `reviewed` / `posted` per-session | Android state lives in `LiveData<UiState>`, not component-local state |
| CSS-in-JS button style objects (`primaryBtnStyle`, `outlineBtnStyle`, etc.) | Android buttons use XML drawables / MaterialButton styles |
| `useNavigate('/walk-post/create', { state: ... })` | Android uses `NavController.navigate(R.id.action_..., Bundle args)` |
| React `<BottomNav />` component | Android uses existing `BottomNavigationView` wired to `MainActivity` |
| Inline JSX conditional rendering `{isCompleted && <div>...}` | Android uses `view.setVisibility(View.VISIBLE/GONE)` in `ViewHolder.bind()` |
| `PostedChip()` React component | Must be `TextView` or custom `Chip` with a purple pill drawable |
| React filter buttons on `MyWalkActivityScreen` | Android uses `ChipGroup` with single-selection or `MaterialButton` toggle |
| `VisibilityBottomSheet` React component | Android uses `BottomSheetDialogFragment` |
| `WalkResultPostCard` React component | Must be a new `WalkResultPostCard` Custom View per section 8 of Frontend_VI.md |

### What must NOT be copied from React/TSX

- React hook patterns (`useState`, `useEffect`, `useNavigate`) — no equivalent in Java Android.
- React component structure — Android uses Fragment + ViewModel + UiState, not component trees.
- Inline style objects — these produce no XML and have no equivalent in the MVVM pattern.
- `navigate('/walk-activity')` as a View Post destination — Android needs a defined nav action and session args.
- Session mock data arrays (`SESSIONS`, `INITIAL_POSTS`) — Android loads real data from backend via Repository.

### Style / component mismatches to note

| Figma | Android gap |
|---|---|
| Orange gradient pill button (`primaryBtnStyle`) | Reuse `WalkMateButton` with FILLED style, or add `bg_button_orange_gradient.xml` drawable |
| Purple "POSTED" chip (`F5F3FF` bg / `DDD6FE` border / `7C3AED` text) | No existing purple chip drawable — new `bg_chip_posted.xml` needed |
| "View Review" green-tinted button (`#F0FDF4` bg / `#86EFAC` border) | No existing green outline button — new style or `WalkMateButton` variant needed |
| Ghost report link (no border, `#A8A29E` text) | New ghost button style or a plain `TextView` with click listener |

---

## 4. MVP Spec Validity Check

| MVP Proposal | Status | Reason |
|---|---|---|
| Walk Complete is not the main trigger for Post | **Valid as-is** | Walk Complete screen must keep Back to Home as primary CTA. Optional "View in History" is acceptable. It must not add Review, Post, or Report. No structural changes required — only confirm absence of these elements. |
| Walk History Item Card is the main trigger | **Valid with modification** | The adapter must be extended with Post / Post My Walk / View Post buttons. Existing Review / Report logic stays. |
| Review, Report, Post are independent | **Valid as-is** | No coupling introduced in the spec. Backend validation checks only caller personal status. |
| User may Post My Walk when global is ACTIVE but callerPersonalStatus = COMPLETED | **Valid with modification** | The per-user statuses are available in `WalkSession`. However, caller metrics (`distanceKm`, `durationSeconds`) must be finalized before allowing post. When global is ACTIVE, the caller's walk has ended but backend must confirm stats are written. If not guaranteed, "Post My Walk" must be gated. |
| Cancelled / No-show sessions cannot be posted | **Valid as-is** | `canPost` rule excludes CANCELLED (and FAILED if that enum exists). Caller NO_SHOW means personal status != COMPLETED, so rule is self-enforcing. |
| One post per session per author | **Valid as-is** | `UNIQUE (session_id, author_id)` constraint covers this at DB level. |
| `walk_post` table: new table with snapshot columns | **Valid as-is** | Distance and duration exist per-user on `walk_session`. Hotspot via JOIN. Companion name via JOIN. |
| `points_earned` column in `walk_post` | **Valid with modification (deferred display)** | Keep column with `DEFAULT 0`. Do not display points in MVP UI. Do not invent a formula. Points display is deferred until per-session gamification storage is added. |
| `route_preview_url` column | **Valid with modification** | Column stays, defaults to `null`. For MVP, `showRouteMap=true` shows a local static placeholder drawable. Real route image generation is deferred and not a dependency. |
| History API returns `hasPosted`, `postId`, `canPost`, `canReview`, `canReport` | **Valid with modification** | `canChat` removed from MVP scope. `currentUserReviewId` is optional — existing `ReviewSnapshot` is sufficient to render "View Review". |
| My Profile links to Walk Activity (separate screen) | **Valid as-is** | `ProfileFragment` has a menu row pattern. Adding `menuWalkActivity` follows the same pattern. |
| Public Profile shows Recent Walks | **Valid with modification** | `PublicProfileFragment` has no such section. Backend filters by viewer relationship. Frontend renders only what backend returns. |
| Three visibility values: PUBLIC / FRIENDS / PRIVATE | **Valid as-is** | DB check constraint consistent. `SocialRepository.areAcceptedFriends()` and `isBlocked()` available on backend. |
| Hard delete for MVP | **Valid as-is — CHOSEN** | After deletion, user may create a new post for the same session. This is intentional and acceptable behavior for MVP. |

---

## 5. Backend / Data Gaps

### 5.1 Missing DB table

The `walk_post` table does not exist. This is the primary DB gap. Full DDL is in section 2 of the implementation plan.

### 5.2 Walk session data sufficiency for snapshotting

| Field needed in `walk_post` | Source in DB | Status |
|---|---|---|
| `distance_km` per caller | `walk_session.user_a_distance_km` / `user_b_distance_km` | Available |
| `duration_seconds` per caller | `walk_session.user_a_duration_seconds` / `user_b_duration_seconds` | Available |
| `hotspot_name` | `hotspot.name` via `walk_session.hotspot_id` | Available via JOIN |
| Companion name | `user_profile.full_name` of the other participant | Available via JOIN |
| `route_preview_url` | Not pre-generated — no image generation pipeline | **Gap — default NULL, placeholder in UI** |
| `points_earned` per session | Not stored per-session; `user_account.total_points` is cumulative | **Gap — default 0, not displayed in MVP UI** |

### 5.2.1 Caption normalization

The `caption` field in `walk_post` is optional text (max 150 chars). The domain entity `WalkPost.create()` must normalize it consistently:

1. **Trim first**: remove leading/trailing whitespace before any validation.
2. **Check length**: reject if trimmed length > 150 (throw `WALK_POST_CAPTION_TOO_LONG`).
3. **Blank after trim**: store as `null` or `""` following the existing project convention for optional text fields. Check other domain entities in the codebase for precedent and use the same pattern.

Frontend `CreateWalkPostViewModel.submit()` applies the same trim-then-length-check before calling the repository.

### 5.3 Determining caller personal status and finalized metrics

The `WalkSession` domain object has `userAStatus` and `userBStatus`. The `SessionHistoryQueryService.toSummary()` already resolves these into `ParticipantSummaryResponse.userStatus`.

**Finalized-metrics for ACTIVE + caller COMPLETED**: When `global == ACTIVE` and `callerStatus == COMPLETED`, the caller's walk has ended. Metrics (`distanceKm`, `durationSeconds`) are written when `WalkSession.complete()` commits. To confirm finalization, the backend checks the actual per-user ended-at or finalized-metrics field on `WalkSession` before allowing post creation. If that field does not exist by name (`userAEndedAt` / `userBEndedAt`), the implementer must verify the actual field name in `WalkSession.java` and use it, or derive finalization from another domain invariant. The `canPost` derivation in `SessionHistoryQueryService` must include this guard — it drives `canPost=true` only when the caller's metrics are confirmed committed.

### 5.4 Missing fields in current History API response

The backend `SessionSummaryResponse` currently contains:

```
sessionId, status, scheduledStart, endedAt, isReviewed, isReported,
reviewSnapshot, reportSnapshot, meetingPointLat, meetingPointLng,
participants (with userStatus), callerAvatarUrl, hotspotName
```

Missing for the new feature:

| Field | Where to derive | MVP required? |
|---|---|---|
| `currentUserPersonalStatus` | Caller's participant `userStatus` | Yes |
| `partnerPersonalStatus` | Partner's participant `userStatus` | Yes |
| `currentUserHasPosted` | `walkPostRepository.existsBySessionAndAuthor()` | Yes |
| `currentUserPostId` | `walkPostRepository.findPostIdBySessionAndAuthor()` | Yes (for "View Post" navigation) |
| `currentUserReviewId` | `reviewRepository.findReviewIdBySessionAndReviewer()` | **No — not in MVP.** Use existing `ReviewSnapshot`. If `ReviewSnapshot` is insufficient to navigate to the review, show a "Reviewed" indicator only and defer. |
| `canPost` | Derived: see final rule below | Yes |
| `canReview` | Derived: `!isReviewed && callerStatus != NO_SHOW && partnerStatus == COMPLETED` | Yes |
| `canReport` | Derived: `!isReported && callerStatus != NO_SHOW` | Yes |
| `canChat` | **Deferred** — not in MVP | No |

**Final `canPost` rule:**

```
callerPersonalStatus == COMPLETED
&& !hasPosted
&& caller is participant of the session
&& caller metrics are finalized (callerEndedAt is non-null — use actual per-user ended-at field;
   if that field does not exist, derive finalization from domain or add the field)
&& sessionStatus != CANCELLED
   [&& sessionStatus != FAILED — only if FAILED exists in the WalkSession status enum;
    otherwise NO_SHOW self-enforces via callerPersonalStatus != COMPLETED]
```

**Recommendation**: Add batch query for `hasPosted` across all history sessions to avoid N+1 in `SessionHistoryQueryService`. Add `walkPostRepository.findExistenceMapBySessionIdsAndAuthor(Set<String> sessionIds, String authorId)`.

### 5.5 Friendship and block data for visibility filtering

`SocialRepository` has:
- `areAcceptedFriends(UUID userId1, UUID userId2)` — per-request friendship check
- `isBlocked(UUID blockerId, UUID blockedId)` — per-request block check
- `getBlockedAndBlockerIds(UUID userId)` — bulk block check

**Final visibility rule**:
| Viewer | PUBLIC | FRIENDS | PRIVATE |
|---|---|---|---|
| Unauthenticated (null viewerId) | Show | Hide | Hide |
| Owner (self) | Show | Show | Show |
| Accepted friend | Show | Show | Hide |
| Stranger | Show | Hide | Hide |
| Blocked either direction | Hide | Hide | Hide |

The block check must cover both directions: `isBlocked(authorId, viewerId) || isBlocked(viewerId, authorId)`. If either party blocked the other, return empty list regardless of visibility settings.

**Unauthenticated callers** (null viewerId): return PUBLIC posts only. Do not call friendship or block checks with a null viewerId — those methods require valid UUIDs and would throw or produce undefined behavior.

### 5.6 Backend missing classes

| Layer | Missing class |
|---|---|
| `domain/walkpost/` | `WalkPost.java` |
| `domain/walkpost/` | `WalkPostRepository.java` |
| `domain/walkpost/` | `WalkPostErrorCode.java` |
| `domain/walkpost/` | `PostVisibility.java` (enum) |
| `domain/walkpost/` | `WalkPostPolicy.java` (session postability validation — see implementation plan) |
| `application/walkpost/` | `WalkPostCommandService.java` |
| `application/walkpost/` | `WalkPostQueryService.java` |
| `application/walkpost/` | `CreateWalkPostCommand.java` (record) |
| `application/walkpost/` | `UpdateWalkPostVisibilityCommand.java` (record) |
| `infrastructure/repository/walkpost/` | `WalkPostJdbcRepository.java` |
| `presentation/controller/walkpost/` | `WalkPostController.java` |
| `presentation/controller/profile/` | `ProfilePostController.java` (or added to existing profile controller) |
| `presentation/dto/request/walkpost/` | `CreateWalkPostRequest.java` |
| `presentation/dto/request/walkpost/` | `UpdateWalkPostVisibilityRequest.java` |
| `presentation/dto/response/walkpost/` | `WalkPostResponse.java` |
| `presentation/mapper/` | `WalkPostMapper.java` |
| DB migration | `V{next}__create_walk_post_table.sql` |

---

## 6. Frontend Gaps

### 6.1 Missing Android screens and classes

| Class | Package |
|---|---|
| `CreateWalkPostFragment.java` | `ui.walkpost.create` |
| `CreateWalkPostViewModel.java` | `ui.walkpost.create` |
| `CreateWalkPostUiState.java` | `ui.walkpost.create` |
| `CreateWalkPostViewModelFactory.java` | `ui.walkpost.create` |
| `WalkActivityFragment.java` | `ui.profile.activity` |
| `WalkActivityViewModel.java` | `ui.profile.activity` |
| `WalkActivityUiState.java` | `ui.profile.activity` |
| `WalkActivityViewModelFactory.java` | `ui.profile.activity` |
| `WalkPost.java` | `domain.walkpost` |
| `WalkPostRepository.java` (interface) | `domain.walkpost` |
| `PostVisibility.java` (enum) | `domain.walkpost` |
| `CreateWalkPostRequest.java` | `data.datasource.remote.dto.request.walkpost` |
| `UpdateWalkPostVisibilityRequest.java` | `data.datasource.remote.dto.request.walkpost` |
| `WalkPostResponse.java` | `data.datasource.remote.dto.response.walkpost` |
| `WalkPostMapper.java` | `data.mapper` |
| `WalkPostRepositoryImpl.java` | `data.repository` |

### 6.2 Missing API interface methods

A new `WalkPostApiService.java` (Retrofit interface) under `data.datasource.remote.api`. Endpoints match the existing session API prefix:

```java
@POST("sessions/{sessionId}/posts")
Call<ApiResponse<WalkPostResponse>> createPost(@Path("sessionId") String sessionId, @Body CreateWalkPostRequest body);

@GET("profiles/me/posts")
Call<ApiResponse<List<WalkPostResponse>>> getMyPosts();

@GET("profiles/{userId}/posts")
Call<ApiResponse<List<WalkPostResponse>>> getUserPosts(@Path("userId") String userId);

@PATCH("walk-posts/{postId}/visibility")
Call<ApiResponse<WalkPostResponse>> updateVisibility(@Path("postId") String postId, @Body UpdateWalkPostVisibilityRequest body);

@DELETE("walk-posts/{postId}")
Call<ApiResponse<Void>> deletePost(@Path("postId") String postId);
```

Note: Base URL already includes `/api/v1/`. These relative paths append to that base.

`WalkPostApiService.createPost()` takes `CreateWalkPostRequest` as a `@Body` — this is the Retrofit API layer and the DTO is appropriate here. However, **`domain/walkpost/WalkPostRepository` (the domain interface) must not reference `CreateWalkPostRequest`**. The `WalkPostRepositoryImpl` receives domain-typed params from `createPost()`, constructs `CreateWalkPostRequest` internally, and calls `WalkPostApiService`. The DTO never escapes `WalkPostRepositoryImpl`.

### 6.3 Missing changes to SessionSummary and its DTO

`SessionSummary.java` must add:
- `boolean hasPosted`
- `String postId` (nullable)
- `boolean canPost`
- `boolean canReview`
- `boolean canReport`

`currentUserReviewId` is **not added in MVP**. The existing `ReviewSnapshot` (stars, comment, tagIds) already provides enough data to render "View Review". If the fragment needs more than `ReviewSnapshot` provides, show a "Reviewed" indicator only and defer the full detail view to a follow-up. Do not add a new `reviewRepository` query method for this in MVP.

`SessionSummaryResponse.java` (frontend DTO) must add matching `@SerializedName` fields.

`SessionSummaryMapper.java` must map the new fields.

### 6.4 Missing changes to History Adapter

`SessionHistoryAdapter` needs:

- New `OnPostClickListener` — `void onPostClick(String sessionId, String hotspotName, double distanceKm, long durationSeconds, String partnerName, boolean myWalkOnly)`
- New `OnViewPostClickListener` — `void onViewPostClick(String postId)` — navigates to `WalkActivityFragment`. The screen opens at the top in MVP; scrolling to or highlighting the specific post by `postId` is deferred to a follow-up.
- New `OnViewReviewClickListener` — `void onViewReviewClick(String sessionId)` — uses `sessionId` and `ReviewSnapshot` data only; no `reviewId` in MVP
- New `btnPost`, `btnViewPost`, `btnViewReview`, `txtPostedChip` views in `item_session_history.xml`
- Revised `onBindViewHolder` logic using the new model fields

**Chat-related items are not added**. `canChat`, `btnChat`, and `OnChatClickListener` are deferred from MVP.

### 6.5 Missing changes to Profile screen

`ProfileFragment.java` must add:
- `menuWalkActivity` view binding (new row in `fragment_profile.xml`)
- `viewModel.onWalkActivityClicked()` click listener
- `viewModel.getNavigateToWalkActivityEvent()` observer
- Nav action `action_profile_to_walkActivityFragment` in `nav_graph.xml`

`ProfileViewModel.java` must add:
- `onWalkActivityClicked()` method
- `MutableLiveData<Boolean> navigateToWalkActivity`
- `getNavigateToWalkActivityEvent()`
- `consumeNavigateToWalkActivity()`

### 6.6 Missing changes to Public Profile screen

`PublicProfileFragment.java` needs a "Recent Walks" section:
- `LinearLayout layoutRecentWalks` (not RecyclerView — avoids nested scroll issues)
- `TextView txtNoRecentWalks`
- At most 5 inflated `item_walk_post_preview.xml` views
- Load from `WalkPostRepository.getUserPosts(userId)` in `PublicProfileViewModel`
- Frontend renders only what backend returns (backend enforces visibility)

### 6.7 Missing Custom Views

Per section 8 of `Frontend_VI.md`:

| Custom View class | File | Purpose |
|---|---|---|
| `WalkResultPostCard` | `core/designsystem/view/WalkResultPostCard.java` | Full post card for Walk Activity and Public Profile; supports `owner` and `viewer` variants |
| `VisibilityChipView` | `core/designsystem/view/VisibilityChipView.java` | Single-chip display: Public / Friends / Only me |
| `VisibilitySelectorView` | `core/designsystem/view/VisibilitySelectorView.java` | Three-option radio-style selector for Create Post screen |

`RoutePreviewView` is deferred. For MVP, use a static placeholder drawable when `showRouteMap=true`.

### 6.8 Missing WalkMateApplication wiring

`WalkMateApplication` does not yet have:
- `private WalkPostRepository walkPostRepository;`
- `public WalkPostRepository getWalkPostRepository()`
- Instantiation of `WalkPostRepositoryImpl`

### 6.9 Navigation gaps

The nav graph is missing:
- `action_sessionHistory_to_createWalkPostFragment` — from History card "Post to Profile" / "Post My Walk"
- `action_createWalkPost_to_sessionHistoryFragment` — back after successful post
- `action_profile_to_walkActivityFragment` — Walk Activity from Profile menu
- `action_walkActivity_to_sessionHistoryFragment` — "Go to History" from empty state

---

## 7. Behavior Change Analysis

### Walk Complete screen

| Behavior | Change? |
|---|---|
| Shows session summary (duration, distance, partner) | **Unchanged** |
| Primary CTA: Back to Home | **Unchanged** |
| Secondary link: View in History | **Unchanged if already exists; add if missing** |
| No Review / Report / Share / Post section | **Confirmed absent — no changes needed to this screen** |

**Risk**: None. Walk Complete is not touched by this feature. Only a quick audit to confirm it has no stray Post/Review CTAs.

### Walk History screen

| Behavior | Change? |
|---|---|
| COMPLETED card shows Leave a Review | **Unchanged** |
| COMPLETED card shows Report | **Unchanged** |
| COMPLETED card — clicking card opens RouteReplayActivity | **Unchanged** |
| COMPLETED card, partner avatar click → PublicProfile | **Unchanged** |
| ACTIVE card shows nothing | **Intentional change: when backend returns `canPost=true` (callerStatus=COMPLETED + metrics finalized), show "Post My Walk" + "Report". No frontend loading state.** |
| CANCELLED card shows nothing | **Intentional change for MVP: show "Report" only. "View Details" is deferred until `RouteReplayActivity` is confirmed safe for sessions with no GPS data.** |
| New: COMPLETED card shows "Post to Profile" or "View Post" | **New behavior** |
| New: COMPLETED card shows "POSTED" chip | **New behavior** |
| New: COMPLETED card shows "View Review" when already reviewed | **New behavior — context-switches Leave a Review to View Review based on `isReviewed` flag** |

**Regression risk**: The review button logic fires on `callerStatus != NO_SHOW && partnerStatus == COMPLETED`. New buttons must be added in a dedicated `renderActionArea()` method to prevent accidentally hiding Review or Report.

### Review flow

| Behavior | Change? |
|---|---|
| SubmitReviewFragment accessed from History card | **Unchanged** |
| Backend `POST /reviews` | **Unchanged** |
| `isReviewed` flag returned in History response | **Unchanged** |
| "Leave a Review" becomes "View Review" after submit | **New behavior — driven by existing `isReviewed` + optional `viewMode` arg to SubmitReviewFragment** |

**Risk**: Low. Additive change. No existing code path removed.

### Report flow

| Behavior | Change? |
|---|---|
| ReportIncidentFragment accessed from History card | **Unchanged** |
| Report available on CANCELLED sessions | **New behavior — currently CANCELLED shows nothing. MVP adds Report only for CANCELLED cards.** |
| `isReported` flag in History response | **Unchanged** |

**Risk**: Low for existing COMPLETED flow. CANCELLED report availability must be tested — `ReportIncidentFragment` must handle sessions that have no `endedAt` timestamp.

### Chat flow

| Behavior | Change? |
|---|---|
| Existing chat behavior | **Unchanged** |
| `canChat` field in History response | **Not added in MVP** |
| "Chat" button on History card | **Not added in MVP** |

Chat is deferred. Its model (session-scoped vs. user-pair-scoped) must be inspected before `canChat` derivation is attempted.

### Profile screen

| Behavior | Change? |
|---|---|
| Existing menu items (Walk History, My Badges, Friends, Security) | **Unchanged** |
| Admin Dashboard card (role-based) | **Unchanged** |
| New menu item: Walk Activity | **New row added below existing menu items** |

**Risk**: Low. Adding a menu row does not affect existing menu items.

### Public Profile screen

| Behavior | Change? |
|---|---|
| Avatar, name, bio, tags, stats, badges, reviews | **Unchanged** |
| Friendship actions | **Unchanged** |
| Overflow menu (Block User) | **Unchanged** |
| New: Recent Walks section | **New section added below reviews, using LinearLayout (not RecyclerView)** |

**Risk**: Medium. Adding a new section risks scrolling performance. Use `LinearLayout` with at most 5 inflated item views.

### Session completion

| Behavior | Change? |
|---|---|
| `WalkSession.complete()` domain behavior | **Unchanged** |
| Gamification side-effects | **Unchanged** |
| Walk session saved to DB | **Unchanged** |
| Session appears in History list | **Unchanged** |

---

## 8. Risks and Open Questions

### Risk 1 — `points_earned` per session is not stored

**Problem**: `walk_session` has no per-session point delta. `user_account.total_points` is cumulative.

**Resolution (final)**: Keep `points_earned` column with `DEFAULT 0`. Set to 0 on post creation. Do not display points in the MVP post card UI. Do not invent a formula. Future migration can add `earned_points_a` / `earned_points_b` to `walk_session` and populate via `GamificationCommandService`, after which the Create Post endpoint can snapshot the value.

### Risk 2 — `route_preview_url` is not pre-generated

**Problem**: No image generation pipeline exists. `session_point_chunks` has polylines but no pre-rendered map image.

**Resolution (final)**: `route_preview_url` column stays, defaults to `null`. When `showRouteMap=true` and `routePreviewUrl` is null, the Android post card shows a local static placeholder drawable. No external map image pipeline is required for MVP.

### Risk 3 — History Adapter refactor regression

**Problem**: Adding multiple new conditional buttons increases `onBindViewHolder` complexity significantly.

**Mitigation**: Extract a private `renderActionArea(SessionSummary summary, ViewHolder holder)` method. This method handles all button visibility logic grouped by state variant (A through F). Write a unit test covering all 6 variants before modifying the adapter.

### Risk 4 — ACTIVE + caller COMPLETED: metrics finalization and UI hiding

**Problem**: When global is ACTIVE but callerStatus is COMPLETED, the caller's metrics may not yet be committed, and showing "Post My Walk" prematurely could snapshot zero or partial data.

**Resolution (final)**: The backend `WalkPostCommandService` must verify caller metrics are finalized (via the actual per-user ended-at or finalized-metrics field on `WalkSession`) before allowing post creation. This check gates `canPost=true` in the history response. The frontend simply hides the button when `canPost=false`. No frontend loading/waiting state is needed. The user can reload History when they are ready, and `canPost` will be true if metrics are finalized.

### Risk 5 — Duplicate post error handling convention

**Problem**: The spec suggests `409 Conflict` for duplicates. But the existing `GlobalExceptionHandler` maps `DomainException` to HTTP 400 by convention.

**Resolution (final)**: `WALK_POST_DUPLICATED` is thrown as `DomainException` and mapped to 400 by `GlobalExceptionHandler`, consistent with all other error codes. Frontend handles `WALK_POST_DUPLICATED` error by reloading History and updating the card state to "View Post".

### Risk 6 — Visibility handling: request vs. response

**Two distinct scenarios with different handling:**

**Request (frontend → backend)**: The backend `PostVisibility.from()` **throws** `WALK_POST_INVALID_VISIBILITY` for invalid or null input. This causes a 400 via `GlobalExceptionHandler`. The frontend should never send an invalid string — `VisibilitySelectorView` only exposes PUBLIC / FRIENDS / PRIVATE — but the backend rejects it regardless.

**Response (backend → frontend)**: The frontend `PostVisibility.from()` maps unknown or null response strings to `PRIVATE` and logs a warning. This handles cases like a new enum value added server-side before the app is updated. Never silently use `PUBLIC` as a fallback — unknown visibility must not widen access.

These two behaviors are intentionally different: backend rejects (throws), frontend degrades safely (PRIVATE fallback).

### Risk 7 — Public Profile performance with nested RecyclerView

**Problem**: `PublicProfileFragment` renders inside a `NestedScrollView`. Adding another `RecyclerView` for posts causes layout and scroll issues.

**Resolution (final)**: Use a `LinearLayout` inflating at most 5 `item_walk_post_preview.xml` views. No `RecyclerView` inside the profile scroll. "See all" link (if needed later) navigates to a separate screen.

### Risk 8 — No ACTIVE card actions exist today

**Problem**: The current adapter silently hides all actions for ACTIVE sessions. Adding "Post My Walk" for ACTIVE + caller COMPLETED means previously invisible cards will now show content.

**Mitigation**: The new condition `else if (global == ACTIVE && summary.canPost)` must be added as a parallel branch to the existing `if (global == COMPLETED)` block. Do not modify the existing COMPLETED block. The frontend relies entirely on the backend-returned `canPost` flag — it does not re-derive status or check `callerEndedAt` locally.

### Risk 9 — Application layer returning presentation DTO

**Problem**: If command/query services are written to return `WalkPostResponse`, this violates the Backend_VI.md constraint that DTOs must not leave the presentation layer.

**Resolution**: Command services return domain objects (`WalkPost`) or void. Query services return domain objects or application-level result objects. Controllers call mapper to convert to `WalkPostResponse`. This is consistent with how other services in the codebase work.

### Risk 10 — Hard delete and repost behavior

**Problem**: With hard delete, a user who deletes their post can immediately create a new post for the same session. This is different from "one post forever per session."

**Resolution (final)**: This is intentional and accepted for MVP. The `UNIQUE (session_id, author_id)` constraint is only enforced at the row level. After hard delete, the constraint no longer applies to that pair. If the product later decides to prevent reposting, a subsequent migration adds a `posted_at` audit table or switches to soft delete. MVP uses hard delete.

### Resolved decisions (no longer open)

All prior open questions are resolved:

1. **Review ID**: `currentUserReviewId` is not added in MVP. Use existing `ReviewSnapshot` to render "View Review". If `ReviewSnapshot` is insufficient, show "Reviewed" state only and defer the full detail view.
2. **CANCELLED "View Details"**: Deferred. For MVP, CANCELLED cards show **Report only**. "View Details" may be added in a follow-up once `RouteReplayActivity` safety for sessions with no GPS data is confirmed.
3. **Public Profile Recent Walks count**: Maximum **5 posts**. No "See all" in MVP.
4. **Post My Walk on ACTIVE cards**: Hidden until backend returns `canPost=true`. No frontend loading/waiting state. User reloads History to see updated state.
