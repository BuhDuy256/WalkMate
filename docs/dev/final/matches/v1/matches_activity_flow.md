# Matches Activity (Intent, Proposal, Session) Technical Documentation

This document traces the end-to-end lifecycle of the "Matches" hub in the WalkMate system, covering the three primary phases: **Finding (Intent)**, **Matching (Proposal)**, and **Walking (Session)**.

## 1. Architectural Overview

The Matches module follows a strict **MVVM (Frontend)** and **DDD-lite (Backend)** architecture.

- **Frontend**: 
    - **Container**: `MatchesFragment` uses a `ViewPager2` to manage three sub-tabs.
    - **State Management**: A shared `MatchesViewModel` (scoped to the Activity) acts as the Single Source of Truth for all three tabs.
    - **Reactive UI**: The `MatchesUiState` aggregates data from `WalkIntent`, `WalkProposal`, and `WalkSession` repositories.
- **Backend**:
    - **Controllers**: Three specialized controllers (`WalkIntentController`, `ProposalController`, `SessionController`) manage the distinct domain lifecycles.
    - **Domain Integrity**: The system enforces invariants such as matching compatibility (age range, time overlap) and session duration guards (minimum 5-minute walk).

---

## 2. Intent & Finding Flow (Tab 1)

Users express their availability by creating a **Walk Intent**.

### 2.1 Listing & Polling
When the user enters the "Finding" tab, the `MatchesViewModel` fetches all `OPEN` or `MATCHING` intents.
1. **UI**: `FindingFragment` observes `uiState.getActiveIntents()`.
2. **Action**: `matchesViewModel.loadAll()` triggers `intentRepository.listActiveIntents()`.
3. **API**: `GET /api/v1/intents`

### 2.2 Intent Creation
1. **Action**: User creates an intent (usually from the Explore screen).
2. **API**: `POST /api/v1/intents`
3. **Matching Engine**: 
    - **Case A1 (Inline Match)**: A candidate is found immediately. The backend returns a `CreateIntentResponse` with both the `intent` and a `proposal`. The UI navigates to the **Proposal** tab.
    - **Case A2 (No Match)**: The intent is created with status `OPEN`. The response `proposal` is null.

### 2.3 Manual Match Trigger
If an intent is `OPEN`, the user can manually re-trigger the matching engine.
1. **API**: `POST /api/v1/intents/{intentId}/match`
2. **Result**: If a match is found, returns a `WalkProposalResponse`; otherwise returns `204 No Content`.

---

## 3. Proposal & Matching Flow (Tab 2)

A **Proposal** is a temporary contract between two users' intents.

### 3.1 The P-3 Acceptance Protocol
WalkMate uses a double-acceptance protocol to ensure both parties are committed.
1. **User A Accepts**:
    - **API**: `POST /api/v1/proposals/{proposalId}/accept`
    - **State**: Proposal status remains `PENDING`. `my_acceptance_status` becomes `ACCEPTED`.
    - **UI**: `ProposalAdapter` shows a "Waiting for partner..." overlay.
2. **User B Accepts**:
    - **API**: `POST /api/v1/proposals/{proposalId}/accept`
    - **State**: Proposal status transitions to `CONFIRMED`. A `WalkSession` is automatically created.
    - **UI**: `MatchesViewModel` fires a `celebrationEvent` and scrolls to the **Session** tab.

### 3.2 Pass & Cancel
- **Pass**: Rejecting a proposal (`POST .../pass`) keeps the user's intent `OPEN` for other matches.
- **Cancel**: Deleting a proposal (`DELETE .../{proposalId}`) hard-cancels both the proposal and the caller's intent.

---

## 4. Session Lifecycle Flow (Tab 3)

A **Session** represents the actual physical meeting and walk.

### 4.1 Arrival & Activation
Participants must signal arrival at the hotspot.
1. **Action**: User clicks "Arrive" in the `SessionFragment`.
2. **API**: `POST /api/v1/sessions/{sessionId}/activate`
3. **Logic**:
    - If only one user activates: Status remains `PENDING`. UI shows waiting status.
    - If both activate: Status becomes `ACTIVE`.
4. **Transition**: Once `ACTIVE`, the `SessionFragment` launches `TrackingScreenActivity` for GPS path tracing.

### 4.2 Completion & History
- **Complete**: User finishes the walk (`POST .../complete`). 
- **Validation**: Enforces the **S-5 Guard** (Session must last at least 5 minutes).
- **History**: Once terminal (`COMPLETED` or `CANCELLED`), sessions disappear from the Matches screen and move to Session History.

---

## 5. API Reference & Examples

### 5.1 Create Walk Intent
- **Endpoint**: `POST /api/v1/intents`
- **Request Body**:
```json
{
  "hotspot_id": "ho_guom_01",
  "date": "2026-04-22",
  "time_start": 17.5,
  "time_end": 19.0,
  "age_min": 18,
  "age_max": 25,
  "is_private": false,
  "description": "Walking around the lake"
}
```
- **Response**: `ApiResponse<CreateIntentResponse>`

### 5.2 Accept Proposal
- **Endpoint**: `POST /api/v1/proposals/{proposalId}/accept`
- **Response (Case B - Both Accepted)**:
```json
{
  "success": true,
  "data": {
    "proposal_id": "prop_456",
    "status": "CONFIRMED",
    "session_id": "sess_789",
    "my_acceptance_status": "ACCEPTED"
  }
}
```

### 5.3 Activate Session (Arrival)
- **Endpoint**: `POST /api/v1/sessions/{sessionId}/activate`
- **Response**: `ApiResponse<WalkSessionResponse>`
```json
{
  "success": true,
  "data": {
    "session_id": "sess_789",
    "status": "ACTIVE",
    "user_a_activated_at": "2026-04-22T17:35:00Z",
    "user_b_activated_at": "2026-04-22T17:40:00Z",
    "user_a_status": "ACTIVE",
    "user_b_status": "ACTIVE"
  }
}
```

### 5.4 Cancel Session
- **Endpoint**: `POST /api/v1/sessions/{sessionId}/cancel`
- **Request Body**:
```json
{
  "reason": "Something came up, sorry!"
}
```
- **Response**: `ApiResponse<Void>`
