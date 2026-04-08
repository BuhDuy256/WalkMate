# Phase 1 Output Report — GPS Chunk Repository & Service Layer

**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Implemented by:** Claude (Sonnet 4.6)

---

## 1. ACKG Pre-flight Results

ACKG `find_usages` returned empty for all symbols (the MCP indexes declarations/imports, not inline call sites). Verification was performed by direct file reads, which is authoritative.

### 1.1 `nextChunkIndex` — call sites

| File | Line | Call |
|------|------|------|
| `TrackingChunkRepository.java` | 11 | interface declaration `int nextChunkIndex(String sessionId)` |
| `TrackingChunkJdbcRepository.java` | 18 | implementation override |
| `TrackingCommandService.java` | 88 | `chunkRepository.nextChunkIndex(sessionId)` |

**Matches Phase 0 report exactly. No discrepancy.**

### 1.2 `saveChunk` — call sites

| File | Line | Call |
|------|------|------|
| `TrackingChunkRepository.java` | 28 | interface declaration |
| `TrackingChunkJdbcRepository.java` | 43 | implementation override |
| `TrackingCommandService.java` | 89 | `chunkRepository.saveChunk(sessionId, chunkIndex, polyline, timestampBytes, points.size())` |

**Matches Phase 0 report exactly. No discrepancy.**

### 1.3 `calculateTotalDistanceKm` — call site

| File | Line | Call |
|------|------|------|
| `GamificationCommandService.java` | 87 | `calculateTotalDistanceKm(session.getSessionId())` — parameter type: `String sessionId` |
| `GamificationCommandService.java` | 145 | private method definition |

**Confirmed: parameter was `String sessionId`. Changed to `WalkSession session` per plan.**

### 1.4 `findPolylinesBySessionId` — call sites

| File | Line | Call |
|------|------|------|
| `GamificationCommandService.java` | 146 | `trackingChunkRepository.findPolylinesBySessionId(sessionId)` |
| `TrackingChunkRepository.java` | 17 | interface declaration |
| `TrackingChunkJdbcRepository.java` | 31 | implementation override |

**Only one non-infrastructure caller: `GamificationCommandService`. That call site is replaced in Step 1.4. `findPolylinesBySessionId` is retained in the interface (still needed by History flow — G-12/G-14).**

---

## 2. Files Modified

| File | Change |
|------|--------|
| `backend/src/main/java/com/walkmate/domain/tracking/TrackingChunkRepository.java` | Replaced old `nextChunkIndex(String)` and `saveChunk(String, int, ...)` with 4 new per-user scoped methods; retained `findPolylinesBySessionId` |
| `backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java` | Full rewrite: implemented 5 methods (4 new + retained `findPolylinesBySessionId`); removed old single-user implementations |
| `backend/src/main/java/com/walkmate/application/tracking/TrackingCommandService.java` | Two-line change: pass `callerId` to both `nextChunkIndex` and `saveChunk` calls |
| `backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java` | Changed `calculateTotalDistanceKm` signature + body; updated call site in `rewardBothParticipants` |

---

## 3. Interface Diff Summary

### Methods removed from `TrackingChunkRepository`

```java
// REMOVED — no callers outside TrackingCommandService (confirmed by ACKG + direct read)
int nextChunkIndex(String sessionId);
void saveChunk(String sessionId, int chunkIndex, String polyline, byte[] timestamps, int pointCount);
```

### Methods added to `TrackingChunkRepository`

```java
// Closes G-2
int nextChunkIndex(String sessionId, String userId);
void saveChunk(String sessionId, String userId, int chunkIndex, String polyline,
               byte[] timestamps, int pointCount);
List<String> findPolylinesBySessionAndUser(String sessionId, String userId);

// Closes G-4
int countChunks(String sessionId, String userId);
```

### Method retained (unchanged)

```java
// Retained — still required by History flow (Phase 4 / G-14); not removed per plan note
List<String> findPolylinesBySessionId(String sessionId);
```

---

## 4. JDBC Implementation Snippets

All SQL references the V107 schema (with `user_id uuid NOT NULL` on `session_point_chunks`).

### `nextChunkIndex(sessionId, userId)`

```sql
SELECT COALESCE(MAX(chunk_index) + 1, 0)
FROM session_point_chunks
WHERE session_id = :sessionId AND user_id = :userId
```

### `saveChunk(sessionId, userId, chunkIndex, polyline, timestamps, pointCount)`

```sql
INSERT INTO session_point_chunks
    (session_id, user_id, chunk_index, polyline, timestamps, point_count)
VALUES
    (:sessionId, :userId, :chunkIndex, :polyline, :timestamps, :pointCount)
```

### `findPolylinesBySessionAndUser(sessionId, userId)`

```sql
SELECT polyline FROM session_point_chunks
WHERE session_id = :sessionId AND user_id = :userId
ORDER BY chunk_index ASC
```

### `countChunks(sessionId, userId)`

```sql
SELECT COUNT(*) FROM session_point_chunks
WHERE session_id = :sessionId AND user_id = :userId
```

---

## 5. Gamification Distance Logic

Final form of `calculateTotalDistanceKm` in `GamificationCommandService`:

```java
private double calculateTotalDistanceKm(WalkSession session) {
    String sid = session.getSessionId();
    String idA = session.getUserIdA();
    String idB = session.getUserIdB();

    int countA = trackingChunkRepository.countChunks(sid, idA);
    int countB = trackingChunkRepository.countChunks(sid, idB);
    // Use the participant with more chunks (better GPS coverage). Tiebreak: user_id_a.
    String canonicalUserId = (countB > countA) ? idB : idA;

    List<String> polylines = trackingChunkRepository.findPolylinesBySessionAndUser(sid, canonicalUserId);
    if (polylines.isEmpty()) return 0.0;
    return polylines.stream()
            .mapToDouble(PolylineDecoder::calculateDistanceKm)
            .sum();
}
```

Call site in `rewardBothParticipants` updated from:
```java
double distanceKm = calculateTotalDistanceKm(session.getSessionId());
```
to:
```java
double distanceKm = calculateTotalDistanceKm(session);
```

---

## 6. `compileJava` Output

```
> Task :backend:compileJava

BUILD SUCCESSFUL in 31s
1 actionable task: 1 executed
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.1.0/userguide/configuration_cache_enabling.html
```

---

## 7. Gaps Closed

| Gap | Description | Status |
|-----|-------------|--------|
| G-1 | Add `user_id` to `session_point_chunks`, update unique constraint | **CLOSED** (Phase 0) |
| G-2 | Update `TrackingChunkRepository` interface and JDBC impl to scope by `userId` | **CLOSED** |
| G-3 | Pass `callerId` to both chunk repo calls in `TrackingCommandService.syncRoutePoints` | **CLOSED** |
| G-4 | Add `countChunks(sessionId, userId)` to repository | **CLOSED** |
| G-5 | Fix `calculateTotalDistanceKm` with fallback-user selection | **CLOSED** |

---

## 8. Open Issues / Deviations

| # | Description | Severity |
|---|-------------|----------|
| 1 | `findPolylinesBySessionId(String sessionId)` retained in interface and JDBC impl — plan Step 1.1 notes removal only "if there are other callers"; the method is retained because Phase 4 (History flow / G-14) will use it for dual-path rendering. No deviation from plan intent. | None |
| 2 | `countChunks` null guard: JDBC `COUNT(*)` should never return null, but a defensive `count != null ? count : 0` guard is in place matching the pattern used in `nextChunkIndex`. | None |
