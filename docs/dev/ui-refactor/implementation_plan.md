# WalkMate Frontend — Implementation Plan
**Date:** 2026-04-08
**Based on:** `gap_analysis.md` + `docs/single-source-of-truth/architecture/Frontend_VI.md`
**Architecture constraints:** Pure Java · MVVM · LiveData · Room · Manual DI · No Coroutines · No RxJava
**Execution model:** Fix bottom-up — Data layer first, Domain second, UI last.

---

## 0. Pre-Work: Two New Custom Views

These custom views are needed by multiple features and must be built first.

---

### CV-1 — `CountdownTimerView` (`core/designsystem/view/`)

**Trigger for creation:** Internal state (live tick), reused in ≥3 places (intent cards, proposal cards, activation timer in session cards) → mandatory Custom View per architecture guideline §8.2.

**Extends:** `androidx.appcompat.widget.AppCompatTextView`

**Layout file:** None (extends TextView directly — no `<merge>` layout needed)

**Public API:**
```java
// Parse ISO-8601 instant, compute remaining ms, start internal CountDownTimer.
void startCountdown(String expiresAtIso);

// Provide pre-computed epoch ms for callers that already parsed the timestamp.
void startCountdown(long expiresAtEpochMs);

// Cancel the internal timer — must be called from adapter's onViewRecycled().
void cancelCountdown();
```

**Internal strategy (no Coroutines, no RxJava):**
- Use `android.os.CountDownTimer` (Android SDK built-in).
- In `startCountdown()`: cancel any running timer, compute `remaining = expiresAtEpochMs - System.currentTimeMillis()`. If ≤ 0, display "Expired" and call the optional `OnExpiredListener`. Otherwise create a new `CountDownTimer(remaining, 1_000L)`.
- `onTick(ms)`: set text to `String.format("%02d:%02d", ms/60000, (ms%60000)/1000)`.
- `onFinish()`: set text to "Expired", fire `OnExpiredListener`.
- Override `onDetachedFromWindow()` to cancel the timer so recycled views don't leak.
- Expose `setOnExpiredListener(OnExpiredListener l)` so Fragment can refresh the list when a timer hits zero.

**`attrs.xml` entry:**
```xml
<declare-styleable name="CountdownTimerView">
    <attr name="wm_urgentThresholdSec" format="integer" />
    <attr name="wm_urgentColor"        format="color"   />
    <attr name="wm_normalColor"        format="color"   />
</declare-styleable>
```
Below `wm_urgentThresholdSec` (default 60 s), the text color switches to `wm_urgentColor` (red).

**Adapter contract:** In `RecyclerView.Adapter.onViewRecycled(holder)`, call `holder.countdownView.cancelCountdown()` to prevent stale ticks on recycled cells.

---

### CV-2 — `ActivationWindowButtonView` (`core/designsystem/view/`)

**Trigger for creation:** Encapsulates the S-3 window calculation + button enable/disable state. Used in `SessionAdapter` and the future `SessionDetailFragment`. State toggling logic must not live in Adapter/Fragment per §8.2.

**Extends:** `LinearLayout` (vertical — label above button)

**Layout file:** `res/layout/view_activation_window_button.xml` (`<merge>` root with a `TextView` label and a `WalkMateButton`)

**Public API:**
```java
// Provide the scheduled start as an ISO-8601 string.
// Internally computes open window = start - 10 min, close = start + 15 min.
// Enables/disables the inner WalkMateButton accordingly.
// Schedules a re-evaluation via Handler every 60 seconds until window closes.
void bind(String scheduledStartIso, View.OnClickListener onArrived);

// Cancel the re-evaluation handler — call from onViewRecycled / onDestroyView.
void release();
```

**Internal strategy:**
- Parse `scheduledStartIso` → `Instant` → `epochMs` using `Instant.parse()`.
- `windowOpenMs = epochMs - (WalkSession.ACTIVATION_WINDOW_BEFORE_MINUTES * 60_000L)`.
- `windowCloseMs = epochMs + (WalkSession.ACTIVATION_WINDOW_AFTER_MINUTES * 60_000L)`.
- `boolean inWindow = now >= windowOpenMs && now <= windowCloseMs`.
- Button enabled/disabled set immediately.
- Post a re-check via `new Handler(Looper.getMainLooper()).postDelayed(this::evaluate, 60_000L)` — cancels itself when `release()` is called.

**`attrs.xml` entry:**
```xml
<declare-styleable name="ActivationWindowButtonView">
    <attr name="wm_arriveLabel"  format="string" />
    <attr name="wm_waitingLabel" format="string" />
</declare-styleable>
```

---

## 1. Data Layer Fixes (Foundation — Do First)

These changes are pure data contract corrections with no UI dependency.

---

### 1.1 Add Missing Fields to DTOs

#### `CreateWalkIntentRequest`
**Modify:** Add field `@SerializedName("description") private String description` and update the full constructor and getter.

#### `WalkIntentResponse`
**No change needed** — `expires_at` is already mapped. Mapper drops it (fixed in §1.3).

#### `PushRoutePointsResponse`
**Modify:** Replace the sole `syncedCount: int` field with `@SerializedName("acknowledged_ids") private List<Long> acknowledgedIds`. Add getter. **Remove** `syncedCount` — the spec does not define it; this field was wrong.

#### New: `CompleteWalkSessionRequest`
**Create:** `data/datasource/remote/dto/request/walksession/CompleteWalkSessionRequest.java`
Empty body POJO (backend endpoint requires POST with no body). Single no-arg constructor; no fields.

#### New: `ReportSessionRequest`
**Create:** `data/datasource/remote/dto/request/walksession/ReportSessionRequest.java`
Fields: `@SerializedName("reportedUserId") String reportedUserId`, `@SerializedName("reason") String reason`, `@SerializedName("evidenceUrl") String evidenceUrl` (nullable). Full constructor + getters.

#### New: `SessionRouteResponse`
**Create:** `data/datasource/remote/dto/response/session/SessionRouteResponse.java`
Fields: `@SerializedName("user_a_polylines") List<String> userAPolylines`, `@SerializedName("user_b_polylines") List<String> userBPolylines`, `@SerializedName("total_distance_km") double totalDistanceKm`, `@SerializedName("duration_minutes") int durationMinutes`.

---

### 1.2 Expand `SessionApiService`

**Modify:** `data/datasource/remote/api/SessionApiService.java` — add the following four methods:

```java
// UC-19 — Complete an active session
@POST("api/v1/sessions/{sessionId}/complete")
Call<ApiResponse<WalkSessionResponse>> completeSession(@Path("sessionId") String sessionId);

// UC-22 — Fetch terminal session history
@GET("api/v1/sessions/history")
Call<ApiResponse<List<WalkSessionResponse>>> getSessionHistory();

// UC-23 — Fetch GPS route for a terminal session
@GET("api/v1/sessions/{sessionId}/route")
Call<ApiResponse<SessionRouteResponse>> getSessionRoute(@Path("sessionId") String sessionId);

// UC-25 — Submit an incident report
@POST("api/v1/sessions/{sessionId}/report")
Call<ApiResponse<Void>> reportSession(
        @Path("sessionId") String sessionId,
        @Body ReportSessionRequest body);
```

---

### 1.3 Fix Mappers

#### `WalkIntentMapper.toDomain()`
**Modify:** Pass `response.getExpiresAt()` as the new `expiresAt` field. Pass `response.getDescription()` (once the DTO field is added — see §1.1 note: `WalkIntentResponse` does not have `description` yet; however, the spec's response shape includes it. If backend does not return it yet, default to `null`).

Also **delete** the broken `toRequest()` factory — it hardcodes `LocalDate.now()` and is no longer needed because `WalkIntentRepositoryImpl.createIntent()` builds the request directly.

#### `WalkProposalMapper.toDomain()`
**Modify:** Map the following currently-dropped fields from `WalkProposalResponse`:
- `expiresAt` → `response.getExpiresAt()`
- `meetingLat` → `response.getProposedLat()`
- `meetingLng` → `response.getProposedLng()`
- `myAcceptanceStatus` → `response.getMyAcceptanceStatus()`
- `sessionId` → `response.getSessionId()` (new field, needed for Case B navigation)

#### `WalkSessionMapper.toDomain()`
**Modify:** Map all currently-dropped fields:
- `scheduledEnd` → `response.getScheduledEnd()`
- `startedAt` → `response.getStartedAt()`
- `endedAt` → `response.getEndedAt()`
- `userAActivatedAt` → `response.getUserAActivatedAt()`
- `userBActivatedAt` → `response.getUserBActivatedAt()`
- `isReviewed` → `response.isReviewed()`

The `toDomain()` signature takes `(WalkSessionResponse response, String callerId)` — pass `callerId` to determine `isCallerUserA` (for activation timestamp lookup in S-3).

---

## 2. Domain Layer Changes

---

### 2.1 Enrich `WalkIntent`

**Modify:** `domain/walkintent/WalkIntent.java`

Add fields to constructor and class:
- `private final String expiresAt;` — ISO-8601 string, nullable
- `private final String description;` — nullable

Add getter `getExpiresAt()` and `getDescription()`.

**Status helper:** Add a convenience method `boolean isOpen()` returning `"OPEN".equals(status)` and `boolean isMatching()` returning `"MATCHING".equals(status)`. This prevents magic-string comparisons in adapters.

---

### 2.2 Enrich `WalkProposal`

**Modify:** `domain/walkproposal/WalkProposal.java`

Add fields:
- `private final String expiresAt;` — ISO-8601 string
- `private final double meetingLat;`
- `private final double meetingLng;`
- `private final String myAcceptanceStatus;` — `"ACCEPTED"` or null
- `private final String sessionId;` — null until CONFIRMED

Update constructor (add 5 new params), add getters.

Add helper: `boolean isAcceptedByMe()` returns `"ACCEPTED".equals(myAcceptanceStatus)`.
Add helper: `boolean isConfirmed()` returns `Status.CONFIRMED == status && sessionId != null`.

---

### 2.3 Enrich `WalkSession`

**Modify:** `domain/walksession/WalkSession.java`

Add fields:
- `private final String scheduledEnd;`
- `private final String startedAt;`
- `private final String endedAt;`
- `private final String userAActivatedAt;`
- `private final String userBActivatedAt;`
- `private final boolean isReviewed;`
- `private final boolean isCallerUserA;` — resolved at mapper time from `callerId`

Add **public static constants:**
```java
public static final int ACTIVATION_WINDOW_BEFORE_MINUTES = 10;
public static final int ACTIVATION_WINDOW_AFTER_MINUTES  = 15;
public static final int MINIMUM_WALK_DURATION_MINUTES    = 5;
```

Add helper: `boolean hasCallerActivated()` returns `isCallerUserA ? userAActivatedAt != null : userBActivatedAt != null`.
Add helper: `boolean hasBothActivated()` returns `userAActivatedAt != null && userBActivatedAt != null`.
Add helper: `boolean canComplete()` — returns true if `startedAt` is set and elapsed time from `startedAt` exceeds `MINIMUM_WALK_DURATION_MINUTES`. Uses `Instant.parse(startedAt).toEpochMilli()`.

Update constructor (add 6 new params + `isCallerUserA`). Update `WalkSessionMapper` to build the new constructor.

---

### 2.4 Add `AbortReason` Enum

**Create:** `domain/walksession/AbortReason.java`

```java
public enum AbortReason {
    SAFETY_CONCERN, EMERGENCY, PARTNER_MISCONDUCT, OTHER;

    /** Returns the JSON-safe string expected by the backend. */
    public String toApiValue() { return name(); }
}
```

---

### 2.5 New Domain Model: `SessionRoute`

**Create:** `domain/walksession/SessionRoute.java`

Fields: `List<String> userAPolylines`, `List<String> userBPolylines`, `double totalDistanceKm`, `int durationMinutes`. Used by the Route Replay screen (UC-23).

---

### 2.6 New Domain Model: `SessionSummary` (for History)

**Create:** `domain/walksession/SessionSummary.java`

Fields: `String sessionId`, `WalkSession.Status status`, `String partnerId`, `String scheduledStart`, `double totalDistanceKm`, `int durationMinutes`, `boolean isReviewed`.
This is a lightweight list-item model — no activation/GPS fields.

---

### 2.7 Expand `WalkSessionRepository` Interface

**Modify:** `domain/walksession/WalkSessionRepository.java` — add:

```java
void completeSession(String sessionId, DomainCallback<WalkSession> callback);
void getSessionHistory(DomainCallback<List<SessionSummary>> callback);
void getSessionRoute(String sessionId, DomainCallback<SessionRoute> callback);
void reportSession(String sessionId, String reportedUserId,
                   String reason, String evidenceUrl,
                   DomainCallback<Void> callback);
```

---

### 2.8 Expand `WalkIntentRepository` Interface

**Modify:** `domain/walkintent/WalkIntentRepository.java` — update `createIntent()`:

```java
void createIntent(String hotspotId, String date, float timeStart, float timeEnd,
                  int ageMin, int ageMax, List<String> tags,
                  boolean isPrivate, String invitedFriendId,
                  String description,                         // NEW parameter
                  DomainCallback<WalkIntent> callback);
```

---

### 2.9 Expand `TrackingRepository` Interface

**Modify:** `domain/tracking/TrackingRepository.java` — add:

```java
// Triggered by the periodic 30-second scheduler. Fetches all unsynced points
// and pushes them. Does NOT fire the threshold check — only time-based.
void triggerPeriodicSync(String sessionId);
```

---

## 3. Repository Implementation Changes

---

### 3.1 `WalkSessionRepositoryImpl` — Implement New Methods

**Modify:** `data/repository/WalkSessionRepositoryImpl.java`

For each new `SessionApiService` method, follow the exact same error-handling pattern already used in `getActiveSessions()`: `executor.execute(() -> { try { Response resp = apiService.xxx().execute(); ... callback... } catch (IOException e) { ... } })`.

Specific mappers:
- `completeSession()` → `WalkSessionMapper.toDomain(data, callerId)` → `DomainCallback<WalkSession>`
- `getSessionHistory()` → new `SessionSummaryMapper.toDomainList()` → `DomainCallback<List<SessionSummary>>`
- `getSessionRoute()` → new `SessionRouteMapper.toDomain()` → `DomainCallback<SessionRoute>`
- `reportSession()` → success → `DomainCallback<Void>`

---

### 3.2 New Mapper Classes

**Create:** `data/mapper/SessionSummaryMapper.java`
Maps `WalkSessionResponse` → `SessionSummary`. Only maps the 7 lightweight fields.

**Create:** `data/mapper/SessionRouteMapper.java`
Maps `SessionRouteResponse` → `SessionRoute`. Simple field copy, no user-ID logic.

---

### 3.3 `WalkIntentRepositoryImpl` — Pass `description`

**Modify:** `data/repository/WalkIntentRepositoryImpl.java`

Update `createIntent()` method signature to accept `String description`. Construct the `CreateWalkIntentRequest` object including `description`. If `description` is `null`, pass `null` (backend accepts nullable).

---

### 3.4 `TrackingRepositoryImpl` — Fix Sync Acknowledgment + Add Periodic Trigger

**Modify:** `data/repository/TrackingRepositoryImpl.java`

**(a) Fix `pushRoutePoints()` to use `acknowledgedIds`:**
After a successful response, extract `response.body().getData().getAcknowledgedIds()`. Pass that list to `dao.markAsSynced(acknowledgedIds)`. If `acknowledgedIds` is null or empty, do NOT mark anything — keep them in Room for retry.

**(b) Add `triggerPeriodicSync()` (the new interface method):**
```java
@Override
public void triggerPeriodicSync(String sessionId) {
    // Same logic as triggerBatchSync() but bypasses the threshold check.
    executor.execute(() -> {
        List<RoutePointEntity> unsyncedEntities = dao.getUnsyncedPoints(sessionId);
        if (!unsyncedEntities.isEmpty()) {
            List<RoutePoint> domainPoints = RoutePointMapper.toDomainList(unsyncedEntities);
            pushRoutePoints(sessionId, domainPoints, new DomainCallback<Void>() { ... });
        }
    });
}
```

---

### 3.5 `SessionTrackingService` — Add 30-Second Periodic Sync

**Modify:** `domain/tracking/SessionTrackingService.java`

Add a second `ScheduledExecutorService` dedicated to time-based syncing:
```java
private final ScheduledExecutorService syncScheduler =
        Executors.newSingleThreadScheduledExecutor();
private ScheduledFuture<?> periodicSyncFuture;
```

In `startSession(String sessionId)`:
```java
periodicSyncFuture = syncScheduler.scheduleAtFixedRate(
        () -> repository.triggerPeriodicSync(sessionId),
        30L, 30L, TimeUnit.SECONDS);
```

In `stopTracking()`:
```java
if (periodicSyncFuture != null) periodicSyncFuture.cancel(false);
syncScheduler.shutdown();
```

---

## 4. Feature: Walk Intent (UC-08, 09, 10, 11)

---

### 4.1 `CreateIntentViewModel` — Pass `description`

**Modify:** `ui/explore/createintent/CreateIntentViewModel.java`

Add `description` parameter to `submit()`. Pass to `intentRepository.createIntent(...)`.

### 4.2 `CreateIntentUiState` — Structured Error Handling

**Modify:** `ui/explore/createintent/CreateIntentUiState.java`

Replace the single `String error` field with an `IntentFormError` inner class or a typed enum field:
```java
public enum FieldError { TIME_RANGE, AGE_RANGE, OVERLAPPING, OVERLAPPING_SESSION, HOTSPOT_NOT_FOUND, PRIVATE_FRIEND, GENERAL }
private final FieldError fieldError;     // null if no error
private final String errorMessage;       // raw message for GENERAL errors
```

`CreateIntentViewModel` maps the error code string prefix from `DomainCallback.onError()` to the appropriate `FieldError` enum value. The Fragment reads `FieldError` to decide whether to show an inline field error or a blocking dialog.

### 4.3 `FindingFragment` + `FindingAdapter` — OPEN vs MATCHING, Find Match, Countdown

**Modify:** `ui/matches/finding/FindingFragment.java`
- `onViewCreated()`: after observing `uiState`, schedule an `adapter.notifyDataSetChanged()` via `Handler` every 60 seconds to refresh activation-window calculations.
- Wire "Find Match" clicks from adapter to `matchesViewModel.triggerMatch(intentId)`.

**Modify:** `ui/matches/finding/FindingAdapter.java`
- Item layout must have two button states:
  - OPEN: "Find Match" button enabled + "Cancel" button enabled.
  - MATCHING: "Find Match" → "View Proposal" button; "Cancel" button hidden/disabled; lock icon `ImageView` visible.
- `onBindViewHolder()`: check `intent.isOpen()` / `intent.isMatching()` to set visibility.
- Add `CountdownTimerView` to item layout for `expires_at`. Bind via `holder.countdown.startCountdown(intent.getExpiresAt())`.
- `onViewRecycled(holder)`: call `holder.countdown.cancelCountdown()`.

### 4.4 `MatchesViewModel` — Add `triggerMatch()`

**Modify:** `ui/matches/MatchesViewModel.java`

Add method:
```java
public void triggerMatch(String intentId) {
    intentRepository.findMatch(intentId, new DomainCallback<WalkProposal>() {
        @Override public void onSuccess(WalkProposal result) {
            if (result == null) {
                // Case B: 204 No Content — no match yet
                postNoMatchSignal();
            } else {
                // Case A: match found, proposal created → refresh and navigate to Proposal tab
                loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_PROPOSAL));
            }
        }
        @Override public void onError(Exception error) { postError(error.getMessage()); }
    });
}
```

Add `private final MutableLiveData<Boolean> noMatchFoundEvent = new MutableLiveData<>(null)` and a `getNoMatchFoundEvent()` + `consumeNoMatchFound()` pair. `FindingFragment` observes this to show a Toast "No match found yet. We'll notify you when one is found!"

### 4.5 `WalkIntentRepositoryImpl` — Handle 204 for `findMatch()`

**Modify:** `data/repository/WalkIntentRepositoryImpl.java`

In `findMatch()`, after `Response resp = apiService.findMatch(intentId).execute()`:
```java
if (resp.code() == 204 || resp.body() == null || !resp.body().isSuccess()) {
    if (resp.code() == 204) {
        callback.onSuccess(null); // No match yet — not an error
    } else {
        // real error
        ...
    }
    return;
}
callback.onSuccess(WalkProposalMapper.toDomain(resp.body().getData()));
```

---

## 5. Feature: Proposal Negotiation (UC-12, 13, 14, 15)

---

### 5.1 `ProposalAdapter` — Countdown + Partner Name + Waiting State

**Modify:** `ui/matches/proposal/ProposalAdapter.java`

- Add `CountdownTimerView` to the item layout for `expires_at`. Bind via `holder.countdown.startCountdown(proposal.getExpiresAt())`. Cancel on recycle.
- Show partner name as placeholder + trigger async enrichment (see §5.2).
- Add "Waiting for partner..." overlay `View` (visible when `proposal.isAcceptedByMe() && proposal.getStatus() == PENDING`). In this state, hide "Accept" and "Pass" buttons.
- Expose `setOnExpiredListener` at adapter level: on expiry, call `matchesViewModel.loadAll()`.

### 5.2 Partner Profile Enrichment (UC-12)

**Modify:** `MatchesViewModel` — add a private `enrichProposalPartnerNames(List<WalkProposal> proposals)` helper.
- For each proposal, if `matchedUserId` is not yet enriched, call `userProfileRepository.getPublicProfile(matchedUserId, callback)`.
- On success, post an updated state with the real `matchedUserName` and `avatarUrl`.
- Strategy: introduce a thin `UserProfileCache` (a `Map<String, UserProfile>` inside `MatchesViewModel`) to avoid repeated API calls for the same user within a session. Not persisted to Room.

`MatchesViewModel` already has `WalkIntentRepository`, `WalkProposalRepository`, `WalkSessionRepository` injected. Add `UserProfileRepository` as a fourth dependency. Update `MatchesViewModelFactory` accordingly.

### 5.3 `MatchesViewModel.acceptProposal()` — Distinguish Case A vs Case B

**Modify:** `ui/matches/MatchesViewModel.java`

Change `acceptProposal()` to use `DomainCallback<WalkProposal>` (already what `WalkProposalRepository.acceptProposal()` returns after fixing the repository):

```java
proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkProposal>() {
    @Override public void onSuccess(WalkProposal result) {
        if (result.isConfirmed()) {
            // Case B: session_id is non-null — full reload then navigate to Session tab.
            loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION));
        } else {
            // Case A: this user accepted; partner hasn't yet — update proposal in list.
            updateProposalInPlace(result); // replace old proposal entry in current UiState list
        }
    }
    @Override public void onError(Exception error) { postError(error.getMessage()); }
});
```

Add private helper `updateProposalInPlace(WalkProposal updated)` that rebuilds the proposals list with the updated object.

### 5.4 `WalkProposalRepositoryImpl.acceptProposal()` — Return `WalkProposal`

**Modify:** `data/repository/WalkProposalRepositoryImpl.java`

The current return type is `DomainCallback<WalkSession>`. Change to `DomainCallback<WalkProposal>` — the mapper returns the full proposal object including the new `sessionId` and `status` fields. The ViewModel (§5.3) decides the navigation outcome based on `proposal.isConfirmed()`.

---

### 5.5 `WalkProposalRepository` Interface Update

**Modify:** `domain/walkproposal/WalkProposalRepository.java`

Change `acceptProposal` signature:
```java
// Before:  void acceptProposal(String proposalId, DomainCallback<WalkSession> callback);
// After:
void acceptProposal(String proposalId, DomainCallback<WalkProposal> callback);
```

---

## 6. Feature: Session Lifecycle (UC-16, 17, 18, 19, 20)

---

### 6.1 `SessionFragment` — Replace "Start Walk" with Activation Flow

**Modify:** `ui/matches/session/SessionFragment.java`

- Replace `setOnStartWalkClickListener` with `setOnArriveClickListener` (bound to `ActivationWindowButtonView.bind()`).
- On arrive click: call `matchesViewModel.activateSession(session.getSessionId())`.
- Observe the new `activationResultEvent` LiveData from `MatchesViewModel`:
  - Case A (PENDING, user activated): show "Waiting for partner..." overlay. Start polling every 15 seconds via `Handler.postDelayed(() -> matchesViewModel.loadAll(), 15_000L)` — store the `Runnable` reference for cancellation in `onDestroyView()`.
  - Case B (ACTIVE, both activated): launch `TrackingScreenActivity` with session data.

### 6.2 `MatchesViewModel` — Add `activateSession()`

**Modify:** `ui/matches/MatchesViewModel.java`

Add:
```java
private final MutableLiveData<ActivationResult> activationResultEvent = new MutableLiveData<>();

public LiveData<ActivationResult> getActivationResultEvent() { return activationResultEvent; }
public void consumeActivationResult() { activationResultEvent.postValue(null); }

public void activateSession(String sessionId) {
    sessionRepository.activateSession(sessionId, new DomainCallback<WalkSession>() {
        @Override public void onSuccess(WalkSession result) {
            activationResultEvent.postValue(new ActivationResult(result, null));
            loadAll(null);
        }
        @Override public void onError(Exception error) {
            activationResultEvent.postValue(new ActivationResult(null, error.getMessage()));
        }
    });
}
```

`ActivationResult` is a simple package-private inner class in `MatchesViewModel` (not a full UiState — it's a one-shot navigation signal):
```java
static class ActivationResult {
    final WalkSession session;    // non-null on success
    final String errorCode;       // non-null on failure
}
```

`SessionFragment` observes `activationResultEvent` and routes:
- `session.getStatus() == ACTIVE` → launch `TrackingScreenActivity`
- `session.getStatus() == PENDING` → show waiting UI + start polling
- `errorCode = "SESSION_ACTIVATION_WINDOW_CLOSED"` → Toast + 5-second delayed `loadAll()`

### 6.3 `SessionAdapter` — Wire `ActivationWindowButtonView`

**Modify:** `ui/matches/session/SessionAdapter.java`

- Replace the old "Start Walk" button with `ActivationWindowButtonView` in the item layout.
- `onBindViewHolder()`: call `holder.activationWindowBtn.bind(session.getScheduledStart(), v -> listener.onArrive(session.getSessionId()))`.
- `onViewRecycled()`: call `holder.activationWindowBtn.release()`.
- For ACTIVE sessions: hide `ActivationWindowButtonView`, show "Complete Walk" button (enabled only if `session.canComplete()`) and "Emergency Abort" button.

### 6.4 `TrackingViewModel` — Wire Complete Session + 5-Minute Gate

**Modify:** `ui/tracking/TrackingViewModel.java`

**Inject `WalkSessionRepository`** (add to constructor via `WalkMateApplication`). Update `TrackingViewModelFactory` accordingly.

**Replace `finishWalk()`** with a two-phase approach:

```java
// Called when user taps "Complete Walk" button.
public void requestCompleteWalk() {
    if (walkStateLiveData.getValue() != WalkState.ACTIVE) return;
    // S-5: check 5-minute minimum. elapsedSecondsLiveData is already the stopwatch.
    long elapsed = valueOrDefault(elapsedSecondsLiveData, 0L);
    if (elapsed < WalkSession.MINIMUM_WALK_DURATION_MINUTES * 60L) {
        // Post a "too early" error to UiState — Fragment shows toast + disables button.
        uiStateLiveData.setValue(/* rebuild with completeTooEarly = true */);
        return;
    }
    stopTimer();
    stopGpsService();
    walkStateLiveData.setValue(WalkState.FINISHING); // new intermediate state
    sessionRepository.completeSession(sessionId, new DomainCallback<WalkSession>() {
        @Override public void onSuccess(WalkSession result) {
            walkStateLiveData.postValue(WalkState.FINISHED);
        }
        @Override public void onError(Exception error) {
            // Restore ACTIVE state; show error toast.
            walkStateLiveData.postValue(WalkState.ACTIVE);
            startTimer(); startGpsService();
            completionErrorLiveData.postValue(error.getMessage());
        }
    });
}
```

Add `WalkState.FINISHING` to the `WalkState` enum (intermediate: API call in progress).
Add `private final MutableLiveData<String> completionErrorLiveData = new MutableLiveData<>()` — observed by `TrackingScreenActivity` to show toast.
Add `completeTooEarlySeconds` field to `TrackingUiState` — the remaining seconds until completion is allowed (0 when allowed).

**Also add `abortWalk(AbortReason reason)`:**
```java
public void abortWalk(AbortReason reason) {
    stopTimer(); stopGpsService();
    walkStateLiveData.setValue(WalkState.FINISHING);
    sessionRepository.abortSession(sessionId, reason.toApiValue(), new DomainCallback<Void>() {
        @Override public void onSuccess(Void r) { walkStateLiveData.postValue(WalkState.FINISHED); }
        @Override public void onError(Exception e) { /* restore + toast */ }
    });
}
```

### 6.5 `TrackingScreenActivity` — Add "Complete Walk" + "Emergency Abort" Buttons

**Modify:** `ui/tracking/TrackingScreenActivity.java`

In the layout, add:
- "Complete Walk" `WalkMateButton` (outline style, gray when disabled).
- "Emergency Abort" `WalkMateButton` (danger/red tint).

In `renderState(TrackingUiState state)`:
- Show "Complete Walk" only when `state.getWalkState() == WalkState.ACTIVE`.
- Disable it and show countdown text when `state.getCompleteTooEarlySeconds() > 0`.
- "Complete Walk" click → show `AlertDialog` confirmation → on confirm, call `viewModel.requestCompleteWalk()`.
- "Emergency Abort" click → show `AlertDialog` with `AbortReason` radio buttons → on confirm, call `viewModel.abortWalk(selectedReason)`.

### 6.6 `TrackingUiState` — Add New Fields

**Modify:** `ui/tracking/TrackingUiState.java`

Add:
- `private final long completeTooEarlySeconds;` — 0 when complete is allowed; positive = seconds remaining.
- `private final boolean isSaving;` — true during FINISHING state, drives button loading indicator.

Update constructor accordingly.

### 6.7 Session Polling in `SessionFragment`

**Modify:** `ui/matches/session/SessionFragment.java`

Add:
```java
private final Handler pollHandler = new Handler(Looper.getMainLooper());
private final Runnable pollRunnable = () -> {
    matchesViewModel.loadAll();
    pollHandler.postDelayed(pollRunnable, 30_000L); // reschedule
};

@Override public void onResume() { super.onResume(); pollHandler.postDelayed(pollRunnable, 30_000L); }
@Override public void onPause()  { super.onPause();  pollHandler.removeCallbacks(pollRunnable); }
```

---

## 7. Feature: GPS Path Tracking (UC-21)

---

### 7.1 Stop Sync on Terminal Session State

**Modify:** `data/repository/TrackingRepositoryImpl.java`

In `pushRoutePoints()` error handling:
```java
if ("SESSION_NOT_ACTIVE".equals(apiError.getCode()) ||
    "SESSION_NOT_FOUND".equals(apiError.getCode())) {
    callback.onError(new Exception("SESSION_TERMINAL|" + apiError.getCode()));
}
```

**Modify:** `domain/tracking/SessionTrackingService.java`

In the callback from `triggerPeriodicSync()` → if error message starts with `"SESSION_TERMINAL|"`, call `stopTracking()` and notify the Android foreground service via a registered listener interface:
```java
public interface SessionEndedListener { void onSessionEndedRemotely(String errorCode); }
```
`WalkTrackerService` implements `SessionEndedListener` and on `onSessionEndedRemotely()` calls `stopSelf()` and posts a notification "Your walk session has ended."

---

## 8. Feature: Home Page (UC-07)

---

### 8.1 `HomeViewModel` — Replace Hardcoded Values

**Modify:** `ui/home/HomeViewModel.java`

**(a) Inject `HotspotRepository` and `GamificationRepository`** alongside existing deps. Update `HomeViewModelFactory` accordingly.

**(b) `loadDashboard()`** already fires parallel profile + sessions fetches. Add two more parallel fetches:
- `hotspotRepository.getHotspots(callback)` → count results → `nearbyHotspotCount`
- `gamificationRepository.getStats(userId, callback)` → populate `weeklyDistanceKm`, `weeklySessionCount`

Atomic counter: change `AtomicInteger doneCount` from `== 2` to `== 4` (or use two separate chained flows to keep complexity manageable: hotspot + stats loaded after profile succeeds since we need `userId`).

**(c) Quick Invite List:** Replace `buildMockInviteList()` with a call to `socialRepository.getFriends(callback)` → map to `QuickInviteUser` list.

**(d) `locationName`:** Replace hardcoded `"Ho Chi Minh City"` with a `LocationHelper` utility (a new class in `core/util/`) that resolves the device's last known location to a city name via `Geocoder`. Returns the city string via a callback passed to `HomeViewModel`. If unavailable, fall back to `"Your area"`.

**(e) Refresh on focus:** `HomeFragment.onResume()` → call `homeViewModel.loadDashboard()`.

### 8.2 New Utility: `LocationHelper` (`core/util/`)

**Create:** `core/util/LocationHelper.java`

Static method:
```java
public static void resolveCity(Context context, Location location, LocationNameCallback callback)
```
Uses `android.location.Geocoder` on a background `ExecutorService` thread. Posts result to main thread via `new Handler(Looper.getMainLooper()).post(...)`. Interface `LocationNameCallback` has single method `void onResolved(String cityName)`.

---

## 9. Feature: Profile Page (UC-03, 04, 05)

---

### 9.1 `ProfileViewModel` — Load Badges and Stats

**Modify:** `ui/profile/ProfileViewModel.java`

Inject `GamificationRepository` (add to constructor). Update `ProfileViewModelFactory`.

In `loadProfile()`, after a successful profile fetch, fire two parallel background calls:
```java
gamificationRepository.getBadges(userId, new DomainCallback<List<UserBadge>>() {
    @Override public void onSuccess(List<UserBadge> badges) {
        // merge into current uiState — post updated state with badges
    }
    ...
});
gamificationRepository.getStats(userId, new DomainCallback<UserStats>() {
    @Override public void onSuccess(UserStats stats) {
        // merge into current uiState — post updated state with stats
    }
    ...
});
```
Use an `AtomicInteger` or sequence the merges by checking which fields are still null in the current state.

### 9.2 `ProfileUiState` — Add Badges and Stats

**Modify:** `ui/profile/ProfileUiState.java`

Add fields:
- `private final List<UserBadge> badges;`
- `private final UserStats stats;` (nullable until loaded)

Update constructor + getters.

### 9.3 New: Edit Profile Screen

**Create the following files:**

| File | Package |
|---|---|
| `EditProfileFragment.java` | `ui/profile/edit/` |
| `EditProfileViewModel.java` | `ui/profile/edit/` |
| `EditProfileViewModelFactory.java` | `ui/profile/edit/` |
| `EditProfileUiState.java` | `ui/profile/edit/` |

**`EditProfileUiState`** fields: `isLoading`, `String fullName`, `String gender`, `String dateOfBirth`, `String bio`, `int searchRadius`, `List<String> tags`, `String avatarUrl`, `FieldError fieldError` (nullable), `boolean saveSuccess`.

**`EditProfileViewModel`** methods:
- `loadCurrentProfile()` — pre-fills form with profile data from `UserProfileRepository`.
- `save(String fullName, String gender, String dob, String bio, int radius, List<String> tags)` — validates client-side (bio ≤ 500 chars, tags ≤ 10, gender enum check) before calling `profileRepo.updateProfile(...)`.
- `uploadAvatar(byte[] bytes, String filename, String mimeType)` — delegates to `profileRepo.uploadAvatar(...)`.

**Navigation:** Add a navigation signal `MutableLiveData<Void> navigateToEditEvent` to `ProfileViewModel`. `ProfileFragment.onWalkHistoryClicked()` / edit button tap fires this. `ProfileFragment` observes it and uses Navigation Component or `FragmentManager` to show `EditProfileFragment`.

### 9.4 Navigation Stubs — Wire Real Destinations

**Modify:** `ui/profile/ProfileViewModel.java`

Replace no-op methods with `MutableLiveData<String>` navigation signals:
- `navigateToHistoryEvent` — `ProfileFragment` launches `SessionHistoryFragment` (see §10).
- `navigateToBadgesEvent` — `ProfileFragment` navigates to `GamificationFragment` (future scope, outside this plan).
- Add `consumeNavigation()` method.

---

## 10. Feature: Post-Session (UC-22, 23, 24, 25)

These are entirely new screens. Each follows the standard MVVM sub-feature pattern.

---

### 10.1 Session History (UC-22)

**Create:**

| File | Package |
|---|---|
| `SessionHistoryFragment.java` | `ui/history/` |
| `SessionHistoryViewModel.java` | `ui/history/` |
| `SessionHistoryViewModelFactory.java` | `ui/history/` |
| `SessionHistoryUiState.java` | `ui/history/` |
| `SessionHistoryAdapter.java` | `ui/history/` |

**`SessionHistoryUiState`** fields: `isLoading`, `List<SessionSummary> sessions`, `String error`.

**`SessionHistoryViewModel`** injects `WalkSessionRepository`. `loadHistory()` calls `sessionRepository.getSessionHistory(callback)` → posts state.

**`SessionHistoryAdapter`** binds `SessionSummary` list items. Each card: date, partner ID (to be enriched later via `UserProfileRepository`), status badge, distance/duration. Tap → `onSessionSelected(String sessionId)` callback.

**Entry point:** `ProfileFragment` navigation signal from §9.4 → `requireActivity().getSupportFragmentManager().beginTransaction().replace(...).addToBackStack(null).commit()`.

### 10.2 Route Replay (UC-23)

**Create:**

| File | Package |
|---|---|
| `RouteReplayActivity.java` | `ui/history/routereplay/` |
| `RouteReplayViewModel.java` | `ui/history/routereplay/` |
| `RouteReplayViewModelFactory.java` | `ui/history/routereplay/` |
| `RouteReplayUiState.java` | `ui/history/routereplay/` |

**`RouteReplayActivity`** receives `sessionId` via `Intent.getStringExtra()`.

**`RouteReplayUiState`** fields: `isLoading`, `SessionRoute route`, `String error`.

**`RouteReplayViewModel.loadRoute(sessionId)`** calls `sessionRepository.getSessionRoute(sessionId, callback)`.

**Map rendering:** In `RouteReplayActivity`, observe `uiState.getRoute()` and decode Google Encoded Polylines from `route.getUserAPolylines()` / `route.getUserBPolylines()` using `com.google.maps.android.PolyUtil.decode()`. Draw two `Polyline` objects on `GoogleMap` with distinct colours.

**`SessionHistoryFragment`** launches `RouteReplayActivity` via `startActivity(new Intent(...).putExtra("SESSION_ID", sessionId))`.

### 10.3 Post-Session Summary (UC-19 step 5–7)

**Modify:** `ui/gamification/PostSessionSummaryViewModel.java` — add proper `SessionSummary summary` + `WalkSession session` fields. Add `loadSummary(String sessionId)` that calls `sessionRepository.getSessionHistory(...)` and finds the matching entry.

**Create:** `ui/gamification/PostSessionSummaryFragment.java` + `PostSessionSummaryUiState.java`

`TrackingScreenActivity`, on observing `WalkState.FINISHED`, starts `PostSessionSummaryFragment` via `Intent` or `FragmentTransaction` passing `sessionId`.

### 10.4 Review (UC-24)

**Create:**

| File | Package |
|---|---|
| `SubmitReviewFragment.java` | `ui/review/` |
| `ReviewUiState.java` | `ui/review/` |

**`ReviewViewModel`** already exists. Add `loadReviewState(String sessionId)` (checks if already reviewed using `isReviewed` from `SessionSummary`). Add `submitReview(String sessionId, int stars, String comment)`.

**`SubmitReviewFragment`** shows a star-widget (`RatingBar`) + optional `EditText` comment. Receives `sessionId` as argument.

**Entry point:** `PostSessionSummaryFragment` shows a "Leave a Review" `WalkMateButton`. Tap launches `SubmitReviewFragment` via `FragmentManager`.

### 10.5 Incident Report (UC-25)

**Create:**

| File | Package |
|---|---|
| `ReportIncidentFragment.java` | `ui/report/` |
| `ReportIncidentViewModel.java` | `ui/report/` |
| `ReportIncidentViewModelFactory.java` | `ui/report/` |
| `ReportIncidentUiState.java` | `ui/report/` |

**`ReportIncidentViewModel`** injects `WalkSessionRepository`. `submitReport(sessionId, reportedUserId, reason, evidenceUrl)` calls `sessionRepository.reportSession(...)`.

**Entry point:** `PostSessionSummaryFragment` (after ABORTED sessions) and `SessionHistoryFragment` session detail action bar.

---

## 11. Manual DI — `WalkMateApplication` Updates

**Modify:** `WalkMateApplication.java`

Add singleton getters for any new repositories created by this plan. No new repositories are introduced — all depend on existing API services. However, `MatchesViewModelFactory` and `ProfileViewModelFactory` gain new constructor parameters:
- `MatchesViewModelFactory` → add `UserProfileRepository userProfileRepo`
- `ProfileViewModelFactory` → add `GamificationRepository gamificationRepo`
- `HomeViewModelFactory` → add `HotspotRepository hotspotRepo`, `GamificationRepository gamificationRepo`, `SocialRepository socialRepo`
- `TrackingViewModelFactory` → add `WalkSessionRepository sessionRepo`

Each Fragment's `onViewCreated()` constructs its Factory using `WalkMateApplication` getters. No Hilt/Dagger.

---

## 12. New Class Manifest

| Class | Type | Package | Gap(s) Fixed |
|---|---|---|---|
| `CountdownTimerView` | Custom View | `core/designsystem/view/` | 3.2, 4.4, 5.1 |
| `ActivationWindowButtonView` | Custom View | `core/designsystem/view/` | 5.2, 6.1 |
| `CompleteWalkSessionRequest` | DTO | `data/.../dto/request/walksession/` | 5.3 |
| `ReportSessionRequest` | DTO | `data/.../dto/request/walksession/` | 7.3 |
| `SessionRouteResponse` | DTO | `data/.../dto/response/session/` | 7.2 |
| `AbortReason` | Enum | `domain/walksession/` | 5.7 |
| `SessionRoute` | Domain model | `domain/walksession/` | 7.2 |
| `SessionSummary` | Domain model | `domain/walksession/` | 7.1 |
| `SessionSummaryMapper` | Mapper | `data/mapper/` | 7.1 |
| `SessionRouteMapper` | Mapper | `data/mapper/` | 7.2 |
| `LocationHelper` | Util | `core/util/` | 1.2 |
| `EditProfileFragment` | Fragment | `ui/profile/edit/` | 2.3 |
| `EditProfileViewModel` | ViewModel | `ui/profile/edit/` | 2.3 |
| `EditProfileViewModelFactory` | DI Factory | `ui/profile/edit/` | 2.3 |
| `EditProfileUiState` | UiState | `ui/profile/edit/` | 2.3 |
| `SessionHistoryFragment` | Fragment | `ui/history/` | 7.1 |
| `SessionHistoryViewModel` | ViewModel | `ui/history/` | 7.1 |
| `SessionHistoryViewModelFactory` | DI Factory | `ui/history/` | 7.1 |
| `SessionHistoryUiState` | UiState | `ui/history/` | 7.1 |
| `SessionHistoryAdapter` | Adapter | `ui/history/` | 7.1 |
| `RouteReplayActivity` | Activity | `ui/history/routereplay/` | 7.2 |
| `RouteReplayViewModel` | ViewModel | `ui/history/routereplay/` | 7.2 |
| `RouteReplayViewModelFactory` | DI Factory | `ui/history/routereplay/` | 7.2 |
| `RouteReplayUiState` | UiState | `ui/history/routereplay/` | 7.2 |
| `PostSessionSummaryFragment` | Fragment | `ui/gamification/` | 7.4 |
| `PostSessionSummaryUiState` | UiState | `ui/gamification/` | 7.4 |
| `SubmitReviewFragment` | Fragment | `ui/review/` | 7.5 |
| `ReviewUiState` | UiState | `ui/review/` | 7.5 |
| `ReportIncidentFragment` | Fragment | `ui/report/` | 7.3 |
| `ReportIncidentViewModel` | ViewModel | `ui/report/` | 7.3 |
| `ReportIncidentViewModelFactory` | DI Factory | `ui/report/` | 7.3 |
| `ReportIncidentUiState` | UiState | `ui/report/` | 7.3 |

---

## 13. Modified Class Summary

| Class | Key Change |
|---|---|
| `CreateWalkIntentRequest` | Add `description` field |
| `PushRoutePointsResponse` | Replace `syncedCount` with `List<Long> acknowledgedIds` |
| `SessionApiService` | Add `complete`, `history`, `route`, `report` endpoints |
| `WalkIntentMapper` | Map `expiresAt`; delete broken `toRequest()` factory |
| `WalkProposalMapper` | Map `expiresAt`, `proposedLat/Lng`, `myAcceptanceStatus`, `sessionId` |
| `WalkSessionMapper` | Map `scheduledEnd`, `startedAt`, `endedAt`, activation timestamps, `isReviewed` |
| `WalkIntent` | Add `expiresAt`, `description`, helper methods `isOpen()`, `isMatching()` |
| `WalkProposal` | Add `expiresAt`, `meetingLat/Lng`, `myAcceptanceStatus`, `sessionId`, helpers |
| `WalkSession` | Add 6 new fields, 3 constants, 3 helper methods |
| `WalkSessionRepository` | Add `complete`, `history`, `route`, `report` methods |
| `WalkIntentRepository` | Add `description` param to `createIntent()` |
| `TrackingRepository` | Add `triggerPeriodicSync()` |
| `WalkProposalRepository` | Change `acceptProposal()` return type to `DomainCallback<WalkProposal>` |
| `WalkSessionRepositoryImpl` | Implement 4 new methods |
| `WalkIntentRepositoryImpl` | Pass `description`; handle 204 in `findMatch()` |
| `WalkProposalRepositoryImpl` | `acceptProposal()` returns `WalkProposal` |
| `TrackingRepositoryImpl` | Fix `acknowledged_ids` parsing; add `triggerPeriodicSync()` |
| `SessionTrackingService` | Add `syncScheduler` + 30-second periodic flush |
| `MatchesViewModel` | Add `triggerMatch()`, `activateSession()`, Case A/B in `acceptProposal()`, `UserProfileRepository` injection |
| `MatchesViewModelFactory` | Add `UserProfileRepository` param |
| `TrackingViewModel` | Add `WalkSessionRepository` injection; replace `finishWalk()` with `requestCompleteWalk()` + `abortWalk()`; add 5-minute gate |
| `TrackingViewModelFactory` | Add `WalkSessionRepository` param |
| `TrackingUiState` | Add `completeTooEarlySeconds`, `isSaving` |
| `TrackingScreenActivity` | Add "Complete Walk" + "Emergency Abort" button binding |
| `ProfileViewModel` | Add `GamificationRepository` injection; load badges + stats in parallel |
| `ProfileViewModelFactory` | Add `GamificationRepository` param |
| `ProfileUiState` | Add `badges`, `stats` fields |
| `HomeViewModel` | Add `HotspotRepository`, `GamificationRepository`, `SocialRepository` injection; replace all hardcoded values |
| `HomeViewModelFactory` | Add 3 new repo params |
| `FindingAdapter` | OPEN/MATCHING state rendering; `CountdownTimerView` binding; "Find Match" button |
| `FindingFragment` | Wire "Find Match" → `triggerMatch()`; 60-second refresh handler |
| `ProposalAdapter` | `CountdownTimerView` binding; "waiting" overlay for Case A |
| `SessionAdapter` | Replace "Start Walk" with `ActivationWindowButtonView`; ACTIVE session action buttons |
| `SessionFragment` | Wire `activateSession()`; 30-second polling; Case A waiting UI |
| `WalkMateApplication` | Add getters for new factory dependencies |
| `ReviewViewModel` | Add `loadReviewState()`, `submitReview()` |
| `WalkState` | Add `FINISHING` state |
