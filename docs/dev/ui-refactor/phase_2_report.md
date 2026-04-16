# Phase 2 Report — Data Layer: API Services
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 6.3 (complete), 7.1 (history), 7.2 (route), 7.3 (report)

---

## File Modified

### `SessionApiService.java`
**Path:** `frontend/src/main/java/com/walkmate/data/datasource/remote/api/SessionApiService.java`

### Imports Added
```java
import com.walkmate.data.datasource.remote.dto.request.walksession.ReportSessionRequest;
import com.walkmate.data.datasource.remote.dto.response.session.SessionRouteResponse;
```

### Methods Added (copied verbatim from the file after editing)

```java
// UC-19 — Complete an active walk session
@POST("api/v1/sessions/{sessionId}/complete")
Call<ApiResponse<WalkSessionResponse>> completeSession(
        @Path("sessionId") String sessionId);

// UC-22 — Fetch terminal session history list
@GET("api/v1/sessions/history")
Call<ApiResponse<List<WalkSessionResponse>>> getSessionHistory();

// UC-23 — Fetch GPS route for a completed session
@GET("api/v1/sessions/{sessionId}/route")
Call<ApiResponse<SessionRouteResponse>> getSessionRoute(
        @Path("sessionId") String sessionId);

// UC-25 — Submit an incident report
@POST("api/v1/sessions/{sessionId}/report")
Call<ApiResponse<Void>> reportSession(
        @Path("sessionId") String sessionId,
        @Body ReportSessionRequest body);
```

### Existing Methods — Unchanged
All five pre-existing methods were not touched:

| Method | Signature |
|---|---|
| `getActiveSessions` | `@GET("api/v1/sessions/active") Call<ApiResponse<List<WalkSessionResponse>>> getActiveSessions()` |
| `activateSession` | `@POST("api/v1/sessions/{sessionId}/activate") Call<ApiResponse<WalkSessionResponse>> activateSession(@Path("sessionId") String sessionId)` |
| `cancelSession` | `@POST("api/v1/sessions/{sessionId}/cancel") Call<ApiResponse<Void>> cancelSession(@Path("sessionId") String sessionId, @Body CancelWalkSessionRequest body)` |
| `abortSession` | `@POST("api/v1/sessions/{sessionId}/abort") Call<ApiResponse<Void>> abortSession(@Path("sessionId") String sessionId, @Body AbortWalkSessionRequest body)` |

---

## Full Import Paths for New DTO Types

| Type | Full Package |
|---|---|
| `ReportSessionRequest` | `com.walkmate.data.datasource.remote.dto.request.walksession.ReportSessionRequest` |
| `SessionRouteResponse` | `com.walkmate.data.datasource.remote.dto.response.session.SessionRouteResponse` |

Both types were created in Phase 1.

---

## Notes for Phase 3 (Repository Layer)

- `SessionRepositoryImpl` (or equivalent) must now implement calls to `completeSession`, `getSessionHistory`, `getSessionRoute`, and `reportSession`.
- `completeSession` takes no request body — the endpoint uses an empty POST. The method signature accepts only `sessionId`.
- `TrackingRepositoryImpl.triggerBatchSync()` **still will not compile** (Phase 1 breaking change — `getSyncedCount()` removed). This must be resolved in Phase 3.
