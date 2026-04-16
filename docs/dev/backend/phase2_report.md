# Phase 2 Report — Session `isReviewed`

**Date:** 2026-04-13
**Branch:** `merge/oauth`
**Gap Closed:** 5.1

---

## Changes Made

### 1. `WalkSessionResponse.java`
Added `isReviewed` as the final record component:
```java
@JsonProperty("is_reviewed")
boolean isReviewed
```

### 2. `SessionMapper.java`
Updated `toResponse()` signature to accept an explicit `boolean isReviewed` parameter
and pass it as the last argument to the `WalkSessionResponse` constructor.
The mapper remains a pure mapper — no DB calls.

### 3. `SessionController.java` (3 callers updated)
All three callers deal with PENDING/ACTIVE sessions or a just-completed session where a review
cannot yet exist. All pass `false`:

| Method | Endpoint | Rationale |
|---|---|---|
| `getActiveSessions` | `GET /sessions/active` | Sessions are PENDING or ACTIVE — review not possible yet |
| `activateSession` | `POST /sessions/{id}/activate` | Session transitions to ACTIVE — no review possible |
| `completeSession` | `POST /sessions/{id}/complete` | Session just completed — no review can have been submitted |

---

## Pre-existing Correct Implementations

- **`SessionSummaryResponse`** — already had `is_reviewed` and was already populated correctly.
- **`SessionHistoryQueryService`** — already calls `reviewRepository.existsBySessionAndReviewer()` per item.
- **`WalkReviewRepository.existsBySessionAndReviewer()`** — already existed; no changes required.
- **`WalkReviewJdbcRepository`** — already implemented with `SELECT COUNT(*) > 0`.

No new repository methods were needed.

---

## Verification

- `WalkSessionResponse` — `is_reviewed` field present at end of record.
- `SessionMapper` — `toResponse(WalkSession, boolean)` signature confirmed.
- `SessionController` — all 3 call sites pass explicit `false`.
- MCP re-index: 3 files changed, 0 deleted.
