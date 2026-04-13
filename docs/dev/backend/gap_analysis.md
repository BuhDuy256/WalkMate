# WalkMate Backend — Gap Analysis
**Date:** 2026-04-13
**Branch:** `merge/oauth`
**Reference Spec:** `docs/single-source-of-truth/use-cases/backend_use_cases.md` (updated 2026-04-13)
**Cross-References:** `docs/single-source-of-truth/lifecycle/invariants.md`, `state-transitions.md`
**Scope:** All 43 use cases (UC-01–UC-43). Auth, Discovery, Intent, Proposal, Session, GPS, Post-Session, Social, Notifications, Gamification.

---

## Legend

| Severity | Meaning |
|---|---|
| 🔴 CRITICAL | Breaks spec-mandated data flow; feature cannot function correctly without a fix. |
| 🟠 HIGH | Missing required behaviour or invariant; spec mandates it explicitly. |
| 🟡 MEDIUM | Wrong shape or non-spec endpoint; will cause client integration mismatches. |
| 🟢 LOW | Minor deviation; cleanup or alignment with spec wording. |

---

## Feature 1 — Auth (UC-01 through UC-12)

### 1.1 🔴 UC-01 Register returns full token payload instead of email-only confirmation
**File:** `presentation/controller/user/UserController.java:47–53`

`registerUser()` calls `userCommandService.registerUser()` which issues a `LoginResult` (containing access token + refresh token) and returns a full `LoginUserResponse`. The spec mandates:
- Response: `201 Created` with `{ "data": { "email": "..." } }` only.
- Client then navigates to **Login** screen separately. Auto-login on register is explicitly not the spec flow.

The `RegisterUserCommand` also carries a `deviceId` field that does not appear in the UC-01 request payload spec.

**Impact:** Client cannot implement the "Register → navigate to Login" flow; tokens are handed out prematurely. Security posture is also different (unverified account receives tokens immediately).

---

### 1.2 🟠 UC-10 / UC-11 Logout returns 204 No Content instead of 200 with null body
**File:** `presentation/controller/user/UserController.java:89–106`

Both `logout()` and `logoutAll()` return `ResponseEntity<Void>` via `ResponseEntity.noContent().build()` (HTTP 204). The spec says `200 OK`. The client clears local session on any non-error response so the status code difference is low risk, but it deviates from the documented contract and can cause client-side HTTP interceptors that only accept 200 to mishandle the response.

---

## Feature 2 — Discovery (UC-14)

### 2.1 🟠 UC-14 `HotspotQueryService.getAllHotspots()` throws on empty result instead of returning `[]`
**File:** `application/hotspot/HotspotQueryService.java:21–26`

```java
if (hotspots.isEmpty()) {
    throw new DomainException(HotspotErrorCode.NO_HOTSPOT_AVAILABLE);
}
```

The spec says the map screen renders an empty state when there are no hotspots — it is a valid, non-error condition. The `DomainException` maps to a non-2xx HTTP error, causing the client to show an error toast instead of an empty map.

---

## Feature 3 — Walk Intent (UC-15–18)

### 3.1 🔴 UC-15 Inline matching is never triggered during intent creation (Public path)
**File:** `presentation/controller/walkintent/WalkIntentController.java:53–76`

After saving the intent, the controller immediately returns `WalkIntentResponse` without calling `MatchingCommandService.findOrCreateProposal()`. The spec requires (Case A):
> "Backend tries to find a compatible partner during the create flow."

- **Case A1 (match found):** must return intent in `MATCHING` + a `WalkProposalResponse` embedded in the response.
- **Case A2 (no match):** return intent in `OPEN` with no proposal.

Currently both cases are indistinguishable — the client always gets an OPEN intent with no proposal and must fall back to polling. This breaks the "intent creates proposal inline" guarantee that drives the UI routing logic post-create.

### 3.2 🔴 UC-15 Private invite flow — receiver intent + proposal creation is entirely absent
**File:** `application/walkintent/WalkIntentCommandService.java:46–67`

When `command.isPrivate() == true`, the service validates the friendship and creates the **sender's intent only**. The spec (Case B, invariant **I-2 / P-1**) requires an atomic transaction that:
1. Creates sender intent (`MATCHING`).
2. Creates system-generated receiver intent (`MATCHING`, `is_private = true`, never enters public `OPEN`).
3. Creates `MatchProposal` in `PENDING` status.
4. Auto-accepts sender side via `MatchingCommandService.acceptProposal()`.
5. Sends push notifications: sender confirmation + receiver invite.

None of steps 2–5 are performed. A private invite never results in a proposal and the friend never receives a notification.

### 3.3 🔴 UC-15 Create Intent response shape cannot carry both intent + proposal
**File:** `presentation/controller/walkintent/WalkIntentController.java:54`

The controller is typed as `ResponseEntity<ApiResponse<WalkIntentResponse>>`. Even if inline matching is wired up (fixing gap 3.1), there is no response DTO that can carry both the `WalkIntentResponse` and an optional `WalkProposalResponse`. A new wrapper DTO is required.

---

## Feature 4 — Proposal (UC-19–22)

*(Proposal endpoints `GET /proposals`, `POST /{id}/accept`, `POST /{id}/pass`, `DELETE /{id}` are all present and correctly structured. No critical gaps detected here.)*

---

## Feature 5 — Session (UC-23–27)

### 5.1 🟠 UC-29 / UC-31 `WalkSessionResponse` missing `is_reviewed` field
**File:** `presentation/dto/response/session/WalkSessionResponse.java`

The record has no `is_reviewed` boolean. The spec (UC-29 step 3, UC-31) requires: "Show review prompt if not yet reviewed." The history screen must suppress the "Leave a Review" button once `isReviewed = true`. Without this field the client must make a separate lookup or always show the button (leading to `REVIEW_ALREADY_SUBMITTED` errors).

**Also:** `SessionMapper.toResponse()` must be updated to query `WalkReviewRepository.existsBySessionIdAndReviewerId()` to populate this field.

---

## Feature 6 — GPS Tracking (UC-28)

*(No backend gaps detected. `POST /api/v1/tracking/sync` correctly returns `{ "acknowledged_ids": [...] }` per spec.)*

---

## Feature 7 — Post-Session (UC-29–32)

*(Session history `GET /sessions/history`, route replay `GET /sessions/{id}/route`, review `POST /sessions/{id}/review`, report `POST /sessions/{id}/report` — all controllers present. The `is_reviewed` gap is the primary open item, already captured in 5.1.)*

---

## Feature 8 — Social (UC-33–38)

### DB Layer Audit (friendship table)

The production DB **already has** a `friendship` table:
```
friendship (friendship_id UUID PK, requester_id UUID FK, addressee_id UUID FK,
            status friend_status NOT NULL DEFAULT 'PENDING',
            version BIGINT NOT NULL DEFAULT 0, created_at, updated_at)
```
`SocialJdbcRepository` (migration V104) already queries this table — `follow(A,B)` inserts a PENDING friendship row, `areAcceptedFriends()` queries `WHERE status = 'ACCEPTED'`. **No DB migration is required.** The schema is fully ready for the Friends API.

### 8.1 🔴 UC-34–36 Friend request management API entirely missing despite DB being ready

**Files:** `presentation/controller/social/SocialController.java`, `application/social/SocialCommandService.java`, `domain/social/SocialRepository.java`

The `friendship` table and `areAcceptedFriends()` exist, but **all management endpoints are absent**:

| Required Endpoint | Status |
|---|---|
| `POST /api/v1/friends/{userId}/request` | ❌ Missing (equivalent to current `/follow` but spec-named) |
| `POST /api/v1/friends/requests/{requestId}/accept` | ❌ Missing |
| `POST /api/v1/friends/requests/{requestId}/decline` | ❌ Missing |
| `GET /api/v1/friends` | ❌ Missing (returns accepted friends only) |
| `GET /api/v1/friends/requests/incoming` | ❌ Missing |
| `GET /api/v1/friends/requests/outgoing` | ❌ Missing |
| `DELETE /api/v1/friends/{userId}` | ❌ Missing |

**Domain layer gap:** No `Friendship` entity exists that maps to the `friendship` table. `FollowRelation` is a dead record type never used by the repository. `SocialRepository` has no methods to: find a friendship by ID, list pending requests for a user, list outgoing requests, get accepted friend IDs, or remove a friendship row.

**Application layer gap:** `SocialCommandService` has only `follow/unfollow/block/unblock`. No `acceptFriendRequest`, `declineFriendRequest`, or `removeFriend` service method exists.

**Critical impact on UC-15 private invite:** `WalkIntentCommandService` calls `socialRepository.areAcceptedFriends()` which IS correctly implemented. Private invite validation is therefore **working**. However, users have no way to build the accepted-friend list in the first place (no accept endpoint), so in practice no private invites can be sent unless friendships were created directly in the DB.

### 8.2 🟡 `GET /api/v1/users/me/friends` returns followees, not accepted friends
**File:** `application/social/SocialQueryService.java:37–38`

`getFriends()` calls `socialRepository.getFolloweeIds(callerId)`, which returns all users the caller has a PENDING **or** ACCEPTED friendship row with (as requester). The spec requires only ACCEPTED friends. The comment in the source ("There is no dedicated Friendship table yet") is **stale and incorrect** — the `friendship` table exists and `areAcceptedFriends()` already queries it correctly. This method needs to call a new `getAcceptedFriendIds(callerId)` repository method.

### 8.3 🟡 `SocialController` exposes follow/unfollow endpoints not present in spec
**File:** `presentation/controller/social/SocialController.java:30–78`

Endpoints `POST /follow`, `DELETE /follow`, `GET /followers`, `GET /following` are exposed but absent from the updated UC-33–38 spec. Since `follow()` already writes to the `friendship` table as PENDING rows, the `/follow` endpoint is effectively a friend-request send under a different name. These endpoints must be deprecated once `POST /api/v1/friends/{userId}/request` is live.

---

## Feature 9 — Notifications (UC-39–40)

*(Both `GET /api/v1/notifications` and `POST /api/v1/notifications/{id}/read` are correctly implemented.)*

---

## Feature 10 — Gamification (UC-41–43)

*(Badges, stats, and leaderboard controllers are correctly structured per spec.)*

---

## Summary Table

| ID | Domain | Severity | Gap |
|---|---|---|---|
| 1.1 | Auth | 🔴 | UC-01 Register returns full token payload; spec requires email-only confirmation |
| 1.2 | Auth | 🟠 | UC-10/11 Logout returns 204 instead of 200 with null data |
| 2.1 | Discovery | 🟠 | `HotspotQueryService` throws on empty list instead of returning `[]` |
| 3.1 | Intent | 🔴 | UC-15 inline matching never triggered on create (public path) |
| 3.2 | Intent | 🔴 | UC-15 private invite flow absent — no receiver intent, no proposal, no auto-accept |
| 3.3 | Intent | 🔴 | UC-15 create response cannot embed proposal data (no union DTO) |
| 5.1 | Session | 🟠 | `WalkSessionResponse` missing `is_reviewed` field; review prompt cannot be shown/hidden |
| 8.1 | Social | 🔴 | UC-34–36 friend request API missing; `Friendship` domain entity + service + controller absent (DB table exists) |
| 8.2 | Social | 🟡 | `GET /me/friends` returns PENDING+ACCEPTED followees; must return ACCEPTED only |
| 8.3 | Social | 🟡 | `/follow`, `/unfollow`, `/followers`, `/following` not in spec — should be deprecated once Friends API is live |

**Total: 4 🔴 CRITICAL, 3 🟠 HIGH, 3 🟡 MEDIUM = 10 gaps**

---

## DB Readiness Summary

| Table | Status | Notes |
|---|---|---|
| `friendship` | ✅ Exists | `requester_id`, `addressee_id`, `status` (friend_status enum), `version` (optimistic lock) |
| `block_relation` | ✅ Exists | Fully backed by `SocialJdbcRepository` |
| `walk_intent` | ✅ Exists | Has `is_private`, `invited_friend_id`, `excluded_user_ids` |
| `walk_session` | ✅ Exists | Has all activation timestamps, `version` |
| `walk_review` | ✅ Exists | Backs `areAcceptedFriends` is separate; review check needs `existsBySessionIdAndReviewerId` |

No DB migrations are required for any gap in this analysis.
