# Session Flow & GPS Path Tracing Technical Documentation

This document traces the end-to-end lifecycle of a walk session in the WalkMate system, from initial discovery and activation to the high-frequency GPS path tracing and eventual synchronization with the backend.

## 1. Architectural Overview

The system follows a strict **MVVM (Frontend)** and **DDD-lite + Layered (Backend)** architecture.

- **Frontend**: Utilizes `ViewModel` to bridge UI and Domain logic, `LiveData` for reactive UI updates, and a `Foreground Service` for reliable GPS collection. `Room DB` acts as the Offline-First source of truth for coordinates.
- **Backend**: Implements a Rich Domain Model where the `Session` entity governs its own state transitions (PENDING → ACTIVE → COMPLETED/CANCELLED).

---

## 2. Session Lifecycle Flow

### 2.1 Discovery & Polling
When the user opens the Matches screen, the `SessionFragment` initiates a fetch of all non-terminal sessions.

1.  **UI**: `SessionFragment.onViewCreated` calls `matchesViewModel.loadAll()`.
2.  **Data**: `SessionRepository.getActiveSessions()` performs a network request.
3.  **API**: `GET /api/v1/sessions/active`
4.  **Result**: Returns a list of `WalkSessionResponse` objects.

### 2.2 Arrival & Activation
Participants must signal their arrival at the meeting point to transition the session to `ACTIVE`.

1.  **Action**: User clicks the "Arrive" button (via `ActivationWindowButtonView` in `SessionAdapter`).
2.  **ViewModel**: `matchesViewModel.activateSession(sessionId)` is triggered.
3.  **API**: `POST /api/v1/sessions/{sessionId}/activate`
4.  **Backend Logic**: 
    - If User A activates, session status remains `PENDING`, but `user_a_activated_at` is set.
    - If User B activates and User A is already active, session status transitions to `ACTIVE`.
5.  **Transition**: `SessionFragment` observes the `ACTIVE` status and launches `TrackingScreenActivity`.

---

## 3. GPS Path Tracing Flow (Deep Dive)

Once the session is `ACTIVE`, the high-frequency coordinate tracing begins.

### 3.1 Initialization
`TrackingScreenActivity` starts `TrackingViewModel`, which restores any persisted walk state (Timer, Distance) from Room to ensure continuity if the app was closed.

### 3.2 Collection (Foreground Service)
1.  **Service Start**: `TrackingViewModel.startWalk()` launches `WalkTrackerService` as an Android Foreground Service.
2.  **GPS Acquisition**: The service uses `FusedLocationProviderClient` to request high-accuracy updates every 5 seconds.
3.  **Domain Hand-off**: Raw coordinates are passed to `SessionTrackingService` (Domain Layer).

### 3.3 Filtering & Local Persistence
To prevent "GPS jitter" and database bloat, points are filtered before storage.

1.  **Filter Policy**: `LocationFilterPolicy` rejects points with:
    - Low accuracy (> 300m radius).
    - Micro-movements (< 1m from last point).
2.  **Persistence**: Accepted points are saved to the `route_points` table in Room DB via `TrackingRepositoryImpl`.
3.  **Reactive UI**: `TrackingViewModel` observes the Room DB. New points trigger a `LiveData` emission, updating the Google Map `Polyline` and stats (Distance, Pace) on the UI.

### 3.4 Background Synchronization (Batch Sync)
Coordinates are uploaded to the backend in chunks to optimize battery and network usage.

1.  **Threshold**: `TrackingRepositoryImpl` monitors the count of unsynced points.
2.  **Trigger**: When `unsyncedCount >= 50`, a batch sync is initiated.
3.  **API**: `POST /api/v1/tracking/sync`
4.  **Acknowledgement**: The backend returns the IDs of the points it successfully saved.
5.  **Clean-up**: The Frontend marks these points as `isSynced = true` in Room.

---

## 4. Session Completion

1.  **Gatekeeper**: Completion is only allowed after a minimum duration (e.g., 5 minutes).
2.  **Action**: User clicks "Complete Walk".
3.  **API**: `POST /api/v1/sessions/{sessionId}/complete`
4.  **Cleanup**: `TrackingViewModel` clears Room persistence and stops the `WalkTrackerService`.
5.  **Summary**: `PostSessionSummaryFragment` is displayed with final statistics.

---

## 5. API Reference & Examples

### 5.1 Activate Session
Used to record arrival at the meeting point.

- **Endpoint**: `POST /api/v1/sessions/{sessionId}/activate`
- **Request**: Empty body (Auth via JWT)
- **Response**: `ApiResponse<WalkSessionResponse>`

**Example Response**:
```json
{
  "success": true,
  "data": {
    "session_id": "sess_123",
    "status": "ACTIVE",
    "user_a_activated_at": "2026-04-22T10:00:00Z",
    "user_b_activated_at": "2026-04-22T10:05:00Z",
    "started_at": "2026-04-22T10:05:00Z"
  }
}
```

### 5.2 Sync Tracking Points
Uploads a batch of GPS coordinates.

- **Endpoint**: `POST /api/v1/tracking/sync`
- **Request**: `PushRoutePointsRequest`
- **Response**: `PushRoutePointsResponse`

**Example Request**:
```json
{
  "session_id": "sess_123",
  "points": [
    {
      "local_id": 101,
      "lat": 10.762622,
      "lng": 106.660172,
      "timestamp": 1713780000000,
      "accuracy": 15.5
    },
    {
      "local_id": 102,
      "lat": 10.762700,
      "lng": 106.660200,
      "timestamp": 1713780005000,
      "accuracy": 12.0
    }
  ]
}
```

**Example Response**:
```json
{
  "success": true,
  "data": {
    "acknowledged_ids": [101, 102]
  }
}
```

### 5.3 Complete Session
Finalizes the walk and triggers gamification/rewards.

- **Endpoint**: `POST /api/v1/sessions/{sessionId}/complete`
- **Request**: Empty body
- **Response**: `ApiResponse<WalkSessionResponse>`

**Example Response**:
```json
{
  "success": true,
  "data": {
    "session_id": "sess_123",
    "status": "COMPLETED",
    "ended_at": "2026-04-22T10:45:00Z"
  }
}
```
