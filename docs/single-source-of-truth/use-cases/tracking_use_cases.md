# WalkMate — GPS Tracking Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Background GPS Route Synchronization
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-28 | [Background GPS Route Sync](#uc-28--background-gps-route-sync) | `POST /api/v1/tracking/sync` |

---

### UC-28 — Background GPS Route Sync

**Use Case Name:** Background GPS Route Sync

**Initial assumption:** Session is in `ACTIVE` status. This task starts automatically when UC-24 (Case B) completes. It runs entirely in the background — the user should not need to interact with it.

**Normal:**
1. Android GPS service collects location fixes at regular intervals (e.g., every 5 seconds).
2. Points are buffered locally. Each point has a client-assigned `local_id` (auto-increment), `lat`, `lng`, `timestamp` (epoch ms), and `accuracy`.
3. Every 30 seconds (or when buffer reaches N points), UI calls `POST /api/v1/tracking/sync`:
   ```json
   {
     "session_id": "...",
     "points": [
       { "local_id": 1, "lat": 10.776, "lng": 106.700, "timestamp": 1744123456000, "accuracy": 5.2 },
       ...
     ]
   }
   ```
4. Backend returns `200 OK` with `{ "acknowledged_ids": [1, 2, 3, ...] }`.
5. UI marks all acknowledged `local_id`s as synced. Removes them from the local buffer.
6. Unacknowledged points remain in buffer and are retried on the next sync cycle.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Session is no longer ACTIVE (e.g., session was auto-completed after 4h) | `SESSION_NOT_ACTIVE` | Stop the sync loop silently. Show a notification: "Your walk session has ended." Navigate to history. |
| Future timestamp submitted | `INVALID_ARGUMENT` | Skip the offending point(s) client-side; log the error. |
| lat/lng out of bounds | `INVALID_ARGUMENT` | Skip the offending points client-side. |
| Session not found | `SESSION_NOT_FOUND` | Stop sync loop and navigate to history. |
| Network failure | — | Keep points in the local buffer and retry on the next cycle. Do not discard unsynced points. |

**Other activities:**
- This task must respect Android battery optimization — use `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY` while ACTIVE, then stop.
- The sync must stop when the session reaches any terminal state (`COMPLETED`, `ABORTED`, `CANCELLED`, `NO_SHOW`).

**System state on completion:** GPS polyline data is stored server-side and available for route replay (UC-30) after the session ends.
