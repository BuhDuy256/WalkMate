# WalkMate API — Postman Collection Guide

**File:** `WalkMate_API.postman_collection.json`  
**Schema:** Postman Collection v2.1.0  
**Coverage:** UC-01 through UC-43 (43 use cases) + 1 full E2E integration scenario

---

## Overview

This collection contains two types of testing content:

| Folder | Purpose |
|--------|---------|
| **E2E Integration Flow** | 18-step chronological scenario for Collection Runner automated testing |
| **Auth & Profile**, **Discovery**, **Walk Intent**, … | Individual domain-scoped requests for manual exploration and regression testing |

---

## Quick Setup

### 1. Create a Postman Environment

In Postman, go to **Environments → +** and create a new environment (e.g., `WalkMate Local`).

Add this one variable:

| Variable | Initial Value |
|----------|--------------|
| `baseUrl` | `http://localhost:8080` |
| `hotspotId` | _(paste a real hotspot UUID after seeding the DB)_ |

All other variables (`userA_token`, `userB_id`, `sessionId`, etc.) are written automatically by test scripts — leave them blank.

### 2. Import the Collection

**File → Import → Upload Files** → select `WalkMate_API.postman_collection.json`.

### 3. Start the Backend

```bash
# from project root
./gradlew bootRun
# or
docker-compose up
```

Ensure the server is reachable at `http://localhost:8080/api/v1/hotspots` before running.

### 4. Seed a Hotspot ID

The E2E flow and most intent requests depend on `{{hotspotId}}`. Run this once:

```
GET {{baseUrl}}/api/v1/hotspots
```

Copy any `id` from the response array and paste it into your environment as `hotspotId`.

---

## Running the E2E Integration Flow (Collection Runner)

This folder simulates two strangers — **Alice (User A)** and **Bob (User B)** — going through the full app lifecycle: register, public match, walk, become friends, then private walk.

### Step-by-Step

1. In Postman, click the collection name **WalkMate API**.
2. Click **Run collection** (the triangle icon at the top).
3. In the Runner panel:
   - Under **Folders**, select only **E2E Integration Flow** (deselect all others).
   - Set **Environment** to `WalkMate Local`.
   - Leave **Iterations** = `1`.
   - Leave **Delay** = `0 ms` (or add a small delay if your backend is slow).
4. Click **Run WalkMate API**.

### What Happens

The runner executes all 18 requests in order. Each request's test script saves variables that the next request uses:

```
Step  1  Register User A          → userA_token, userA_id
Step  2  Register User B          → userB_token, userB_id
Step  3  [A] Create Public Intent → userA_intentId
Step  4  [B] Create Public Intent → proposalId          (inline match triggers here)
Step  5  [A] Accept Proposal      → (partial, PENDING)
Step  6  [B] Accept Proposal      → sessionId           (CONFIRMED, WalkSession created)
Step  7  [A] Activate Session     → (PENDING → waiting)
Step  8  [B] Activate Session     → ACTIVE              (walk begins)
Step  9  [A] Complete Session     → COMPLETED
Step 10  [A] Submit Review        → (trust score updated)
Step 11  [A] Send Friend Request  → (friend request PENDING)
Step 12  [B] View Incoming Reqs   → friendRequestId
Step 13  [B] Accept Friend Req    → (friendship ACCEPTED — private invite unlocked)
Step 14  [A] Create Private Intent→ privateProposalId, proposalId (mirrored)
Step 15  [B] View Proposals       → (sees private invite)
Step 16  [B] Accept Proposal      → sessionId           (new CONFIRMED session)
Step 17  [A] Activate Private Ses → (PENDING)
Step 18  [B] Activate Private Ses → ACTIVE              (E2E complete)
```

### Reading Results

After the run, the Runner shows a pass/fail table. Key things to check:

- **All green:** Full lifecycle working end-to-end.
- **Step 4 fails** (`data.proposal is null`): The hotspot or time window doesn't match Step 3's intent. Make sure both intents use the same `hotspotId` and overlapping time window.
- **Step 9 fails with `SESSION_COMPLETE_TOO_EARLY`:** The 5-minute invariant (S-5) is enforced by the backend. This is **expected in automated testing**. The test script marks this as a passing assertion variant. To get a true COMPLETED status, wait 5 minutes after Step 8 before running Step 9 manually.
- **Steps 7/8/17/18 fail with `SESSION_ACTIVATION_WINDOW_CLOSED`:** The scheduled walk time has already passed. Update the `date`, `time_start`, and `time_end` fields in Steps 3/4 and Steps 14 to a future time before running.
- **Step 14 fails with `INTENT_PRIVATE_FRIEND_NOT_ACCEPTED`:** Step 13 did not complete successfully. Re-check Steps 11–13.

---

## Authentication Model

### Collection-Level Auth

The collection uses **Bearer Token** at the collection level:

```
Authorization: Bearer {{accessToken}}
```

This applies to all requests **except** those that explicitly override it.

### Per-Request Overrides in E2E Flow

Every request in the E2E folder overrides the collection-level auth with a user-specific token variable:

| Who | Token Variable |
|-----|---------------|
| User A requests | `{{userA_token}}` |
| User B requests | `{{userB_token}}` |
| Register steps | `noauth` (public endpoint) |

This allows the runner to correctly impersonate two different users in a single run.

### Token Extraction

The Register scripts decode the JWT access token to extract `userId`:

```javascript
function parseJwt(token) {
    var b64 = token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/');
    return JSON.parse(atob(b64));
}
var payload = parseJwt(json.data.accessToken);
var userId = payload.sub; // Spring Security sets userId as the 'sub' claim
```

This avoids a separate `GET /api/v1/profile/me` call just to get the user ID.

---

## ApiResponse\<T\> Wrapper

Every backend response is wrapped:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "2026-06-01T17:00:00Z"
}
```

On error:

```json
{
  "success": false,
  "data": null,
  "error": { "code": "VALIDATION_ERROR", "message": "email: must be valid" },
  "timestamp": "..."
}
```

All test scripts check `json.success === true` before asserting on `json.data`. Validation errors return **HTTP 422**; domain rule violations return **HTTP 400**.

---

## Domain-Scoped Folders (Manual Testing)

Use these folders to test individual use cases in isolation:

| Folder | Use Cases | Notes |
|--------|-----------|-------|
| Auth & Profile | UC-01 to UC-13 | Includes negative tests for VALIDATION_ERROR |
| Discovery | UC-14 | Public endpoint — no auth needed |
| Walk Intent | UC-15 to UC-18 | Both public and private intent variants |
| Proposal Negotiation | UC-19 to UC-22 | |
| Session Lifecycle | UC-23 to UC-27 | |
| GPS Tracking | UC-28 | |
| Post-Session | UC-29 to UC-32 | |
| Social | UC-33 to UC-38 | |
| Notifications | UC-39 to UC-40 | |
| Gamification | UC-41 to UC-43 | Public endpoints |

For these folders, first run **UC-02 Login** (in Auth & Profile) to populate `{{accessToken}}`, then the collection-level Bearer auth applies automatically to all subsequent requests.

---

## Variable Reference

| Variable | Set by Step | Used by Steps | Description |
|----------|------------|---------------|-------------|
| `userA_token` | 1 | 3,5,7,9,10,11,14,17 | User A's JWT access token |
| `userB_token` | 2 | 4,6,8,12,13,15,16,18 | User B's JWT access token |
| `userA_id` | 1 | 14 | User A's UUID (from JWT `sub` claim) |
| `userB_id` | 2 | 11,14 | User B's UUID (from JWT `sub` claim) |
| `userA_intentId` | 3 | — | User A's public walk intent ID |
| `userB_intentId` | 4 | — | User B's public walk intent ID |
| `proposalId` | 4, then 15 | 5,6,16 | Active proposal ID (public, then overwritten to private) |
| `sessionId` | 6, then 16 | 7,8,9,10,17,18 | Active session ID (Phase 2, then Phase 4) |
| `friendRequestId` | 12 | 13 | ID of User A's friend request to User B |
| `privateProposalId` | 14 | 15 | Private invite proposal ID |
| `userA_privateIntentId` | 14 | — | User A's private intent ID |
| `hotspotId` | (manual, env) | 3,4,14 | Target hotspot UUID — must be pre-set |

---

## Troubleshooting

**"proposalId is blank" in Steps 5/6:**  
Step 4 failed to find an inline match. Confirm that `hotspotId`, `date`, `time_start`, and `time_end` in Steps 3 and 4 are identical, and that User A's intent is still OPEN when Step 4 runs.

**"userB_id is blank" in Step 14:**  
JWT decoding failed in Step 2. Verify the backend JWT uses `sub` as the user ID claim. If your backend uses a different claim name (e.g., `userId`), update the `parseJwt` extraction line in Steps 1 and 2.

**"USER_ALREADY_EXISTS" on Register:**  
Each test run requires unique email addresses. Either flush the `users` table between runs, or modify the email values in Steps 1 and 2 (e.g., append a timestamp: `alice+{{$timestamp}}@walkmate.vn`).

**Steps 7/8 pass but Step 9 gets `SESSION_COMPLETE_TOO_EARLY`:**  
Expected behavior — invariant S-5 requires 5 minutes of active walk. Wait 5 minutes after Step 8 before re-running Step 9 manually, or configure your test environment to bypass the time guard.
