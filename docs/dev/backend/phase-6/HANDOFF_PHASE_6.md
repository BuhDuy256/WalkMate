# Phase 6 Handoff — GPS Tracking Sync

## What Was Built

Phase 6 replaces the frontend mock `pushRoutePoints()` with a real backend endpoint. GPS route points recorded locally in Room are now batched, compressed as a Google Encoded Polyline + binary timestamp blob, and persisted in the `session_point_chunks` table.

---

## Backend

### New Migration

| File | Purpose |
|------|---------|
| `V11__create_session_point_chunks.sql` | Creates `session_point_chunks` table: `id`, `session_id`, `chunk_index`, `polyline` (TEXT), `timestamps` (BYTEA), `point_count`, `created_at`. Unique constraint on `(session_id, chunk_index)`. |

### New Domain Class

| Class | Role |
|-------|------|
| `domain/tracking/TrackingChunkRepository` | Interface: `nextChunkIndex(sessionId)`, `saveChunk(sessionId, chunkIndex, polyline, timestamps, pointCount)` |

### New Infrastructure

| Class | Role |
|-------|------|
| `infrastructure/repository/tracking/TrackingChunkJdbcRepository` | `JdbcClient`-based. `nextChunkIndex` uses `COALESCE(MAX(chunk_index)+1, 0)` for safe atomic index selection within the transaction. |
| `infrastructure/util/PolylineEncoder` | Pure Java implementation of the Google Encoded Polyline Algorithm. `encode(List<Double> lats, List<Double> lngs)` — no Android dependency, fully unit-testable. |

### New Application Layer

**`application/tracking/TrackingCommandService.syncRoutePoints(sessionId, callerId, points)`**:

1. Loads session, verifies `status == ACTIVE` and caller is a participant.
2. Validates all points: lat ∈ [−90, 90], lng ∈ [−180, 180], timestamp ≤ now.
3. Encodes coordinate pairs via `PolylineEncoder.encode()`.
4. Packs timestamps as big-endian `long[]` → `BYTEA` (8 bytes × pointCount).
5. Calls `chunkRepository.nextChunkIndex(sessionId)` inside the same `@Transactional` scope.
6. Calls `chunkRepository.saveChunk(...)`.
7. Returns `PushRoutePointsResponse` with the echoed `acknowledgedIds`.

### New Presentation Layer

| Class | Endpoint |
|-------|---------|
| `TrackingController` | `POST /api/v1/tracking/sync` |
| `PushRoutePointsRequest` | `{ "session_id": "...", "points": [{ "local_id", "lat", "lng", "timestamp", "accuracy" }] }` — `@Valid` with bean-validation constraints |
| `PushRoutePointsResponse` | `{ "acknowledged_ids": [123, 124, ...] }` |

### Modified Configuration

- `SecurityConfig` — added `.requestMatchers("/api/v1/tracking/**").authenticated()`.

---

## Frontend

### Modified Files

| File | Change |
|------|--------|
| `data/repository/TrackingRepositoryImpl.java` | Full rewrite of `pushRoutePoints()` — mock body removed, real Retrofit call with `RoutePointSyncApiService` wired. `SessionManager` added to constructor. |
| `WalkMateApplication.java` | `SessionManager` eagerly instantiated in `onCreate()`; passed to `TrackingRepositoryImpl` constructor. New `getSessionManager()` getter exposed for other repositories that may need it. |

### No DTO Changes Required

`PushRoutePointsRequest`, `PushRoutePointsResponse`, and `RoutePointSyncApiService` were already complete from Phase 0. No changes needed.

---

## API Contract Summary

```
POST /api/v1/tracking/sync
  Body:   { "session_id": "uuid", "points": [...] }
  200:    { "success": true, "data": { "acknowledged_ids": [1, 2, ...] } }
  400:    SESSION_NOT_ACTIVE | SESSION_NOT_PARTICIPANT | VALIDATION_ERROR
```

All endpoints require a valid JWT (`Authorization: Bearer <token>`). The session must be `ACTIVE`.

---

## Chunk Data Format

| Field | Type | Description |
|-------|------|-------------|
| `polyline` | `TEXT` | Google Encoded Polyline of all coordinates in the batch |
| `timestamps` | `BYTEA` | Big-endian packed `long[]` — 8 bytes × `point_count` (Unix epoch ms) |
| `chunk_index` | `INT` | 0-based, monotonically increasing per session |

To reconstruct the full route: decode all chunks ordered by `chunk_index`, concatenate the decoded coordinate lists.

---

## Sync Reliability Guarantee

- Points remain in Room with `isSynced = false` until the server acknowledges them.
- The `acknowledgedIds` in the response contain exactly the `localId` values the client submitted, enabling precise partial-sync marking in future versions.
- If the network call fails, the points stay in Room and will be retried the next time the 50-point threshold is crossed.

---

## Known Gaps / Phase 7 Considerations

1. **Chunk reassembly endpoint** — no backend `GET /api/v1/sessions/{id}/route` endpoint yet to serve the encoded polyline back to the client.
2. **Partner live-location sharing** — chunks are stored per-user. Phase 7 could serve each user's latest chunk to the other via WebSocket.
3. **`nextChunkIndex` race condition** — two concurrent uploads from the same user on different threads would collide. Mitigated by the unique constraint (second insert fails), but the caller currently gets an opaque 500. Phase 7 should add a retry-on-conflict loop or use a DB sequence per session.
