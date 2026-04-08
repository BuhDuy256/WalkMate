# WalkMate Frontend — Execution Playbook
**Date:** 2026-04-09
**Based on:** `gap_analysis.md` + `implementation_plan.md` + `docs/single-source-of-truth/architecture/Frontend_VI.md`
**Architecture constraints:** Pure Java · MVVM · LiveData · Room · Manual DI · No Coroutines · No RxJava
**Strategy:** Bottom-up — Data layer first, Domain second, UI last. Each phase is atomic and independently verifiable.

---

## How to Use This Playbook

1. Execute phases **in order** — later phases depend on types created in earlier ones.
2. Each prompt is **copy-pasteable** into a new AI session. It is fully self-contained.
3. Every phase begins by reading the **previous phase's report** (document chaining).
4. Every phase ends by writing a **phase report** to `docs/dev/ui-refactor/`.
5. The ACKG MCP tool (`mcp__ackg-walkmate__get_file_outline`, `mcp__ackg-walkmate__search_symbols`, `mcp__ackg-walkmate__find_usages`, `mcp__ackg-walkmate__get_definition`) **must be used** to read every file before modifying it.
6. **Never** violate the non-negotiable rules repeated in each prompt.

---

## Non-Negotiable Architecture Rules

These apply to every phase without exception:

| Rule | Enforcement |
|---|---|
| Pure Java only | Zero Kotlin files, zero `suspend`, zero `Flow`, zero RxJava imports |
| Async model | `ExecutorService` for background, `Handler`/`postDelayed` for polling, `LiveData` for UI updates |
| MVVM discipline | Fragment/Activity observes LiveData; zero business logic in View layer |
| Manual DI | Service Locator via `WalkMateApplication` singleton getters; no Hilt, no Dagger |
| DTO boundary | Network DTOs (`...Response`, `...Request`) never cross into the domain layer — always map via a Mapper class |
| Custom Views | Any UI pattern reused in ≥3 places **must** be a Custom View (Frontend_VI.md §8.2) |
| No speculative code | Only implement what the plan explicitly requires; no extra abstractions, no future-proofing |

---

## Phase Map

| Phase | Name | Key Deliverable |
|---|---|---|
| 0 | Custom Views Pre-Work | `CountdownTimerView`, `ActivationWindowButtonView` |
| 1 | Data Layer — DTOs | Fix/create 5 DTO classes |
| 2 | Data Layer — API Services | Add 4 endpoints to `SessionApiService` |
| 3 | Data Layer — Mappers | Fix 3 mappers; create 2 new mappers |
| 4 | Domain Models | Enrich 3 models; create 4 new domain classes |
| 5 | Repository Interfaces | Add method signatures to 3 interfaces |
| 6 | Repository & Service Implementations | Implement new methods in 4 impl classes + `SessionTrackingService` |
| 7 | Walk Intent Feature | `FindingAdapter`, `FindingFragment`, `MatchesViewModel` |
| 8 | Proposal Negotiation Feature | `ProposalAdapter`, Case A/B logic, partner enrichment |
| 9 | Session Lifecycle Feature | `SessionAdapter`, `SessionFragment`, `TrackingViewModel` |
| 10 | GPS Path Tracking Feature | Periodic sync, `WalkTrackerService` interval, terminal-state stop |
| 11 | Home Page Feature | Replace all hardcoded values with real repository calls |
| 12 | Profile Page Feature | Badges, stats, reviews, fix `isOnline`, edit/avatar screens |
| 13 | Post-Session Features | History, Route Replay, Summary, Review, Report |
| 14 | DI & Factory Updates | `WalkMateApplication` + all ViewModelFactory classes |
| 15 | WebSocket Chat Integration | `ChatRepository`, `ChatViewModel`, `ChatFragment` (Gap 4.6) |

---

## Phase 0: Custom Views Pre-Work

**Objective:** Create the two custom views that are required by multiple downstream phases. These must exist before any adapter or fragment work begins.
**Gaps Closed:** 3.2 (Intent countdown), 4.4 (Proposal countdown), 5.2 (Activation window), 6.1 (Session card)
**Depends On:** Nothing (this is the starting point).

---

### Prompt for Phase 0

```
## Phase 0: Custom Views Pre-Work

## Architecture Rules (Non-Negotiable)
- Pure Java only — zero Kotlin, zero Coroutines, zero RxJava
- Custom Views must extend existing Android SDK view classes — no new dependencies
- Internal timer: android.os.CountDownTimer (built-in SDK, not a library)
- Internal scheduling: android.os.Handler + postDelayed (built-in SDK)
- No business logic in Views — Views only render state that is passed in via public API methods

## Step 1: Read the Reference Architecture
Use mcp__ackg-walkmate__search_symbols to search for "WalkMateButton" to find an existing custom
view in the codebase and understand the project's conventions for custom views.
Use mcp__ackg-walkmate__search_symbols to find "attrs.xml" and read it to see existing
declare-styleable entries.

## Step 2: Create CountdownTimerView

**File to create:** `frontend/src/main/java/com/walkmate/core/designsystem/view/CountdownTimerView.java`
**Extends:** `androidx.appcompat.widget.AppCompatTextView`
**No layout file** — this view is text-only.

Public API:
```java
// Parse ISO-8601 instant, compute remaining ms, start internal CountDownTimer.
void startCountdown(String expiresAtIso);

// Provide pre-computed epoch ms for callers that already parsed the timestamp.
void startCountdown(long expiresAtEpochMs);

// Cancel the internal timer. MUST be called from adapter's onViewRecycled().
void cancelCountdown();

// Called by Fragment to refresh list when a timer hits zero.
void setOnExpiredListener(OnExpiredListener listener);

public interface OnExpiredListener { void onExpired(); }
```

Internal implementation rules:
- Use `android.os.CountDownTimer` — no third-party timer.
- In `startCountdown()`: cancel any running timer first (guard against re-bind on recycle).
  Compute `remaining = expiresAtEpochMs - System.currentTimeMillis()`.
  If remaining <= 0: set text "Expired", fire OnExpiredListener. Return.
  Otherwise: create `new CountDownTimer(remaining, 1_000L)`.
- `onTick(long ms)`: `setText(String.format("%02d:%02d", ms / 60000, (ms % 60000) / 1000))`.
  If ms <= urgentThresholdMs: `setTextColor(urgentColor)`. Else: `setTextColor(normalColor)`.
- `onFinish()`: setText("Expired"), fire OnExpiredListener.
- Override `onDetachedFromWindow()`: cancel timer. This prevents leaks in recycled RecyclerView cells.
- Read urgentThresholdSec, urgentColor, normalColor from `attrs.xml` declare-styleable (see below).

Add to `res/values/attrs.xml`:
```xml
<declare-styleable name="CountdownTimerView">
    <attr name="wm_urgentThresholdSec" format="integer" />
    <attr name="wm_urgentColor"        format="color"   />
    <attr name="wm_normalColor"        format="color"   />
</declare-styleable>
```

## Step 3: Create ActivationWindowButtonView

**File to create:** `frontend/src/main/java/com/walkmate/core/designsystem/view/ActivationWindowButtonView.java`
**Extends:** `LinearLayout` (vertical orientation)
**Layout file:** `res/layout/view_activation_window_button.xml` with a `<merge>` root containing
a `TextView` (status label) and a `WalkMateButton` (arrive button).

Public API:
```java
// Bind with session scheduled start time and the arrive click handler.
// Internally computes open = scheduledStart - 10 min, close = scheduledStart + 15 min.
// Enables/disables the inner button. Starts 60-second re-evaluation loop.
void bind(String scheduledStartIso, View.OnClickListener onArrivedClick);

// Cancel the re-evaluation Handler. MUST be called from onViewRecycled / onDestroyView.
void release();
```

Internal implementation rules:
- Parse scheduledStartIso via `Instant.parse(scheduledStartIso).toEpochMilli()`.
- `WINDOW_OPEN_MS  = epochMs - (10 * 60_000L)` — 10 minutes before.
- `WINDOW_CLOSE_MS = epochMs + (15 * 60_000L)` — 15 minutes after.
- `boolean inWindow = now >= WINDOW_OPEN_MS && now <= WINDOW_CLOSE_MS`.
- Set button enabled/disabled + label text ("Arrive" vs "Not yet open" vs "Window closed") immediately.
- Post re-evaluation via `new Handler(Looper.getMainLooper()).postDelayed(this::evaluate, 60_000L)`.
- `release()`: remove all pending callbacks from the Handler.
- After WINDOW_CLOSE_MS, stop re-evaluation — call `release()` internally.

Add to `res/values/attrs.xml`:
```xml
<declare-styleable name="ActivationWindowButtonView">
    <attr name="wm_arriveLabel"  format="string" />
    <attr name="wm_waitingLabel" format="string" />
</declare-styleable>
```

## Step 4: Output Phase Report
Write `docs/dev/ui-refactor/phase_0_report.md` with:
- Full package paths of files created
- Exact public API of each custom view (method signatures)
- Any deviation from the spec above and the reason
- The attrs.xml entry added (for next phase reference)
```

---

## Phase 1: Data Layer — DTOs

**Objective:** Fix existing DTO classes and create missing ones so the data layer correctly represents the backend contract.
**Gaps Closed:** 3.1 (`description` missing from request), 5.1 (missing proposal fields), 6.4 (`syncedCount` wrong field), 7.1 (history/route/report DTOs missing)
**Depends On:** Phase 0 report exists (read it first for context chain).

---

### Prompt for Phase 1

```
## Phase 1: Data Layer — DTOs

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_0_report.md before starting.

## Architecture Rules (Non-Negotiable)
- DTOs live in data/datasource/remote/dto/ — they never leak into domain or UI layers
- All JSON field names use @SerializedName("snake_case") annotation
- Every field needs a getter; constructors are full-arg
- No logic in DTO classes — pure data containers only

## Step 1: Read Existing DTOs with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on these files before modifying any:
- `data/datasource/remote/dto/request/walkintent/CreateWalkIntentRequest.java`
- `data/datasource/remote/dto/response/tracking/PushRoutePointsResponse.java`
- `data/datasource/remote/dto/response/walkproposal/WalkProposalResponse.java`
- `data/datasource/remote/dto/response/walksession/WalkSessionResponse.java`
Use mcp__ackg-walkmate__search_symbols to find the dto/request/walksession/ package structure.

## Step 2: Modify CreateWalkIntentRequest
Add field: `@SerializedName("description") private String description`
Add to full constructor. Add getter `getDescription()`.
Keep all existing fields and constructor parameters intact.

## Step 3: Modify PushRoutePointsResponse
REMOVE the field `private int syncedCount` and its getter.
ADD: `@SerializedName("acknowledged_ids") private List<Long> acknowledgedIds`
ADD getter: `List<Long> getAcknowledgedIds()`
This is a breaking change — the mapper will be fixed in Phase 3.

## Step 4: Create CompleteWalkSessionRequest
**File:** `data/datasource/remote/dto/request/walksession/CompleteWalkSessionRequest.java`
Empty body POJO. No fields. Single no-arg constructor. Backend requires POST with empty body.

## Step 5: Create ReportSessionRequest
**File:** `data/datasource/remote/dto/request/walksession/ReportSessionRequest.java`
Fields:
- `@SerializedName("reported_user_id") private String reportedUserId`
- `@SerializedName("reason") private String reason`
- `@SerializedName("evidence_url") private String evidenceUrl` (nullable)
Full constructor + getters.

## Step 6: Create SessionRouteResponse
**File:** `data/datasource/remote/dto/response/session/SessionRouteResponse.java`
Fields:
- `@SerializedName("user_a_polylines") private List<String> userAPolylines`
- `@SerializedName("user_b_polylines") private List<String> userBPolylines`
- `@SerializedName("total_distance_km") private double totalDistanceKm`
- `@SerializedName("duration_minutes") private int durationMinutes`
Full constructor + getters.

## Step 7: Output Phase Report
Write `docs/dev/ui-refactor/phase_1_report.md` with:
- List of files modified with the exact fields changed
- List of files created with full package paths
- Note the PushRoutePointsResponse breaking change so Phase 3 knows to fix the mapper
```

---

## Phase 2: Data Layer — API Services

**Objective:** Add the four missing backend endpoints to `SessionApiService` so the repository layer can call complete, history, route, and report operations.
**Gaps Closed:** 6.3 (complete missing), 7.1 (history missing), 7.2 (route missing), 7.3 (report missing)
**Depends On:** Phase 1 — `SessionRouteResponse`, `CompleteWalkSessionRequest`, `ReportSessionRequest` must exist.

---

### Prompt for Phase 2

```
## Phase 2: Data Layer — API Services

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_1_report.md before starting.

## Architecture Rules (Non-Negotiable)
- All API calls are Retrofit 2 interface methods
- Return types are always Call<ApiResponse<T>> where T is the response DTO
- Use @Path for URL path params, @Body for request bodies, no @Query unless spec requires it
- Never change existing method signatures in ApiService interfaces

## Step 1: Read SessionApiService with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `data/datasource/remote/api/SessionApiService.java`
Use mcp__ackg-walkmate__search_symbols to find `ApiResponse` to understand the response wrapper.
Use mcp__ackg-walkmate__search_symbols to find `WalkSessionResponse` to confirm it exists in
the correct package (needed as return type for completeSession and getSessionHistory).

## Step 2: Add Four Methods to SessionApiService

Add the following four method signatures. Do not modify any existing methods.

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

Import `SessionRouteResponse` and `ReportSessionRequest` from the packages created in Phase 1.

## Step 3: Output Phase Report
Write `docs/dev/ui-refactor/phase_2_report.md` with:
- Exact method signatures added (copy-paste from the file after editing)
- Confirmation that no existing methods were altered
- Full import paths for the new DTO types
```

---

## Phase 3: Data Layer — Mappers

**Objective:** Fix three existing mappers to stop dropping fields, and create two new mappers for the new domain models.
**Gaps Closed:** 3.2 (intent expiresAt dropped), 4.1 (proposal fields dropped), 6.1 (session timestamps dropped), 6.4 (acknowledged_ids parsing)
**Depends On:** Phase 1 (DTO fields now exist), Phase 4 will enrich domain models — but mapper code can reference fields now and the domain model will add them in Phase 4. Write mappers referencing fields that will exist after Phase 4; mark any forward-reference with a TODO comment if needed.

---

### Prompt for Phase 3

```
## Phase 3: Data Layer — Mappers

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_2_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Mappers are pure static utility classes — no constructors, no state
- toDomain() always takes a DTO and returns a domain model
- toDomainList() is a convenience that calls toDomain() in a loop
- No API calls, no Room queries inside mappers
- If a domain field does not exist yet (will be added in Phase 4), add a TODO and pass null/0
  as a placeholder — do not skip mapping the DTO field

## Step 1: Read All Mappers with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on each of these before editing:
- `data/mapper/WalkIntentMapper.java`
- `data/mapper/WalkProposalMapper.java`
- `data/mapper/WalkSessionMapper.java`
- `data/repository/TrackingRepositoryImpl.java` (to understand how PushRoutePointsResponse is used)

Use mcp__ackg-walkmate__search_symbols to find `WalkIntent`, `WalkProposal`, `WalkSession`
domain model classes to understand current constructor signatures.

## Step 2: Fix WalkIntentMapper.toDomain()
Map the following fields that are currently dropped:
- `response.getExpiresAt()` → `expiresAt` parameter
- `response.getDescription()` → `description` parameter (if field exists on DTO; else pass null)

DELETE the broken `toRequest()` static factory method entirely. The repository builds
CreateWalkIntentRequest directly and must not use this method.

## Step 3: Fix WalkProposalMapper.toDomain()
Map the following fields that are currently dropped:
- `response.getExpiresAt()` → `expiresAt`
- `response.getProposedLat()` (or equivalent getter) → `meetingLat`
- `response.getProposedLng()` (or equivalent getter) → `meetingLng`
- `response.getMyAcceptanceStatus()` → `myAcceptanceStatus`
- `response.getSessionId()` → `sessionId`

For partner fields (name, avatar): pass the `matchedUserId` through as-is; enrichment happens
in the ViewModel (Phase 8). Do NOT call any API from inside the mapper.

Use mcp__ackg-walkmate__get_file_outline on WalkProposalResponse.java to confirm exact getter
names before writing the mapper code.

## Step 4: Fix WalkSessionMapper.toDomain()
Change the method signature to: `public static WalkSession toDomain(WalkSessionResponse response, String callerId)`

Map all currently-dropped fields:
- `response.getScheduledEnd()` → `scheduledEnd`
- `response.getStartedAt()` → `startedAt`
- `response.getEndedAt()` → `endedAt`
- `response.getUserAActivatedAt()` → `userAActivatedAt`
- `response.getUserBActivatedAt()` → `userBActivatedAt`
- `response.isReviewed()` → `isReviewed`
- Compute `isCallerUserA`: compare `callerId` with the `userAId` field in the response.

Use mcp__ackg-walkmate__find_usages on WalkSessionMapper to find all call sites that currently
call `toDomain(response)` — update each to pass `callerId`. If the call site does not have
callerId available yet, pass an empty string "" as a placeholder with a TODO comment.

## Step 5: Fix TrackingRepositoryImpl — Use acknowledged_ids
Use mcp__ackg-walkmate__get_file_outline on TrackingRepositoryImpl.java.
Find the pushRoutePoints() method and the place where PushRoutePointsResponse is consumed.
Replace `response.body().getData().getSyncedCount()` (or equivalent) with:
```java
List<Long> acked = response.body().getData().getAcknowledgedIds();
if (acked != null && !acked.isEmpty()) {
    dao.markAsSynced(acked); // pass the list of acknowledged IDs
}
```
If `dao.markAsSynced()` does not accept a `List<Long>`, use
mcp__ackg-walkmate__search_symbols to find its current signature and adapt accordingly.

## Step 6: Create SessionSummaryMapper
**File:** `data/mapper/SessionSummaryMapper.java`
Maps `WalkSessionResponse` → `SessionSummary` (domain model, created in Phase 4).
Only map these 7 fields: sessionId, status, partnerId (from partnerUserId or equivalent),
scheduledStart, totalDistanceKm (0.0 if not in response), durationMinutes (0 if not in response),
isReviewed.

## Step 7: Create SessionRouteMapper
**File:** `data/mapper/SessionRouteMapper.java`
Maps `SessionRouteResponse` → `SessionRoute` (domain model, created in Phase 4).
Simple field copy: userAPolylines, userBPolylines, totalDistanceKm, durationMinutes.

## Step 8: Output Phase Report
Write `docs/dev/ui-refactor/phase_3_report.md` with:
- Each mapper change described with before/after field mapping table
- Exact new toDomain() signature for WalkSessionMapper (important for Phase 6 call sites)
- List of call sites updated for WalkSessionMapper signature change
- Any fields that could not be mapped yet (waiting on Phase 4 domain model additions)
```

---

## Phase 4: Domain Models

**Objective:** Enrich the three core domain models with fields that were being dropped, add business logic helpers, and create four new domain classes.
**Gaps Closed:** 3.2, 3.3 (WalkIntent fields), 4.1 (WalkProposal fields), 5.4 (WalkSession timestamps + constants), 7.1 (SessionSummary), 7.2 (SessionRoute)
**Depends On:** Phase 3 (mappers reference domain constructors — domain models must be updated to match).

---

### Prompt for Phase 4

```
## Phase 4: Domain Models

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_3_report.md before starting. Pay attention to any
fields the mapper noted as "waiting on Phase 4" — those must be added here.

## Architecture Rules (Non-Negotiable)
- Domain models are immutable — all fields are final, set only via constructor
- No Android imports in domain classes (no Context, no View, no LiveData)
- No Retrofit/Room annotations in domain classes
- Helper methods are pure boolean/computation — no side effects
- Enums live in the same package as the domain model they belong to

## Step 1: Read Domain Models with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `domain/walkintent/WalkIntent.java`
- `domain/walkproposal/WalkProposal.java`
- `domain/walksession/WalkSession.java`
Use mcp__ackg-walkmate__search_symbols to find `DomainCallback` to understand the callback interface.

## Step 2: Enrich WalkIntent
Add to constructor and class body (keep ALL existing fields):
- `private final String expiresAt;` — ISO-8601 string, nullable
- `private final String description;` — nullable

Add getters: `getExpiresAt()`, `getDescription()`

Add helper methods (no Android dependencies):
```java
public boolean isOpen() { return "OPEN".equals(status); }
public boolean isMatching() { return "MATCHING".equals(status); }
```

Update constructor signature. Update any builder/factory if one exists (check with
mcp__ackg-walkmate__find_usages on the WalkIntent constructor).

## Step 3: Enrich WalkProposal
Add to constructor and class body (keep ALL existing fields):
- `private final String expiresAt;` — ISO-8601 string
- `private final double meetingLat;`
- `private final double meetingLng;`
- `private final String myAcceptanceStatus;` — "ACCEPTED" or null
- `private final String sessionId;` — null until CONFIRMED

Add getters for all 5 new fields.

Add helper methods:
```java
public boolean isAcceptedByMe() { return "ACCEPTED".equals(myAcceptanceStatus); }
public boolean isConfirmed() { return Status.CONFIRMED == status && sessionId != null; }
```

## Step 4: Enrich WalkSession
Add to constructor and class body (keep ALL existing fields):
- `private final String scheduledEnd;`
- `private final String startedAt;`
- `private final String endedAt;`
- `private final String userAActivatedAt;`
- `private final String userBActivatedAt;`
- `private final boolean isReviewed;`
- `private final boolean isCallerUserA;`

Add public static constants:
```java
public static final int ACTIVATION_WINDOW_BEFORE_MINUTES = 10;
public static final int ACTIVATION_WINDOW_AFTER_MINUTES  = 15;
public static final int MINIMUM_WALK_DURATION_MINUTES    = 5;
```

Add helper methods:
```java
public boolean hasCallerActivated() {
    return isCallerUserA ? userAActivatedAt != null : userBActivatedAt != null;
}
public boolean hasBothActivated() {
    return userAActivatedAt != null && userBActivatedAt != null;
}
public boolean canComplete() {
    if (startedAt == null) return false;
    long startMs = Instant.parse(startedAt).toEpochMilli();
    long elapsed = System.currentTimeMillis() - startMs;
    return elapsed >= MINIMUM_WALK_DURATION_MINUTES * 60_000L;
}
```

## Step 5: Create AbortReason Enum
**File:** `domain/walksession/AbortReason.java`
```java
public enum AbortReason {
    SAFETY_CONCERN, EMERGENCY, PARTNER_MISCONDUCT, OTHER;
    public String toApiValue() { return name(); }
}
```

## Step 6: Create SessionRoute Domain Model
**File:** `domain/walksession/SessionRoute.java`
Fields: `List<String> userAPolylines`, `List<String> userBPolylines`,
`double totalDistanceKm`, `int durationMinutes`. Full constructor + getters.

## Step 7: Create SessionSummary Domain Model
**File:** `domain/walksession/SessionSummary.java`
Fields: `String sessionId`, `WalkSession.Status status`, `String partnerId`,
`String scheduledStart`, `double totalDistanceKm`, `int durationMinutes`, `boolean isReviewed`.
Full constructor + getters. No activation/GPS fields — lightweight list-item model only.

## Step 8: Add WalkState.FINISHING
Use mcp__ackg-walkmate__search_symbols to find `WalkState.java`.
Add `FINISHING` between `ACTIVE` and `FINISHED` in the enum body.
This state represents "API call in progress" — the user tapped Complete but we are awaiting response.

## Step 9: Output Phase Report
Write `docs/dev/ui-refactor/phase_4_report.md` with:
- Updated constructor signatures for WalkIntent, WalkProposal, WalkSession (full param list)
- List of new files created with package paths
- Confirmation that WalkState.FINISHING was added
- Any existing call sites that broke due to constructor changes and how they were updated
```

---

## Phase 5: Repository Interfaces

**Objective:** Add method signatures to three repository interfaces so the domain layer contract is complete before implementing the bodies.
**Gaps Closed:** 2.7 (session complete/history/route/report), 2.8 (intent description param), 2.9 (periodic sync)
**Depends On:** Phase 4 — `SessionSummary`, `SessionRoute`, `AbortReason` must exist as types.

---

### Prompt for Phase 5

```
## Phase 5: Repository Interfaces

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_4_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Repository interfaces live in the domain layer — no Android, no Retrofit, no Room imports
- All async methods use DomainCallback<T> — no return values from async methods
- Callback is always the last parameter
- Interface changes are additive — never remove or rename existing methods

## Step 1: Read Repository Interfaces with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `domain/walksession/WalkSessionRepository.java`
- `domain/walkintent/WalkIntentRepository.java`
- `domain/tracking/TrackingRepository.java`
- `domain/walkproposal/WalkProposalRepository.java`
Use mcp__ackg-walkmate__search_symbols to find `DomainCallback` and confirm its generic signature.

## Step 2: Expand WalkSessionRepository
Add these four method signatures:
```java
void completeSession(String sessionId, DomainCallback<WalkSession> callback);
void getSessionHistory(DomainCallback<List<SessionSummary>> callback);
void getSessionRoute(String sessionId, DomainCallback<SessionRoute> callback);
void reportSession(String sessionId, String reportedUserId,
                   String reason, String evidenceUrl,
                   DomainCallback<Void> callback);
```

## Step 3: Update WalkIntentRepository
Find the existing `createIntent()` method signature. Add `String description` as a new parameter.
Insert it after the existing parameters and before the `DomainCallback` parameter. Do not
change any other method signatures.

## Step 4: Expand TrackingRepository
Add:
```java
// Triggered by the 30-second periodic scheduler. Syncs all unsynced points
// regardless of batch size threshold.
void triggerPeriodicSync(String sessionId);
```

## Step 5: Update WalkProposalRepository
Find the existing `acceptProposal()` method. Change its callback type from
`DomainCallback<WalkSession>` to `DomainCallback<WalkProposal>`.
Use mcp__ackg-walkmate__find_usages on acceptProposal to list all call sites
that will need updating in Phase 6.

## Step 6: Output Phase Report
Write `docs/dev/ui-refactor/phase_5_report.md` with:
- Exact new method signatures added to each interface
- The acceptProposal() call sites that need updating (from find_usages) — list them for Phase 6
- Updated createIntent() signature (full param list for Phase 6 reference)
```

---

## Phase 6: Repository & Service Implementations

**Objective:** Implement all new interface methods, fix the periodic sync scheduler in `SessionTrackingService`, and fix the `WalkTrackerService` GPS interval.
**Gaps Closed:** 5.6 (GPS interval wrong), 6.3 (complete endpoint), 7.1 (history), 7.2 (route), 7.3 (report), 6.4 (acknowledged_ids), periodic sync (30-second flush)
**Depends On:** Phase 5 (interfaces finalized), Phase 3 (mappers exist), Phase 2 (API service methods exist).

---

### Prompt for Phase 6

```
## Phase 6: Repository & Service Implementations

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_5_report.md before starting. The report lists:
1. All new interface methods to implement
2. The acceptProposal() call sites that need updating
3. The updated createIntent() signature

## Architecture Rules (Non-Negotiable)
- Repository impl bodies use executor.execute(() -> { ... }) for all network calls
- Error handling pattern: check resp.isSuccessful(), else parse error body and call callback.onError()
- Scheduler for time-based tasks: ScheduledExecutorService + scheduleAtFixedRate
- Never call android.os.Handler from repository or service layer

## Step 1: Read Implementation Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `data/repository/WalkSessionRepositoryImpl.java`
- `data/repository/WalkIntentRepositoryImpl.java`
- `data/repository/WalkProposalRepositoryImpl.java`
- `data/repository/TrackingRepositoryImpl.java`
- `domain/tracking/SessionTrackingService.java`
- `ui/tracking/WalkTrackerService.java`

Use mcp__ackg-walkmate__get_definition on the existing getActiveSessions() method in
WalkSessionRepositoryImpl to understand the exact executor + callback error-handling pattern
to replicate for new methods.

## Step 2: WalkSessionRepositoryImpl — Implement Four New Methods

For each method, follow the SAME pattern as getActiveSessions():
executor.execute(() -> { try { Response<ApiResponse<T>> resp = apiService.xxx().execute();
  if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
      T data = resp.body().getData();
      callback.onSuccess(mapperCall(data));
  } else { callback.onError(parseError(resp)); }
} catch (IOException e) { callback.onError(e); } });

Implement:
- `completeSession(sessionId, callback)` → apiService.completeSession(sessionId) →
  WalkSessionMapper.toDomain(data, callerId). Inject callerId from the session store or accept
  as a constructor parameter of the repository.
- `getSessionHistory(callback)` → apiService.getSessionHistory() →
  SessionSummaryMapper.toDomainList(data)
- `getSessionRoute(sessionId, callback)` → apiService.getSessionRoute(sessionId) →
  SessionRouteMapper.toDomain(data)
- `reportSession(sessionId, reportedUserId, reason, evidenceUrl, callback)` →
  new ReportSessionRequest(reportedUserId, reason, evidenceUrl) →
  apiService.reportSession(sessionId, request) → callback.onSuccess(null)

## Step 3: WalkIntentRepositoryImpl — Pass description
Use mcp__ackg-walkmate__get_file_outline on WalkIntentRepositoryImpl.java.
Find createIntent() method. Add String description parameter. Pass it when constructing
CreateWalkIntentRequest.

Find findMatch() method. Fix the 204 No Content handling:
```java
if (resp.code() == 204) {
    callback.onSuccess(null); // No match yet — not an error
    return;
}
```

## Step 4: WalkProposalRepositoryImpl — Fix acceptProposal() Return Type
Find acceptProposal(). Change callback type from DomainCallback<WalkSession> to
DomainCallback<WalkProposal>. Map the response to WalkProposal using WalkProposalMapper.toDomain().
Update every call site listed in phase_5_report.md.

## Step 5: TrackingRepositoryImpl — Add triggerPeriodicSync()
Implement the new interface method. It must bypass the BATCH_SIZE_THRESHOLD check:
```java
@Override
public void triggerPeriodicSync(String sessionId) {
    executor.execute(() -> {
        List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId);
        if (unsyncedEntities != null && !unsyncedEntities.isEmpty()) {
            List<RoutePoint> domainPoints = RoutePointMapper.toDomainList(unsyncedEntities);
            pushRoutePoints(sessionId, domainPoints, new DomainCallback<Void>() {
                @Override public void onSuccess(Void v) { /* silent success */ }
                @Override public void onError(Exception e) {
                    if (e.getMessage() != null && e.getMessage().startsWith("SESSION_TERMINAL|")) {
                        if (sessionEndedListener != null) {
                            sessionEndedListener.onSessionEndedRemotely(e.getMessage());
                        }
                    }
                }
            });
        }
    });
}
```

Also add the SessionEndedListener interface and a setter in TrackingRepositoryImpl:
```java
public interface SessionEndedListener { void onSessionEndedRemotely(String errorCode); }
private SessionEndedListener sessionEndedListener;
public void setSessionEndedListener(SessionEndedListener l) { this.sessionEndedListener = l; }
```

In pushRoutePoints() error handling, add terminal-state detection:
```java
if ("SESSION_NOT_ACTIVE".equals(errorCode) || "SESSION_NOT_FOUND".equals(errorCode)) {
    callback.onError(new Exception("SESSION_TERMINAL|" + errorCode));
}
```

## Step 6: SessionTrackingService — Add 30-Second Periodic Sync
Use mcp__ackg-walkmate__get_file_outline on SessionTrackingService.java.
Add a second scheduler field:
```java
private final ScheduledExecutorService syncScheduler =
        Executors.newSingleThreadScheduledExecutor();
private ScheduledFuture<?> periodicSyncFuture;
```

In startSession(String sessionId) — after starting the GPS tracking, add:
```java
periodicSyncFuture = syncScheduler.scheduleAtFixedRate(
        () -> repository.triggerPeriodicSync(sessionId),
        30L, 30L, TimeUnit.SECONDS);
```

In stopTracking() — before or after existing shutdown code, add:
```java
if (periodicSyncFuture != null) { periodicSyncFuture.cancel(false); }
syncScheduler.shutdown();
```

## Step 7: WalkTrackerService — Fix GPS Interval
Use mcp__ackg-walkmate__search_symbols to find LOCATION_INTERVAL_MS in WalkTrackerService.java.
Change its value from 3_000L to 5_000L (spec requires 5-second interval, not 3-second).
This is a one-line change.

## Step 8: Output Phase Report
Write `docs/dev/ui-refactor/phase_6_report.md` with:
- Confirmation each new method was implemented with the correct executor pattern
- Confirmation WalkTrackerService GPS interval is now 5_000L
- Confirmation 30-second scheduler was added to SessionTrackingService
- Confirmation 204 handling added for findMatch()
- Any call sites that were updated
```

---

## Phase 7: Walk Intent Feature

**Objective:** Wire the intent list UI to show OPEN vs MATCHING states correctly, show countdown timers on intent cards, expose "Find Match" as an action, and handle Case A / Case B match responses.
**Gaps Closed:** 3.2 (countdown timer on intents), 3.5 (MATCHING state not differentiated), 3.6 (findMatch not exposed), 3.7 (204 No Content handling)
**Depends On:** Phase 0 (CountdownTimerView exists), Phase 4 (WalkIntent helpers exist), Phase 6 (repository findMatch fixed).

---

### Prompt for Phase 7

```
## Phase 7: Walk Intent Feature

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_6_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Adapter is a pure renderer — zero business logic
- Fragment observes LiveData; it does not call repository methods directly
- CountdownTimerView.cancelCountdown() MUST be called in onViewRecycled()
- No runnable stored in Adapter — periodic refresh dispatched from Fragment via Handler

## Step 1: Read Intent UI Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/matches/finding/FindingFragment.java`
- `ui/matches/finding/FindingAdapter.java`
- `ui/matches/MatchesViewModel.java`
Use mcp__ackg-walkmate__get_definition on MatchesViewModel to understand the existing
loadAll(), cancelIntent() methods and the UiState structure.
Use mcp__ackg-walkmate__search_symbols to find the MatchesUiState (or equivalent) class.

## Step 2: Modify FindingAdapter
In the item layout (find it via mcp__ackg-walkmate__search_symbols searching for the layout
resource reference in FindingAdapter), add:
- A CountdownTimerView widget for expires_at
- A lock ImageView (GONE by default; VISIBLE when MATCHING)
- Button state: "Find Match" (OPEN) / "View Proposal" (MATCHING)
- "Cancel" button: enabled when OPEN, gone when MATCHING

In onBindViewHolder():
```java
if (intent.isOpen()) {
    holder.btnFindMatch.setText("Find Match");
    holder.btnFindMatch.setEnabled(true);
    holder.btnCancel.setVisibility(View.VISIBLE);
    holder.lockIcon.setVisibility(View.GONE);
    holder.btnFindMatch.setOnClickListener(v -> listener.onFindMatchClicked(intent.getId()));
} else if (intent.isMatching()) {
    holder.btnFindMatch.setText("View Proposal");
    holder.btnFindMatch.setEnabled(true);
    holder.btnCancel.setVisibility(View.GONE);
    holder.lockIcon.setVisibility(View.VISIBLE);
    holder.btnFindMatch.setOnClickListener(v -> listener.onViewProposalClicked(intent.getId()));
}
holder.countdown.startCountdown(intent.getExpiresAt());
```

In onViewRecycled(holder): `holder.countdown.cancelCountdown()`

## Step 3: Modify MatchesViewModel — Add triggerMatch()
Add the following (do not modify existing methods):
```java
private final MutableLiveData<Boolean> noMatchFoundEvent = new MutableLiveData<>();

public LiveData<Boolean> getNoMatchFoundEvent() { return noMatchFoundEvent; }
public void consumeNoMatchFoundEvent() { noMatchFoundEvent.postValue(null); }

public void triggerMatch(String intentId) {
    intentRepository.findMatch(intentId, new DomainCallback<WalkProposal>() {
        @Override public void onSuccess(WalkProposal result) {
            if (result == null) {
                // Case B: 204 No Content — no match yet
                noMatchFoundEvent.postValue(true);
            } else {
                // Case A: match found, proposal created
                loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_PROPOSAL));
            }
        }
        @Override public void onError(Exception e) { postError(e.getMessage()); }
    });
}
```

Use mcp__ackg-walkmate__search_symbols to find MatchesPagerAdapter.TAB_PROPOSAL constant
and confirm it exists (or the equivalent tab index for the proposal pane).

## Step 4: Modify FindingFragment
In onViewCreated(), observe noMatchFoundEvent:
```java
matchesViewModel.getNoMatchFoundEvent().observe(getViewLifecycleOwner(), noMatch -> {
    if (noMatch != null && noMatch) {
        Toast.makeText(requireContext(),
            "No match found yet. You'll be notified when one is found!", Toast.LENGTH_LONG).show();
        matchesViewModel.consumeNoMatchFoundEvent();
    }
});
```

Add a 60-second adapter refresh loop (for countdown visual updates):
```java
private final Handler refreshHandler = new Handler(Looper.getMainLooper());
private final Runnable refreshRunnable = () -> {
    adapter.notifyDataSetChanged();
    refreshHandler.postDelayed(refreshRunnable, 60_000L);
};
@Override public void onResume()  { refreshHandler.postDelayed(refreshRunnable, 60_000L); }
@Override public void onPause()   { refreshHandler.removeCallbacks(refreshRunnable); }
```

Wire adapter callbacks: onFindMatchClicked → matchesViewModel.triggerMatch(intentId)

## Step 5: Output Phase Report
Write `docs/dev/ui-refactor/phase_7_report.md` with:
- Confirmation of adapter button state logic (OPEN vs MATCHING)
- Confirmation CountdownTimerView is cancelled in onViewRecycled
- Confirmation noMatchFoundEvent is observed in FindingFragment
- Any layout XML changes made (resource file names)
```

---

## Phase 8: Proposal Negotiation Feature

**Objective:** Show countdown timers on proposal cards, display real partner names via async enrichment, correctly handle Case A (waiting) vs Case B (confirmed) acceptance, and route navigation accordingly.
**Gaps Closed:** 4.1 (proposal fields wired to UI), 4.2 (Case A vs Case B), 4.3 (premature session navigation), 4.4 (countdown on proposals), 4.5 (partner profile enrichment)
**Depends On:** Phase 0 (CountdownTimerView), Phase 4 (WalkProposal helpers), Phase 6 (acceptProposal returns WalkProposal).

---

### Prompt for Phase 8

```
## Phase 8: Proposal Negotiation Feature

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_7_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Partner name enrichment happens in ViewModel — never in Adapter or Fragment
- In-memory user profile cache is a Map<String, UserProfile> inside MatchesViewModel (not Room)
- CountdownTimerView.cancelCountdown() MUST be called in onViewRecycled()
- Case A: show "Waiting for partner..." overlay — both Accept and Pass buttons must be hidden
- Case B: session_id is non-null — navigate to Session tab

## Step 1: Read Proposal UI Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/matches/proposal/ProposalAdapter.java`
- `ui/matches/MatchesViewModel.java`
Use mcp__ackg-walkmate__search_symbols to find UserProfileRepository to confirm its interface
and the getPublicProfile() method signature.
Use mcp__ackg-walkmate__search_symbols to find MatchesViewModelFactory to understand
its current constructor dependencies.

## Step 2: Modify ProposalAdapter
In the item layout, add:
- CountdownTimerView for expiresAt
- "Waiting for partner..." overlay View (GONE by default)
- Partner name TextView and avatar ImageView

In onBindViewHolder():
```java
holder.countdown.startCountdown(proposal.getExpiresAt());

if (proposal.isAcceptedByMe() && proposal.getStatus() == WalkProposal.Status.PENDING) {
    // Case A: I accepted, waiting for partner
    holder.waitingOverlay.setVisibility(View.VISIBLE);
    holder.btnAccept.setVisibility(View.GONE);
    holder.btnPass.setVisibility(View.GONE);
} else {
    holder.waitingOverlay.setVisibility(View.GONE);
    holder.btnAccept.setVisibility(View.VISIBLE);
    holder.btnPass.setVisibility(View.VISIBLE);
}

// Partner name: show matchedUserId as placeholder; ViewModel enriches it asynchronously
holder.partnerName.setText(proposal.getMatchedUserName() != null
    ? proposal.getMatchedUserName() : proposal.getMatchedUserId());
```

In onViewRecycled(holder): `holder.countdown.cancelCountdown()`

Set onExpiredListener to trigger matchesViewModel.loadAll() on expiry.

## Step 3: Modify MatchesViewModel — Fix acceptProposal() for Case A / Case B
Replace the existing acceptProposal() body with:
```java
proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkProposal>() {
    @Override public void onSuccess(WalkProposal result) {
        if (result.isConfirmed()) {
            // Case B: session created — reload all and navigate to Session tab
            loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION));
        } else {
            // Case A: I accepted but partner has not — update the proposal in the list
            updateProposalInPlace(result);
        }
    }
    @Override public void onError(Exception e) { postError(e.getMessage()); }
});
```

Add helper:
```java
private void updateProposalInPlace(WalkProposal updated) {
    // Get current proposals from the UiState, replace the matching entry, re-post state
}
```
Use mcp__ackg-walkmate__get_definition on the current UiState class to understand how to
rebuild and post the updated state.

## Step 4: Add Partner Name Enrichment to MatchesViewModel
Inject UserProfileRepository as a new dependency (update MatchesViewModelFactory in Phase 14).
For now, add the field and the enrichment method:

```java
private final Map<String, UserProfile> profileCache = new HashMap<>();

private void enrichProposalPartnerNames(List<WalkProposal> proposals) {
    for (WalkProposal p : proposals) {
        String uid = p.getMatchedUserId();
        if (profileCache.containsKey(uid)) {
            // already enriched — update UiState directly
        } else {
            userProfileRepository.getPublicProfile(uid, new DomainCallback<UserProfile>() {
                @Override public void onSuccess(UserProfile profile) {
                    profileCache.put(uid, profile);
                    // rebuild UiState with updated name/avatar for this proposal
                }
                @Override public void onError(Exception e) { /* fail silently */ }
            });
        }
    }
}
```

Call enrichProposalPartnerNames() after proposals are loaded in loadAll().

## Step 5: Output Phase Report
Write `docs/dev/ui-refactor/phase_8_report.md` with:
- Confirmation Case A / Case B logic is correct in acceptProposal()
- Confirmation waiting overlay hides Accept/Pass in Case A
- Confirmation partner name enrichment is called after loadAll()
- Confirmation CountdownTimerView is cancelled in onViewRecycled
- New MatchesViewModel constructor signature (for Phase 14 DI update)
```

---

## Phase 9: Session Lifecycle Feature

**Objective:** Replace the direct "Start Walk" launch with the proper activation window flow, wire `ActivationWindowButtonView` in the session adapter, add the 5-minute gate for completion, and implement Emergency Abort.
**Gaps Closed:** 5.2 (activation window not enforced), 5.3 (direct start instead of activate API), 5.4 (5-minute minimum not enforced), 5.7 (abort endpoint not wired)
**Depends On:** Phase 0 (ActivationWindowButtonView), Phase 4 (WalkSession constants + helpers), Phase 6 (completeSession, abortSession in repository).

---

### Prompt for Phase 9

```
## Phase 9: Session Lifecycle Feature

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_8_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Activation window enforcement lives in ActivationWindowButtonView — not in Adapter or Fragment
- 5-minute gate lives in TrackingViewModel — not in TrackingScreenActivity
- FINISHING is an intermediate WalkState — UI must show a loading indicator during this state
- Polling via Handler.postDelayed — cancel in onPause() / onDestroyView()

## Step 1: Read Session UI Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/matches/session/SessionFragment.java`
- `ui/matches/session/SessionAdapter.java`
- `ui/tracking/TrackingViewModel.java`
- `ui/tracking/TrackingScreenActivity.java`
- `ui/tracking/TrackingUiState.java`
Use mcp__ackg-walkmate__search_symbols to find WalkTrackerService to understand how GPS
service is started and stopped.

## Step 2: Modify SessionAdapter
Replace the "Start Walk" button in the item layout with ActivationWindowButtonView.

In onBindViewHolder():
```java
if (session.getStatus() == WalkSession.Status.PENDING) {
    holder.activationBtn.setVisibility(View.VISIBLE);
    holder.activationBtn.bind(session.getScheduledStart(),
        v -> listener.onArriveClicked(session.getSessionId()));
    holder.btnComplete.setVisibility(View.GONE);
    holder.btnAbort.setVisibility(View.GONE);
} else if (session.getStatus() == WalkSession.Status.ACTIVE) {
    holder.activationBtn.setVisibility(View.GONE);
    holder.btnComplete.setVisibility(View.VISIBLE);
    holder.btnComplete.setEnabled(session.canComplete());
    holder.btnAbort.setVisibility(View.VISIBLE);
    holder.btnAbort.setOnClickListener(v -> listener.onAbortClicked(session.getSessionId()));
}
```

In onViewRecycled(holder): `holder.activationBtn.release()`

## Step 3: Modify SessionFragment — Wire Activation Flow
Remove the existing direct TrackingScreenActivity launch tied to "Start Walk" button.

Add to MatchesViewModel a new activateSession() method and ActivationResult inner class:
```java
// In MatchesViewModel:
private final MutableLiveData<ActivationResult> activationResultEvent = new MutableLiveData<>();
public LiveData<ActivationResult> getActivationResultEvent() { return activationResultEvent; }
public void consumeActivationResult() { activationResultEvent.postValue(null); }

public void activateSession(String sessionId) {
    sessionRepository.activateSession(sessionId, new DomainCallback<WalkSession>() {
        @Override public void onSuccess(WalkSession result) {
            activationResultEvent.postValue(new ActivationResult(result, null));
            loadAll(null);
        }
        @Override public void onError(Exception e) {
            activationResultEvent.postValue(new ActivationResult(null, e.getMessage()));
        }
    });
}

static class ActivationResult {
    final WalkSession session;
    final String errorCode;
    ActivationResult(WalkSession s, String e) { session = s; errorCode = e; }
}
```

In SessionFragment.onViewCreated(), observe activationResultEvent:
```java
matchesViewModel.getActivationResultEvent().observe(getViewLifecycleOwner(), result -> {
    if (result == null) return;
    matchesViewModel.consumeActivationResult();
    if (result.session != null && result.session.getStatus() == WalkSession.Status.ACTIVE) {
        // Case B: both activated — launch tracking
        startActivity(new Intent(requireContext(), TrackingScreenActivity.class)
                .putExtra("SESSION_ID", result.session.getSessionId()));
    } else if (result.session != null) {
        // Case A: only this user activated — show waiting + start polling
        showWaitingForPartnerUi();
        startActivationPolling();
    } else if ("SESSION_ACTIVATION_WINDOW_CLOSED".equals(result.errorCode)) {
        Toast.makeText(requireContext(), "Activation window has closed.", Toast.LENGTH_SHORT).show();
    }
});
```

Add polling in SessionFragment:
```java
private final Handler pollHandler = new Handler(Looper.getMainLooper());
private final Runnable pollRunnable = () -> {
    matchesViewModel.loadAll();
    pollHandler.postDelayed(pollRunnable, 15_000L);
};
private void startActivationPolling() { pollHandler.postDelayed(pollRunnable, 15_000L); }
@Override public void onPause()  { pollHandler.removeCallbacks(pollRunnable); }
```

Also add a 30-second background refresh in onResume (for session status updates while browsing):
```java
@Override public void onResume() {
    super.onResume();
    pollHandler.postDelayed(pollRunnable, 30_000L);
}
```

## Step 4: Modify TrackingViewModel — Add requestCompleteWalk() and abortWalk()

Inject WalkSessionRepository (constructor param; factory updated in Phase 14).

Replace or supplement existing finishWalk() with:
```java
public void requestCompleteWalk() {
    if (walkStateLiveData.getValue() != WalkState.ACTIVE) return;
    long elapsed = elapsedSecondsLiveData.getValue() != null ? elapsedSecondsLiveData.getValue() : 0L;
    if (elapsed < WalkSession.MINIMUM_WALK_DURATION_MINUTES * 60L) {
        // Post remaining seconds to UiState so Activity can show countdown
        long remaining = WalkSession.MINIMUM_WALK_DURATION_MINUTES * 60L - elapsed;
        rebuildUiState(state -> state.withCompleteTooEarlySeconds(remaining));
        return;
    }
    stopTimer();
    stopGpsService();
    walkStateLiveData.setValue(WalkState.FINISHING);
    sessionRepository.completeSession(currentSessionId, new DomainCallback<WalkSession>() {
        @Override public void onSuccess(WalkSession r) {
            walkStateLiveData.postValue(WalkState.FINISHED);
        }
        @Override public void onError(Exception e) {
            walkStateLiveData.postValue(WalkState.ACTIVE);
            startTimer(); startGpsService();
            completionErrorLiveData.postValue(e.getMessage());
        }
    });
}

public void abortWalk(AbortReason reason) {
    stopTimer(); stopGpsService();
    walkStateLiveData.setValue(WalkState.FINISHING);
    sessionRepository.abortSession(currentSessionId, reason.toApiValue(), new DomainCallback<Void>() {
        @Override public void onSuccess(Void r) { walkStateLiveData.postValue(WalkState.FINISHED); }
        @Override public void onError(Exception e) {
            walkStateLiveData.postValue(WalkState.ACTIVE);
            startTimer(); startGpsService();
            completionErrorLiveData.postValue(e.getMessage());
        }
    });
}
```

Add: `private final MutableLiveData<String> completionErrorLiveData = new MutableLiveData<>();`
Add: `public LiveData<String> getCompletionError() { return completionErrorLiveData; }`

## Step 5: Modify TrackingUiState — Add New Fields
Add:
- `private final long completeTooEarlySeconds;` — 0 when complete is allowed
- `private final boolean isSaving;` — true during FINISHING state

Update constructor and getters.

## Step 6: Modify TrackingScreenActivity — Wire New Buttons
In the layout, add:
- "Complete Walk" WalkMateButton
- "Emergency Abort" WalkMateButton (red tint)

In renderState(TrackingUiState state):
- "Complete Walk" visible only when state.getWalkState() == WalkState.ACTIVE
- If state.getCompleteTooEarlySeconds() > 0: disable button; show remaining time as text
- If state.isSaving(): show loading indicator on both buttons
- "Complete Walk" click → AlertDialog confirmation → viewModel.requestCompleteWalk()
- "Emergency Abort" click → AlertDialog with radio buttons for AbortReason →
  viewModel.abortWalk(selectedReason)

Observe completionErrorLiveData → Toast on error.
On WalkState.FINISHED → start PostSessionSummaryFragment (Phase 13).

## Step 7: Output Phase Report
Write `docs/dev/ui-refactor/phase_9_report.md` with:
- Confirmation activation flow replaces direct TrackingScreenActivity launch
- Confirmation 5-minute gate is enforced in TrackingViewModel
- Confirmation polling is cancelled in onPause
- New TrackingViewModel constructor signature (for Phase 14)
- New MatchesViewModel method added (activateSession)
```

---

## Phase 10: GPS Path Tracking Feature

**Objective:** The 30-second sync scheduler was wired in Phase 6. This phase wires the terminal-session stop into `WalkTrackerService`, confirming the background service correctly halts tracking when the backend signals the session is over.
**Gaps Closed:** 5.6 (GPS interval — already fixed in Phase 6), 5.8 (no stop on terminal session state)
**Depends On:** Phase 6 (SessionEndedListener interface exists in TrackingRepositoryImpl).

---

### Prompt for Phase 10

```
## Phase 10: GPS Path Tracking Feature

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_9_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Service-to-service communication via listener interface — no static fields, no broadcasts
- stopSelf() called on the Android Service when session ends remotely
- Foreground notification updated to reflect terminal state

## Step 1: Read Service Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/tracking/WalkTrackerService.java`
- `domain/tracking/SessionTrackingService.java`
- `data/repository/TrackingRepositoryImpl.java`
Use mcp__ackg-walkmate__search_symbols to find the SessionEndedListener interface added in Phase 6.

## Step 2: Wire SessionEndedListener in WalkTrackerService
In WalkTrackerService, after constructing/obtaining TrackingRepositoryImpl (via
WalkMateApplication), set it as the SessionEndedListener:

```java
trackingRepository.setSessionEndedListener(errorCode -> {
    // Runs on executor thread — post to main thread for service ops
    new Handler(Looper.getMainLooper()).post(() -> {
        updateNotification("Your walk session has ended.");
        stopSelf();
    });
});
```

This ensures that when the backend returns SESSION_NOT_ACTIVE or SESSION_NOT_FOUND during
any periodic sync attempt, the foreground service shuts down cleanly.

## Step 3: Verify Periodic Sync Is Running
Use mcp__ackg-walkmate__get_definition on SessionTrackingService.startSession() to confirm
the scheduleAtFixedRate call added in Phase 6 is present.

If not present (Phase 6 missed it), add it now following the same pattern specified in
Phase 6 Step 6.

## Step 4: Verify GPS Interval
Use mcp__ackg-walkmate__search_symbols to find LOCATION_INTERVAL_MS in WalkTrackerService.
Confirm value is 5_000L. If it is still 3_000L, change it now.

## Step 5: Output Phase Report
Write `docs/dev/ui-refactor/phase_10_report.md` with:
- Confirmation SessionEndedListener is set in WalkTrackerService
- Confirmation GPS interval is 5_000L
- Confirmation 30-second periodic sync scheduler is running in SessionTrackingService
- Any additional changes made
```

---

## Phase 11: Home Page Feature

**Objective:** Replace all hardcoded values in `HomeViewModel` with real repository calls: hotspot count, weekly stats, quick-invite friends list, and location name.
**Gaps Closed:** 1.1 (hotspot count hardcoded), 1.2 (stats hardcoded), 1.3 (friend list hardcoded), 1.4 (no onResume refresh)
**Depends On:** Phase 6 (repositories implemented). Phase 14 will wire DI — for now, note new constructor dependencies.

---

### Prompt for Phase 11

```
## Phase 11: Home Page Feature

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_10_report.md before starting.

## Architecture Rules (Non-Negotiable)
- All hardcoded mock data removed — replaced with repository calls
- Parallel fetches use an AtomicInteger counter to detect when all complete
- Location resolution is always done on a background ExecutorService, never on main thread
- streakDays remains hardcoded (no backend endpoint yet) — add a TODO comment

## Step 1: Read Home Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/home/HomeViewModel.java`
- `ui/home/HomeFragment.java`
Use mcp__ackg-walkmate__search_symbols to find HotspotRepository, GamificationRepository,
and SocialRepository to confirm their interface method signatures.
Use mcp__ackg-walkmate__search_symbols to find buildMockInviteList and buildReadyState in
HomeViewModel to understand what is being replaced.

## Step 2: Update HomeViewModel — Inject Three New Repositories
Add constructor parameters (factories updated in Phase 14):
- `HotspotRepository hotspotRepository`
- `GamificationRepository gamificationRepository`
- `SocialRepository socialRepository`

## Step 3: Replace Hardcoded Values in loadDashboard()

**(a) Hotspot count:** Replace `nearbyHotspotCount = 5` with:
```java
hotspotRepository.getHotspots(new DomainCallback<List<Hotspot>>() {
    @Override public void onSuccess(List<Hotspot> hotspots) {
        // Update state with hotspots.size() as nearbyHotspotCount
    }
    ...
});
```

**(b) Weekly stats:** Replace `weeklyDistanceKm = 12.5`, `weeklySessionCount = 3` with:
```java
gamificationRepository.getStats(currentUserId, new DomainCallback<UserStats>() {
    @Override public void onSuccess(UserStats stats) {
        // Update state with stats.getWeeklyDistanceKm(), stats.getWeeklySessionCount()
    }
    ...
});
```

**(c) Quick-invite friends:** Replace buildMockInviteList() with:
```java
socialRepository.getFriends(new DomainCallback<List<UserProfile>>() {
    @Override public void onSuccess(List<UserProfile> friends) {
        // Map friends to QuickInviteUser list
        // Use mcp__ackg-walkmate__search_symbols to find QuickInviteUser constructor
    }
    ...
});
```

**(d) streakDays:** Add comment: // TODO: No backend endpoint for streaks yet — hardcoded.
Leave the value as-is; do not invent a fake endpoint.

## Step 4: Create LocationHelper Utility
**File:** `core/util/LocationHelper.java`

```java
public final class LocationHelper {
    private LocationHelper() {}

    public interface LocationNameCallback { void onResolved(String cityName); }

    public static void resolveCity(Context context, Location location,
                                   LocationNameCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            String city = "Your area";
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(
                        location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String locality = addresses.get(0).getLocality();
                    if (locality != null) city = locality;
                }
            } catch (IOException ignored) {}
            final String result = city;
            mainHandler.post(() -> callback.onResolved(result));
        });
    }
}
```

In HomeViewModel.loadDashboard(), replace `locationName = "Ho Chi Minh City"` with a call
to LocationHelper.resolveCity() using the device's last known location. Pass the resolved
name into the UiState when available.

## Step 5: Add onResume Refresh in HomeFragment
In HomeFragment, add:
```java
@Override public void onResume() {
    super.onResume();
    homeViewModel.loadDashboard();
}
```

## Step 6: Output Phase Report
Write `docs/dev/ui-refactor/phase_11_report.md` with:
- Confirmation mock data removed (list which fields)
- New HomeViewModel constructor signature (for Phase 14)
- Confirmation onResume refresh added in HomeFragment
- LocationHelper file path and public API
```

---

## Phase 12: Profile Page Feature

**Objective:** Wire real badges, stats, and reviews to the profile screen; fix the hardcoded `isOnline=true`; and build the Edit Profile and Avatar Upload screens.
**Gaps Closed:** 2.1 (badges/stats never loaded), **2.2 (isOnline hardcoded to true)**, 2.3 (Edit Profile screen missing), 2.4 (avatar upload missing), **2.5 (reviews not shown)**
**Depends On:** Phase 4 (domain models), Phase 6 (repositories).

---

### Prompt for Phase 12

```
## Phase 12: Profile Page Feature

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_11_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Parallel fetches (badges, stats, reviews) each post individual state updates when they arrive
- isOnline MUST be set to false — do not poll, do not guess, do not show a green dot
- EditProfileFragment follows the exact same MVVM pattern as every other feature Fragment
- Avatar upload accepts byte[] — the Fragment handles image picking, ViewModel handles upload

## Step 1: Read Profile Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/profile/ProfileViewModel.java`
- `ui/profile/ProfileUiState.java`
- `ui/profile/ProfileFragment.java`
Use mcp__ackg-walkmate__search_symbols to find GamificationRepository to confirm
getBadges() and getStats() method signatures.
Use mcp__ackg-walkmate__search_symbols to find ReviewApiService to confirm getReviewsForUser()
exists and its return type.
Use mcp__ackg-walkmate__search_symbols to find UserReview or Review domain model.

## Step 2: Fix isOnline — Gap 2.2
In ProfileViewModel, find the line that sets isOnline = true (hardcoded).
CHANGE it to: `isOnline = false`
Add a comment: `// Presence not supported by backend yet — always false until implemented.`
This is a one-line fix. Do not add any polling logic, do not add any new LiveData for this.

## Step 3: Modify ProfileUiState — Add New Fields
Add:
- `private final List<UserBadge> badges;` — empty list until loaded
- `private final UserStats stats;` — null until loaded
- `private final List<UserReview> reviews;` — empty list until loaded

Update constructor and add getters.
Use mcp__ackg-walkmate__search_symbols to confirm the exact class name for UserBadge,
UserStats, and UserReview (they may have different names in this codebase).

## Step 4: Modify ProfileViewModel — Load Badges, Stats, Reviews in Parallel
Inject GamificationRepository (constructor param; factory updated in Phase 14).

In loadProfile(), after a successful profile fetch, fire three parallel background calls:

```java
// Badges
gamificationRepository.getBadges(userId, new DomainCallback<List<UserBadge>>() {
    @Override public void onSuccess(List<UserBadge> badges) {
        ProfileUiState current = uiStateLiveData.getValue();
        if (current != null) {
            uiStateLiveData.postValue(current.withBadges(badges));
        }
    }
    @Override public void onError(Exception e) { /* non-fatal: fail silently for badges */ }
});

// Stats
gamificationRepository.getStats(userId, new DomainCallback<UserStats>() {
    @Override public void onSuccess(UserStats stats) {
        ProfileUiState current = uiStateLiveData.getValue();
        if (current != null) {
            uiStateLiveData.postValue(current.withStats(stats));
        }
    }
    @Override public void onError(Exception e) { /* non-fatal */ }
});

// Reviews — Gap 2.5
userProfileRepository.getReviewsForUser(userId, new DomainCallback<List<UserReview>>() {
    @Override public void onSuccess(List<UserReview> reviews) {
        ProfileUiState current = uiStateLiveData.getValue();
        if (current != null) {
            uiStateLiveData.postValue(current.withReviews(reviews));
        }
    }
    @Override public void onError(Exception e) { /* non-fatal */ }
});
```

If ProfileUiState is immutable (all-final fields), add withBadges(), withStats(), withReviews()
copy-mutator methods that return a new instance with only the relevant field changed.

If UserProfileRepository does not have getReviewsForUser(), add it:
1. Use mcp__ackg-walkmate__get_file_outline on UserProfileRepository.java (domain interface)
2. Add: `void getReviewsForUser(String userId, DomainCallback<List<UserReview>> callback);`
3. Use mcp__ackg-walkmate__search_symbols to find UserProfileRepositoryImpl.java
4. Implement by calling the existing ReviewApiService.getReviewsForUser() endpoint.

## Step 5: Create Edit Profile Screen

Create these files:
- `ui/profile/edit/EditProfileFragment.java`
- `ui/profile/edit/EditProfileViewModel.java`
- `ui/profile/edit/EditProfileViewModelFactory.java`
- `ui/profile/edit/EditProfileUiState.java`

**EditProfileUiState fields:** `boolean isLoading`, `String fullName`, `String gender`,
`String dateOfBirth`, `String bio`, `int searchRadius`, `List<String> tags`,
`String avatarUrl`, `boolean saveSuccess`, `String fieldError` (nullable).

**EditProfileViewModel methods:**
- `loadCurrentProfile()` — calls userProfileRepository.getProfile() → pre-fills form fields
- `save(String fullName, String gender, String dob, String bio, int radius, List<String> tags)`
  — client-side validation first: bio ≤ 500 chars, tags ≤ 10; then call profileRepo.updateProfile()
- `uploadAvatar(Uri imageUri, ContentResolver resolver)` — read bytes from URI on executor;
  call profileRepo.uploadAvatar(bytes, filename, mimeType, callback)

**EditProfileFragment:** Form layout with fields for fullName, gender, dateOfBirth, bio,
searchRadius, tags. Save button calls viewModel.save(). Image picker launches on avatar tap.
Observe saveSuccess → pop back stack on true.

## Step 6: Wire Navigation from ProfileFragment
In ProfileViewModel, add:
```java
private final MutableLiveData<Void> navigateToEditEvent = new MutableLiveData<>();
public LiveData<Void> getNavigateToEditEvent() { return navigateToEditEvent; }
public void consumeNavigateToEdit() { navigateToEditEvent.postValue(null); }
```

In the existing no-op edit/settings click handler, call:
`navigateToEditEvent.postValue(null)`

In ProfileFragment, observe navigateToEditEvent and launch EditProfileFragment via
FragmentManager.beginTransaction().replace().addToBackStack(null).commit().

## Step 7: Output Phase Report
Write `docs/dev/ui-refactor/phase_12_report.md` with:
- Confirmation isOnline is now hardcoded to false with the TODO comment (Gap 2.2 closed)
- Confirmation reviews fetch is wired via getReviewsForUser() (Gap 2.5 closed)
- Confirmation badges and stats are fetched in parallel after loadProfile() (Gap 2.1 closed)
- New ProfileViewModel constructor signature (for Phase 14)
- List of new files created for Edit Profile screen
- Whether getReviewsForUser() had to be added to UserProfileRepository interface
```

---

## Phase 13: Post-Session Features

**Objective:** Build the four post-session screens: Session History, Route Replay, Post-Session Summary, Submit Review, and Incident Report.
**Gaps Closed:** 7.1 (history screen missing), 7.2 (route replay missing), 7.3 (incident report missing), 7.4 (post-session summary), 7.5 (review screen)
**Depends On:** Phase 6 (repository methods), Phase 4 (SessionSummary, SessionRoute domain models).

---

### Prompt for Phase 13

```
## Phase 13: Post-Session Features

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_12_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Each screen is an independent MVVM unit: Fragment + ViewModel + ViewModelFactory + UiState
- Activities (RouteReplayActivity) receive sessionId via Intent.getStringExtra("SESSION_ID")
- Fragments receive sessionId via Fragment.setArguments(Bundle)
- No business logic in Fragment/Activity — all state in ViewModel
- Polyline decoding: use com.google.maps.android.PolyUtil.decode() — do not write custom decoder

## Step 1: Verify Repository Methods Exist with ACKG MCP
Use mcp__ackg-walkmate__search_symbols to confirm these exist (added in Phase 6):
- WalkSessionRepository.getSessionHistory()
- WalkSessionRepository.getSessionRoute()
- WalkSessionRepository.reportSession()
Use mcp__ackg-walkmate__search_symbols to find ReviewViewModel and ReviewRepository to
understand the existing review submission infrastructure.

## Step 2: Create Session History Screen

Files to create:
- `ui/history/SessionHistoryFragment.java`
- `ui/history/SessionHistoryViewModel.java`
- `ui/history/SessionHistoryViewModelFactory.java`
- `ui/history/SessionHistoryUiState.java`
- `ui/history/SessionHistoryAdapter.java`

**UiState fields:** `boolean isLoading`, `List<SessionSummary> sessions`, `String error`.

**ViewModel:** Inject WalkSessionRepository. Method loadHistory() calls
sessionRepository.getSessionHistory(callback) → posts state.

**Adapter:** Each item shows: formatted date, partnerId (as text placeholder),
status badge chip, distance, duration. Tap → listener.onSessionSelected(sessionId).

**Entry point:** ProfileFragment navigation signal navigateToHistoryEvent → launches
SessionHistoryFragment via FragmentManager.

Add navigateToHistoryEvent MutableLiveData to ProfileViewModel and wire the onWalkHistoryClicked()
no-op to fire it.

## Step 3: Create Route Replay Screen

Files to create:
- `ui/history/routereplay/RouteReplayActivity.java`
- `ui/history/routereplay/RouteReplayViewModel.java`
- `ui/history/routereplay/RouteReplayViewModelFactory.java`
- `ui/history/routereplay/RouteReplayUiState.java`

**UiState fields:** `boolean isLoading`, `SessionRoute route`, `String error`.

**ViewModel:** loadRoute(String sessionId) → sessionRepository.getSessionRoute(sessionId, callback)

**Activity:** In onMapReady(GoogleMap map), observe uiState.getRoute() and decode polylines:
```java
List<LatLng> userAPoints = PolyUtil.decode(route.getUserAPolylines().get(0)); // if encoded
map.addPolyline(new PolylineOptions().addAll(userAPoints).color(Color.BLUE).width(5));
// repeat for userB with Color.RED
```
If polylines are already LatLng arrays in the response (not encoded), draw them directly.

**Entry point:** SessionHistoryAdapter.onSessionSelected → SessionHistoryFragment calls
startActivity(new Intent(requireContext(), RouteReplayActivity.class)
    .putExtra("SESSION_ID", sessionId))

## Step 4: Create Post-Session Summary Screen

Files to create:
- `ui/gamification/PostSessionSummaryFragment.java`
- `ui/gamification/PostSessionSummaryUiState.java`

Use mcp__ackg-walkmate__get_file_outline on the existing PostSessionSummaryViewModel.java
(per implementation_plan.md §10.3, this already exists). Add loadSummary(String sessionId)
if missing — it calls sessionRepository.getSessionHistory() and finds the matching entry.

**Fragment:** Receives sessionId as argument. Observes UiState. Shows distance, duration,
partner name. Shows "Leave a Review" button (→ SubmitReviewFragment) and
"Report Incident" button (→ ReportIncidentFragment, only if session was ABORTED).

**Entry point:** TrackingScreenActivity observes WalkState.FINISHED → starts
PostSessionSummaryFragment passing the sessionId.

## Step 5: Create Submit Review Screen

Files to create:
- `ui/review/SubmitReviewFragment.java`
- `ui/review/ReviewUiState.java`

Use mcp__ackg-walkmate__get_file_outline on the existing ReviewViewModel.java.
Add loadReviewState(String sessionId) — checks sessionSummary.isReviewed(); if already
reviewed, post UiState with alreadyReviewed=true. Add submitReview(String sessionId,
int stars, String comment).

**Fragment:** RatingBar (1–5 stars) + optional EditText comment. Submit button calls
viewModel.submitReview(). On success, pop back stack.

## Step 6: Create Incident Report Screen

Files to create:
- `ui/report/ReportIncidentFragment.java`
- `ui/report/ReportIncidentViewModel.java`
- `ui/report/ReportIncidentViewModelFactory.java`
- `ui/report/ReportIncidentUiState.java`

**UiState fields:** `boolean isLoading`, `boolean submitted`, `String error`.

**ViewModel:** Inject WalkSessionRepository. submitReport(sessionId, reportedUserId, reason,
evidenceUrl) calls sessionRepository.reportSession(callback).

**Fragment:** Spinner or RadioGroup for reason (map to AbortReason values). EditText for
optional evidenceUrl. Submit button. On submitted=true, Toast + pop back stack.

## Step 7: Output Phase Report
Write `docs/dev/ui-refactor/phase_13_report.md` with:
- Full list of files created with package paths
- Entry point wiring summary (what launches what)
- Any Repository methods that were missing and had to be added
- Confirmation that TrackingScreenActivity launches PostSessionSummaryFragment on FINISHED
```

---

## Phase 14: DI & Factory Updates

**Objective:** Update `WalkMateApplication` and all `ViewModelFactory` classes to wire the new constructor dependencies introduced in Phases 7–13.
**Gaps Closed:** Architectural completeness — all ViewModels compile and receive correct dependencies.
**Depends On:** All previous phases — constructor signatures are now stable.

---

### Prompt for Phase 14

```
## Phase 14: DI & Factory Updates

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_13_report.md before starting.
Also read phase reports for phases 8, 9, 11, 12 — each recorded new constructor signatures.

## Architecture Rules (Non-Negotiable)
- Service Locator pattern only — WalkMateApplication provides singleton repository getters
- Each Fragment constructs its own ViewModelFactory using WalkMateApplication getters
- ViewModelFactory.create() uses instanceof checks — standard Android pattern
- No new repositories are introduced in this phase — only wiring

## Step 1: Read DI Files with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `WalkMateApplication.java`
- `ui/matches/MatchesViewModelFactory.java`
- `ui/profile/ProfileViewModelFactory.java`
- `ui/home/HomeViewModelFactory.java`
- `ui/tracking/TrackingViewModelFactory.java`
Use mcp__ackg-walkmate__search_symbols to list all *ViewModelFactory.java files in the project.

## Step 2: Update MatchesViewModelFactory
From phase_8_report.md, the new constructor signature requires UserProfileRepository.
Add UserProfileRepository as a constructor parameter.
In create(), pass it to MatchesViewModel constructor.
In MatchesFragment (or wherever the factory is instantiated), pass
`application.getUserProfileRepository()` — add this getter to WalkMateApplication if missing.

## Step 3: Update ProfileViewModelFactory
From phase_12_report.md, the new constructor requires GamificationRepository.
Add GamificationRepository as a constructor parameter.
In create(), pass it to ProfileViewModel constructor.
In ProfileFragment, pass `application.getGamificationRepository()`.

## Step 4: Update HomeViewModelFactory
From phase_11_report.md, the new constructor requires HotspotRepository,
GamificationRepository, SocialRepository.
Add all three as constructor parameters.
In create(), pass them to HomeViewModel constructor.
In HomeFragment, pass the three repository instances from WalkMateApplication.

## Step 5: Update TrackingViewModelFactory
From phase_9_report.md, the new constructor requires WalkSessionRepository.
Add WalkSessionRepository as a constructor parameter.
In create(), pass it to TrackingViewModel constructor.
In TrackingScreenActivity, pass `application.getWalkSessionRepository()`.

## Step 6: Update WalkMateApplication
For each new getter needed, add a singleton pattern:
```java
private UserProfileRepository userProfileRepository;

public UserProfileRepository getUserProfileRepository() {
    if (userProfileRepository == null) {
        userProfileRepository = new UserProfileRepositoryImpl(
            retrofitClient.create(UserProfileApiService.class), executor);
    }
    return userProfileRepository;
}
```
Use mcp__ackg-walkmate__get_file_outline on WalkMateApplication.java first to see the existing
singleton pattern and replicate it exactly.

Add getters for: UserProfileRepository (if not present), GamificationRepository,
SocialRepository, HotspotRepository, WalkSessionRepository (if not present).

## Step 7: Create Missing ViewModelFactories
From phase_13_report.md, the following factories were created as files:
- SessionHistoryViewModelFactory
- RouteReplayViewModelFactory
- ReportIncidentViewModelFactory
- EditProfileViewModelFactory (Phase 12)

Verify each follows the same pattern as existing factories. If any are missing, create them now.

## Step 8: Output Phase Report
Write `docs/dev/ui-refactor/phase_14_report.md` with:
- List of all factories updated with new parameters
- List of all getters added to WalkMateApplication
- Confirmation that every Fragment constructs its factory using WalkMateApplication getters
- Any compilation errors encountered and how they were resolved
```

---

## Phase 15: WebSocket Chat Integration (Gap 4.6)

**Objective:** Replace the "coming soon" Toast in `SessionFragment` with a fully functional in-session chat backed by an OkHttp WebSocket connection scoped to the session ID.
**Gaps Closed:** **4.6** (WebSocket chat completely absent)
**Depends On:** Phase 14 (DI infrastructure complete). OkHttp is already a transitive dependency of Retrofit — no new library additions required.

---

### Prompt for Phase 15

```
## Phase 15: WebSocket Chat Integration

## Chain: Read Previous Report
Read docs/dev/ui-refactor/phase_14_report.md before starting.

## Architecture Rules (Non-Negotiable)
- Pure Java only — OkHttp WebSocket API is sync/callback-based; no Kotlin coroutines
- WebSocket lifecycle is owned by ChatRepositoryImpl — not by Fragment or ViewModel
- Messages are stored in MutableLiveData<List<ChatMessage>> — Fragment observes, never mutates
- Reconnect on failure: exponential backoff capped at 30 seconds, max 5 retries
- disconnect() MUST be called when the session reaches a terminal state (COMPLETED, ABORTED,
  CANCELLED, NO_SHOW) or when the user navigates away from the chat
- The chat button in SessionFragment is enabled for ACTIVE and CONFIRMED (waiting) sessions

## Step 1: Read Existing Chat Touchpoints with ACKG MCP
Use mcp__ackg-walkmate__get_file_outline on:
- `ui/matches/session/SessionFragment.java` — find the chat Toast listener to remove
- `WalkMateApplication.java` — to understand how OkHttpClient is instantiated (Retrofit shares it)
Use mcp__ackg-walkmate__search_symbols to find "OkHttpClient" to confirm it is already in scope.
Use mcp__ackg-walkmate__search_symbols to find the backend WebSocket URL pattern if present
in any configuration file or constant. If not found, use the base URL from RetrofitClient
with path: `ws://[host]/api/v1/sessions/{sessionId}/chat`
Use mcp__ackg-walkmate__search_symbols to find any existing ChatMessage or Message class.

## Step 2: Create ChatMessage Domain Model
**File:** `domain/chat/ChatMessage.java`
```java
public class ChatMessage {
    private final String messageId;
    private final String sessionId;
    private final String senderId;
    private final String senderName;  // nullable, resolved via profile cache
    private final String content;
    private final long timestampMs;
    private final boolean isFromMe;   // computed: senderId.equals(currentUserId)

    public ChatMessage(String messageId, String sessionId, String senderId,
                       String senderName, String content, long timestampMs,
                       boolean isFromMe) { ... }
    // Full getters
}
```

## Step 3: Create ChatRepository Interface
**File:** `domain/chat/ChatRepository.java`
```java
public interface ChatRepository {
    // Establish WebSocket connection for the given session.
    // idempotent — calling when already connected is a no-op.
    void connect(String sessionId, String currentUserId);

    // Send a message. No-op if not connected.
    void sendMessage(String content);

    // Gracefully close the WebSocket.
    void disconnect();

    // Observable message list. Backed by LiveData — Fragment observes this.
    LiveData<List<ChatMessage>> getMessages();

    // Observable connection state for showing a "Connecting..." indicator.
    LiveData<ConnectionState> getConnectionState();

    enum ConnectionState { CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR }
}
```

## Step 4: Create ChatRepositoryImpl
**File:** `data/repository/ChatRepositoryImpl.java`

Key fields:
```java
private final OkHttpClient okHttpClient;
private final String baseWsUrl;      // ws://[host]/api/v1/sessions/
private WebSocket activeWebSocket;
private String currentSessionId;
private String currentUserId;
private int retryCount = 0;
private static final int MAX_RETRIES = 5;

private final MutableLiveData<List<ChatMessage>> messagesLiveData =
        new MutableLiveData<>(new ArrayList<>());
private final MutableLiveData<ChatRepository.ConnectionState> connectionStateLiveData =
        new MutableLiveData<>(ChatRepository.ConnectionState.DISCONNECTED);
```

**connect() implementation:**
```java
@Override
public void connect(String sessionId, String currentUserId) {
    if (activeWebSocket != null && sessionId.equals(currentSessionId)) return;
    disconnect();
    this.currentSessionId = sessionId;
    this.currentUserId = currentUserId;
    this.retryCount = 0;
    openWebSocket(sessionId);
}

private void openWebSocket(String sessionId) {
    connectionStateLiveData.postValue(ChatRepository.ConnectionState.CONNECTING);
    Request request = new Request.Builder()
            .url(baseWsUrl + sessionId + "/chat")
            .build();
    activeWebSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            retryCount = 0;
            connectionStateLiveData.postValue(ChatRepository.ConnectionState.CONNECTED);
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            ChatMessage msg = parseMessage(text, currentUserId);
            if (msg != null) appendMessage(msg);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            connectionStateLiveData.postValue(ChatRepository.ConnectionState.RECONNECTING);
            scheduleReconnect(sessionId);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            connectionStateLiveData.postValue(ChatRepository.ConnectionState.DISCONNECTED);
        }
    });
}

private void scheduleReconnect(String sessionId) {
    if (retryCount >= MAX_RETRIES) {
        connectionStateLiveData.postValue(ChatRepository.ConnectionState.ERROR);
        return;
    }
    long delayMs = Math.min((long) Math.pow(2, retryCount) * 1_000L, 30_000L);
    retryCount++;
    new Handler(Looper.getMainLooper()).postDelayed(() -> openWebSocket(sessionId), delayMs);
}
```

**sendMessage() implementation:**
```java
@Override
public void sendMessage(String content) {
    if (activeWebSocket == null) return;
    // Build JSON payload: {"type":"CHAT_MESSAGE","content":"..."}
    String json = "{\"type\":\"CHAT_MESSAGE\",\"content\":\""
            + content.replace("\"", "\\\"") + "\"}";
    activeWebSocket.send(json);
}
```

**parseMessage() — parse incoming JSON:**
Use org.json.JSONObject (Android SDK built-in) to parse:
- `message_id`, `sender_id`, `sender_name` (nullable), `content`, `timestamp`
- Compute `isFromMe = senderId.equals(currentUserId)`

**appendMessage():** Get current list from messagesLiveData.getValue(), create a new ArrayList,
add the message, post to messagesLiveData. Always post on background thread (postValue is safe).

**disconnect():**
```java
@Override
public void disconnect() {
    if (activeWebSocket != null) {
        activeWebSocket.close(1000, "Session ended");
        activeWebSocket = null;
    }
    currentSessionId = null;
    messagesLiveData.postValue(new ArrayList<>());
    connectionStateLiveData.postValue(ChatRepository.ConnectionState.DISCONNECTED);
}
```

## Step 5: Create ChatViewModel and ChatUiState
**File:** `domain/chat/ChatUiState.java`
Fields: `boolean isLoading`, `List<ChatMessage> messages`, `ChatRepository.ConnectionState connectionState`, `String error`.

**File:** `ui/chat/ChatViewModel.java`
```java
public class ChatViewModel extends ViewModel {
    private final ChatRepository chatRepository;

    public ChatViewModel(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public void startChat(String sessionId, String currentUserId) {
        chatRepository.connect(sessionId, currentUserId);
    }

    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        chatRepository.sendMessage(content.trim());
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return chatRepository.getMessages();
    }

    public LiveData<ChatRepository.ConnectionState> getConnectionState() {
        return chatRepository.getConnectionState();
    }

    @Override
    protected void onCleared() {
        chatRepository.disconnect();
    }
}
```

**File:** `ui/chat/ChatViewModelFactory.java` — standard factory pattern.

## Step 6: Create ChatFragment
**File:** `ui/chat/ChatFragment.java`

Layout contains:
- RecyclerView for message list (messages flow bottom-up; scroll to bottom on new message)
- EditText for composing a message
- Send ImageButton
- "Connecting..." TextView (VISIBLE when connectionState == CONNECTING or RECONNECTING)
- "Could not connect" error TextView (VISIBLE when connectionState == ERROR)

Receives `sessionId` and `currentUserId` via Bundle arguments.

In onViewCreated():
```java
chatViewModel.startChat(sessionId, currentUserId);

chatViewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
    chatAdapter.submitList(new ArrayList<>(messages));
    recyclerView.scrollToPosition(messages.size() - 1);
});

chatViewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
    connectingLabel.setVisibility(
        state == ChatRepository.ConnectionState.CONNECTING ||
        state == ChatRepository.ConnectionState.RECONNECTING
            ? View.VISIBLE : View.GONE);
    errorLabel.setVisibility(
        state == ChatRepository.ConnectionState.ERROR ? View.VISIBLE : View.GONE);
});

sendButton.setOnClickListener(v -> {
    String text = messageInput.getText().toString().trim();
    if (!text.isEmpty()) {
        chatViewModel.sendMessage(text);
        messageInput.setText("");
    }
});
```

Create `ChatAdapter.java` in `ui/chat/`:
- Extends `RecyclerView.Adapter<ChatAdapter.MessageViewHolder>`
- Two view types: VIEW_TYPE_MINE (right-aligned bubble) and VIEW_TYPE_THEIRS (left-aligned bubble)
- `getItemViewType(int position)`: return `messages.get(position).isFromMe() ? VIEW_TYPE_MINE : VIEW_TYPE_THEIRS`

## Step 7: Wire ChatFragment into SessionFragment
Use mcp__ackg-walkmate__get_file_outline on SessionFragment.java.
Find the chat button click listener that currently shows the "coming soon" Toast.

REMOVE the Toast. REPLACE with:
```java
holder.btnChat.setOnClickListener(v -> {
    // Only enabled for ACTIVE or CONFIRMED sessions
    if (session.getStatus() == WalkSession.Status.ACTIVE ||
        session.getStatus() == WalkSession.Status.PENDING) {
        Bundle args = new Bundle();
        args.putString("SESSION_ID", session.getSessionId());
        args.putString("CURRENT_USER_ID", currentUserId); // from WalkMateApplication or auth store
        ChatFragment chatFragment = new ChatFragment();
        chatFragment.setArguments(args);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, chatFragment)
                .addToBackStack("chat")
                .commit();
    }
});
```

Use mcp__ackg-walkmate__search_symbols to find the correct fragment container ID in the
activity layout.

## Step 8: Wire ChatRepository into WalkMateApplication
In WalkMateApplication.java, add a singleton getter for ChatRepository:
```java
private ChatRepository chatRepository;

public ChatRepository getChatRepository() {
    if (chatRepository == null) {
        // Reuse the existing OkHttpClient from Retrofit — do not create a second one
        chatRepository = new ChatRepositoryImpl(getOkHttpClient(), getBaseWsUrl());
    }
    return chatRepository;
}
```

Use mcp__ackg-walkmate__get_file_outline on WalkMateApplication.java to find the existing
OkHttpClient getter (likely used by RetrofitClient). Reuse it — do not instantiate a second
OkHttpClient.

`getBaseWsUrl()` should derive the WebSocket URL from the existing HTTP base URL:
replace `http://` with `ws://` and `https://` with `wss://`.

## Step 9: Output Phase Report
Write `docs/dev/ui-refactor/phase_15_report.md` with:
- Full list of files created with package paths
- Confirmation the "coming soon" Toast was removed from SessionFragment
- Confirmation ChatRepository reuses the existing OkHttpClient
- WebSocket URL pattern used (exact string template)
- Reconnect strategy summary (max retries, backoff formula)
- Confirmation chatViewModel.onCleared() calls disconnect()
- Any assumptions made about the backend WebSocket message schema
```

---

## Appendix A: Phase Dependency Graph

```
Phase 0 (Custom Views)
    └── Phase 7 (Intent UI)
    └── Phase 8 (Proposal UI)
    └── Phase 9 (Session UI)

Phase 1 (DTOs)
    └── Phase 2 (API Services)
        └── Phase 3 (Mappers)
            └── Phase 6 (Repo Impls)

Phase 4 (Domain Models)
    └── Phase 5 (Repo Interfaces)
        └── Phase 6 (Repo Impls)
            └── Phases 7–13 (Feature Phases)
                └── Phase 14 (DI/Factories)
                    └── Phase 15 (Chat)
```

---

## Appendix B: New Class Manifest (All Phases)

| Class | Type | Package | Phase |
|---|---|---|---|
| `CountdownTimerView` | Custom View | `core/designsystem/view/` | 0 |
| `ActivationWindowButtonView` | Custom View | `core/designsystem/view/` | 0 |
| `CompleteWalkSessionRequest` | DTO | `data/.../dto/request/walksession/` | 1 |
| `ReportSessionRequest` | DTO | `data/.../dto/request/walksession/` | 1 |
| `SessionRouteResponse` | DTO | `data/.../dto/response/session/` | 1 |
| `SessionSummaryMapper` | Mapper | `data/mapper/` | 3 |
| `SessionRouteMapper` | Mapper | `data/mapper/` | 3 |
| `AbortReason` | Enum | `domain/walksession/` | 4 |
| `SessionRoute` | Domain model | `domain/walksession/` | 4 |
| `SessionSummary` | Domain model | `domain/walksession/` | 4 |
| `ChatMessage` | Domain model | `domain/chat/` | 15 |
| `ChatRepository` | Interface | `domain/chat/` | 15 |
| `LocationHelper` | Util | `core/util/` | 11 |
| `EditProfileFragment` | Fragment | `ui/profile/edit/` | 12 |
| `EditProfileViewModel` | ViewModel | `ui/profile/edit/` | 12 |
| `EditProfileViewModelFactory` | DI Factory | `ui/profile/edit/` | 12 |
| `EditProfileUiState` | UiState | `ui/profile/edit/` | 12 |
| `SessionHistoryFragment` | Fragment | `ui/history/` | 13 |
| `SessionHistoryViewModel` | ViewModel | `ui/history/` | 13 |
| `SessionHistoryViewModelFactory` | DI Factory | `ui/history/` | 13 |
| `SessionHistoryUiState` | UiState | `ui/history/` | 13 |
| `SessionHistoryAdapter` | Adapter | `ui/history/` | 13 |
| `RouteReplayActivity` | Activity | `ui/history/routereplay/` | 13 |
| `RouteReplayViewModel` | ViewModel | `ui/history/routereplay/` | 13 |
| `RouteReplayViewModelFactory` | DI Factory | `ui/history/routereplay/` | 13 |
| `RouteReplayUiState` | UiState | `ui/history/routereplay/` | 13 |
| `PostSessionSummaryFragment` | Fragment | `ui/gamification/` | 13 |
| `PostSessionSummaryUiState` | UiState | `ui/gamification/` | 13 |
| `SubmitReviewFragment` | Fragment | `ui/review/` | 13 |
| `ReviewUiState` | UiState | `ui/review/` | 13 |
| `ReportIncidentFragment` | Fragment | `ui/report/` | 13 |
| `ReportIncidentViewModel` | ViewModel | `ui/report/` | 13 |
| `ReportIncidentViewModelFactory` | DI Factory | `ui/report/` | 13 |
| `ReportIncidentUiState` | UiState | `ui/report/` | 13 |
| `ChatRepositoryImpl` | Repository | `data/repository/` | 15 |
| `ChatViewModel` | ViewModel | `ui/chat/` | 15 |
| `ChatViewModelFactory` | DI Factory | `ui/chat/` | 15 |
| `ChatFragment` | Fragment | `ui/chat/` | 15 |
| `ChatAdapter` | Adapter | `ui/chat/` | 15 |
| `ChatUiState` | UiState | `domain/chat/` | 15 |

---

## Appendix C: Gap Coverage Summary

| Gap ID | Description | Phase | Severity |
|---|---|---|---|
| 1.1 | Hotspot count hardcoded | 11 | Medium |
| 1.2 | Home stats mocked | 11 | Medium |
| 1.3 | Friend list mocked | 11 | Medium |
| 1.4 | No onResume refresh | 11 | Low |
| 2.1 | Badges/stats never loaded | 12 | High |
| **2.2** | **isOnline hardcoded true** | **12** | **Medium** |
| 2.3 | Edit Profile screen missing | 12 | High |
| 2.4 | Avatar upload missing | 12 | High |
| **2.5** | **Reviews not shown on profile** | **12** | **Low** |
| 3.1 | description missing from request | 1, 6 | High |
| 3.2 | WalkIntent expiresAt dropped | 3, 4, 7 | High |
| 3.3 | WalkIntent description dropped | 3, 4 | High |
| 3.4 | Mapper hardcodes today's date | 3 | Medium |
| 3.5 | MATCHING state not differentiated | 7 | High |
| 3.6 | findMatch not in MatchesViewModel | 7 | High |
| 3.7 | 204 No Content not handled | 6 | High |
| 4.1 | WalkProposal missing 5 fields | 1, 3, 4 | Critical |
| 4.2 | Case A vs Case B not distinguished | 8 | Critical |
| 4.3 | Premature session navigation | 8 | Critical |
| 4.4 | No countdown on proposal cards | 0, 8 | High |
| 4.5 | Partner profile not fetched | 8 | High |
| **4.6** | **Chat integration absent** | **15** | **High** |
| 5.1 | WalkProposal mapper drops fields | 3 | Critical |
| 5.2 | Activation window not enforced | 0, 9 | High |
| 5.3 | Direct start bypasses activate API | 9 | Critical |
| 5.4 | 5-minute minimum not enforced | 9 | High |
| 5.6 | GPS interval is 3s not 5s | 6 | Medium |
| 5.7 | Abort endpoint not wired | 9 | High |
| 5.8 | No stop on terminal session | 10 | High |
| 6.1 | WalkSession timestamps dropped | 3, 4 | Critical |
| 6.3 | complete endpoint missing | 2, 6 | Critical |
| 6.4 | acknowledged_ids parsing wrong | 1, 3 | Critical |
| 7.1 | Session history screen missing | 2, 6, 13 | High |
| 7.2 | Route replay screen missing | 2, 6, 13 | Medium |
| 7.3 | Incident report screen missing | 2, 6, 13 | High |
| 7.4 | Post-session summary missing | 13 | Medium |
| 7.5 | Review screen missing | 13 | Medium |
