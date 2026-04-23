# WalkMate History Flow Analysis

This document traces the end-to-end flow of the Walk Session History feature, identifies the root causes of the reported problems, and lists the associated API endpoints.

## 1. End-to-End Flow Trace

### Phase 1: History List Display
1.  **Entry Point:** `ProfileFragment` triggers navigation to `SessionHistoryFragment`.
2.  **Initialization:** `SessionHistoryFragment` initializes `SessionHistoryViewModel` using its factory.
3.  **Data Fetching:**
    *   `viewModel.loadHistory()` is called in `onViewCreated`.
    *   `WalkSessionRepository.getSessionHistory()` is invoked, calling `GET api/v1/sessions/history`.
    *   On success, the UI state is updated to `READY` with a list of `SessionSummary`.
4.  **Partner Name Enrichment:**
    *   The ViewModel identifies unique `partnerId`s from the session list.
    *   It triggers parallel calls to `UserProfileRepository.getProfile(partnerId)` (`GET api/v1/profiles/{userId}`).
    *   As each profile arrives, the UI state is updated via `LiveData`, and `SessionHistoryAdapter` reflects the display names instead of raw IDs.
5.  **Rendering:** `SessionHistoryAdapter` binds the data to `item_session_history.xml`.

### Phase 2: Route Replay (Map)
1.  **Selection:** User taps a session card.
2.  **Navigation:** `SessionHistoryFragment` starts `RouteReplayActivity`, passing `SESSION_ID`.
3.  **Data Fetching:**
    *   `RouteReplayViewModel.loadRoute(sessionId)` calls `GET api/v1/sessions/{sessionId}/route`.
    *   The response contains encoded polyline strings for both users.
4.  **Map Rendering:**
    *   Once `GoogleMap` is ready (`onMapReady`) and data is fetched, `drawRoute()` is called.
    *   `PolyUtil.decode()` (from `android-maps-utils`) decodes the strings into `LatLng` points.
    *   User A's path is drawn in **Blue**, User B's in **Red**.
    *   The camera animates to fit the bounds of all points.

### Phase 3: Reporting Incident
1.  **Action:** User clicks "Report" on a `COMPLETED` or `NO_SHOW` session.
2.  **Navigation:** `NavController` moves to `ReportIncidentFragment`, passing session metadata (ID, partner ID, status, terminal timestamp).
3.  **Validation:** `ReportIncidentFragment` checks the "Reporting Window" (72h for Completed, 24h for Aborted/No Show).
4.  **Submission:** User selects a reason and clicks "Submit", calling `POST api/v1/sessions/{sessionId}/report`.

---

## 2. Problem Diagnosis

### Problem 1: History Card UI
*   **Issue:** The card lacks border/color and looks "flat".
*   **Cause:** `item_session_history.xml` uses a basic `LinearLayout` as its root. It lacks `MaterialCardView`, elevation, and background drawables.
*   **Recommendation:** Wrap the layout in a `com.google.android.material.card.MaterialCardView` with `app:cardElevation="2dp"` and `app:cardCornerRadius="12dp"`.

### Problem 2: Map Rendering but Empty
*   **Issue:** Google Map loads, but no paths are visible.
*   **Cause:**
    1.  **Backend Data:** The `GET .../route` API is likely returning empty lists for `user_a_polylines` and `user_b_polylines`.
    2.  **Format Mismatch:** If strings are returned but not in the standard Google Encoded Polyline format, `PolyUtil.decode()` returns an empty list silently.
*   **Verification:** Check the network response for the `{sessionId}/route` endpoint. If the arrays are empty, the issue is in the backend GPS aggregation logic.

### Problem 3: Crash on Report Button
*   **Issue:** App crashes when entering the Report flow.
*   **Cause:**
    1.  **Navigation Race Condition:** If the button is clicked multiple times rapidly, the second `navigate()` call fails because the current destination has already changed (common in Jetpack Navigation).
    2.  **Missing Status Case:** `SessionHistoryAdapter` identifies `reportable` sessions as `COMPLETED` or `NO_SHOW`. However, `ABORTED` sessions (which are reportable according to business rules) are excluded in the adapter but expected in the fragment logic.
    3.  **Resource Missing:** If a specific drawable or color (like `ic_back` or `text_muted`) was missing, inflation would fail. (Note: These were verified as existing, so navigation state is the prime suspect).

---

## 3. Backend API Dependencies

| Endpoint | Method | Purpose |
| :--- | :--- | :--- |
| `/api/v1/sessions/history` | `GET` | Fetches the list of past sessions for the current user. |
| `/api/v1/sessions/{sessionId}/route` | `GET` | Fetches encoded GPS polylines for a specific session. |
| `/api/v1/profiles/{userId}` | `GET` | Fetches user details (used to show partner names in history). |
| `/api/v1/sessions/{sessionId}/report` | `POST` | Submits an incident report for a session. |
