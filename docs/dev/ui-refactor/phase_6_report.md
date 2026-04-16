# Phase 6 Report — Repository & Service Implementations
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 5.6 (GPS interval), 6.3 (complete endpoint), 7.1 (history), 7.2 (route), 7.3 (report), 6.4 (acknowledged_ids terminal detection), periodic sync (30-second flush)

---

## WalkSessionRepositoryImpl — Four New Methods

All four follow the exact `executor.execute / try / resp.isSuccessful / ErrorParser / IOException` pattern from `getActiveSessions()`.

| Method | API call | Mapper |
|---|---|---|
| `completeSession` | `apiService.completeSession(sessionId)` | `WalkSessionMapper.toDomain(data, callerId)` |
| `getSessionHistory` | `apiService.getSessionHistory()` | `SessionSummaryMapper.toDomainList(data, callerId)` |
| `getSessionRoute` | `apiService.getSessionRoute(sessionId)` | `SessionRouteMapper.toDomain(data)` |
| `reportSession` | `apiService.reportSession(sessionId, new ReportSessionRequest(...))` | `callback.onSuccess(null)` |

New imports added to `WalkSessionRepositoryImpl`:
- `ReportSessionRequest`, `SessionRouteResponse`
- `SessionRouteMapper`, `SessionSummaryMapper`
- `SessionRoute`, `SessionSummary`

---

## WalkIntentRepositoryImpl — `description` Parameter

`createIntent()` signature updated to include `String description` as parameter #10:
```java
public void createIntent(String hotspotId, String date, float timeStart, float timeEnd,
                         int ageMin, int ageMax, List<String> tags,
                         boolean isPrivate, String invitedFriendId,
                         String description,
                         DomainCallback<WalkIntent> callback)
```
`description` is passed through to `new CreateWalkIntentRequest(...)` (constructor already supported it from Phase 2).

**Note:** `findMatch()` already had the 204 handler (lines 111–115) — no change needed.

---

## WalkProposalRepositoryImpl — `acceptProposal()` Fixed

- Callback type: `DomainCallback<WalkSession>` → `DomainCallback<WalkProposal>`
- Body simplified: always returns `WalkProposalMapper.toDomain(resp.body().getData())`; the old session-id branch and `WalkProposalMapper.toSession()` call removed
- Stale javadoc and `import com.walkmate.domain.walksession.WalkSession` removed

---

## MatchesViewModel — `acceptProposal()` Call Site Updated

`ui/matches/MatchesViewModel.java:261`:
- `new DomainCallback<WalkSession>()` → `new DomainCallback<WalkProposal>()`
- `public void onSuccess(WalkSession result)` → `public void onSuccess(WalkProposal result)`

(`WalkProposal` was already imported; `WalkSession` import retained — still used for session actions.)

---

## CreateIntentViewModel — `createIntent()` Call Site Fixed

`ui/explore/createintent/CreateIntentViewModel.java:34`:
Passed `null` for `description` to preserve existing behavior:
```java
intentRepository.createIntent(hotspotId, date, timeStart, timeEnd, ageMin, ageMax, tags,
        isPrivate, invitedFriendId, null,
        new DomainCallback<WalkIntent>() { ... });
```

---

## TrackingRepositoryImpl — `triggerPeriodicSync()` + Terminal Detection

### New public interface (inner)
```java
public interface SessionEndedListener {
    void onSessionEndedRemotely(String errorCode);
}
private SessionEndedListener sessionEndedListener;
public void setSessionEndedListener(SessionEndedListener l) { ... }
```

### `triggerPeriodicSync(String sessionId)`
Bypasses `BATCH_SIZE_THRESHOLD`. Wraps in `executor.execute`, fetches all unsynced points, delegates to `pushRoutePoints`. On `SESSION_TERMINAL|*` error, fires `sessionEndedListener`.

### Terminal-state detection in `pushRoutePoints()`
Before the existing 422/default branches:
```java
if ("SESSION_NOT_ACTIVE".equals(errorCode) || "SESSION_NOT_FOUND".equals(errorCode)) {
    callback.onError(new Exception("SESSION_TERMINAL|" + errorCode));
}
```

---

## SessionTrackingService — 30-Second Periodic Sync

New imports: `ScheduledExecutorService`, `ScheduledFuture`, `TimeUnit`.

New fields:
```java
private final ScheduledExecutorService syncScheduler =
        Executors.newSingleThreadScheduledExecutor();
private ScheduledFuture<?> periodicSyncFuture;
```

`startSession()` — scheduler starts after filter reset:
```java
periodicSyncFuture = syncScheduler.scheduleAtFixedRate(
        () -> repository.triggerPeriodicSync(sessionId),
        30L, 30L, TimeUnit.SECONDS);
```

`stopTracking()` — scheduler cancelled and shut down before executor:
```java
if (periodicSyncFuture != null) { periodicSyncFuture.cancel(false); }
syncScheduler.shutdown();
executor.shutdown();
```

---

## WalkTrackerService — GPS Interval Fixed

`service/WalkTrackerService.java`:
```java
// Before
private static final long LOCATION_INTERVAL_MS = 3_000L;
// After
private static final long LOCATION_INTERVAL_MS = 5_000L;
```
