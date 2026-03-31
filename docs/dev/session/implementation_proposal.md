# Implementation Proposal — Strava-like Walk Tracking UI
> **Branch:** `improve/coordination-flow`
> **Date:** 2026-03-30
> **Depends on:** `gap_analysis.md`

---

## Overview

The work is split into **5 phases**, each self-contained. Phases 1–3 can be done in parallel by different developers. Phase 4 depends on 1–3. Phase 5 is polish/cleanup.

```
Phase 1 — Domain & State Machine
Phase 2 — Service Lifecycle Fixes
Phase 3 — Entry-Point Wiring (SessionFragment → TrackingScreen)
Phase 4 — ViewModel: Timer + Pace + Lifecycle commands
Phase 5 — UI Layer: Stats sheet + Lifecycle controls + Design system
```

---

## Phase 1 — Domain & State Machine

**Layer:** `domain/tracking/`

### 1.1 Add `WalkState` enum

**File:** `domain/tracking/WalkState.java` *(new)*
```java
package com.walkmate.domain.tracking;

public enum WalkState {
    READY,    // Before user taps Start
    ACTIVE,   // GPS running, stopwatch ticking
    PAUSED,   // GPS stopped, stopwatch frozen, polyline intact
    FINISHED  // Walk ended; summary available
}
```

### 1.2 Extend `TrackingUiState`

**File:** `ui/tracking/TrackingUiState.java` *(modify)*

Add fields:
```java
private final WalkState walkState;       // replaces boolean isTracking
private final long elapsedSeconds;       // seconds since first Start (paused time excluded)
private final double paceMinPerKm;       // 0.0 until enough distance accumulated
private final String partnerName;        // shown in sheet header
```

Remove any boolean `isTracking` field — `walkState` replaces it entirely.

---

## Phase 2 — Service Lifecycle Fixes

**Layer:** `data/` (service classes)

### 2.1 Fix: Explicit service stop on Finish

**File:** `ui/tracking/TrackingScreenActivity.java` *(modify)*

```java
// In onDestroy() — add:
stopService(new Intent(this, WalkTrackerService.class));
```

And in the "Finish walk" handler (added in Phase 5):
```java
private void finishWalk() {
    viewModel.finishWalk();                          // transitions state to FINISHED
    stopService(new Intent(this, WalkTrackerService.class));
}
```

### 2.2 Fix: Shut down ExecutorService

**File:** `SessionTrackingService.java` *(modify)*

```java
public void stopTracking() {
    executor.shutdown();    // graceful — finishes queued writes before stopping
}
```

Call `sessionTrackingService.stopTracking()` from `WalkTrackerService.onDestroy()`.

### 2.3 Fix: Remove hardcoded session ID

**File:** `TrackingScreenActivity.java` *(modify)*

```java
// Replace hardcoded string with Intent extra:
private static final String EXTRA_SESSION_ID    = "SESSION_ID";
private static final String EXTRA_PARTNER_NAME  = "PARTNER_NAME";
private static final String EXTRA_MEETING_LAT   = "MEETING_POINT_LAT";
private static final String EXTRA_MEETING_LNG   = "MEETING_POINT_LNG";

// In onCreate():
String sessionId   = getIntent().getStringExtra(EXTRA_SESSION_ID);
String partnerName = getIntent().getStringExtra(EXTRA_PARTNER_NAME);
double lat         = getIntent().getDoubleExtra(EXTRA_MEETING_LAT, 0.0);
double lng         = getIntent().getDoubleExtra(EXTRA_MEETING_LNG, 0.0);

viewModel.startTrackingSession(sessionId, partnerName, lat, lng);

Intent serviceIntent = new Intent(this, WalkTrackerService.class);
serviceIntent.putExtra(EXTRA_SESSION_ID, sessionId);
startForegroundService(serviceIntent);
```

Remove the fallback ID generation from `WalkTrackerService.onStartCommand()`. If `SESSION_ID` is null/empty, log an error and call `stopSelf()` — this is a programming error, not a runtime case.

---

## Phase 3 — Entry-Point Wiring

**Layer:** `ui/matches/session/`

### 3.1 Add "Start Walk" button to `SessionAdapter`

**File:** `SessionAdapter.java` *(modify)*

```java
public interface OnStartWalkClickListener {
    void onStartWalkClick(WalkSession session);
}

// In ViewHolder.bind():
btnStartWalk.setOnClickListener(v -> {
    if (startWalkListener != null) startWalkListener.onStartWalkClick(session);
});
```

Show `btnStartWalk` only when `session.getStatus() == WalkSession.Status.PENDING_MEET`.

**File:** `item_session_card.xml` *(modify)*

Add a primary orange pill button labeled `@string/btn_start_walk` below the existing action row. Visible only for `PENDING_MEET` — controlled in `bind()` via `View.VISIBLE / View.GONE`.

### 3.2 Wire listener in `SessionFragment`

**File:** `SessionFragment.java` *(modify)*

```java
adapter.setOnStartWalkClickListener(session -> {
    Intent intent = new Intent(requireContext(), TrackingScreenActivity.class);
    intent.putExtra("SESSION_ID",        session.getSessionId());
    intent.putExtra("PARTNER_NAME",      session.getPartnerName());
    intent.putExtra("MEETING_POINT_LAT", session.getMeetingPointLat());
    intent.putExtra("MEETING_POINT_LNG", session.getMeetingPointLng());
    startActivity(intent);
});
```

---

## Phase 4 — ViewModel: Timer, Pace, Lifecycle Commands

**Layer:** `ui/tracking/TrackingViewModel.java`

### 4.1 New state fields

```java
private final MutableLiveData<WalkState> walkStateLiveData =
        new MutableLiveData<>(WalkState.READY);

// Stopwatch — runs on background executor, posts every second
private final MutableLiveData<Long> elapsedSecondsLiveData =
        new MutableLiveData<>(0L);

private ScheduledExecutorService timerExecutor;
private long walkStartEpochMs = 0L;
private long pausedAccumulatedMs = 0L;
private long pauseStartEpochMs = 0L;
```

### 4.2 Lifecycle command methods

```java
/** Called from Activity: user taps Start */
public void startWalk() {
    walkStartEpochMs = System.currentTimeMillis();
    walkStateLiveData.postValue(WalkState.ACTIVE);
    startTimer();
}

/** Called from Activity: user taps Pause */
public void pauseWalk() {
    pauseStartEpochMs = System.currentTimeMillis();
    walkStateLiveData.postValue(WalkState.PAUSED);
    stopTimer();
    // Signal service to stop GPS (avoids recording stationary points)
}

/** Called from Activity: user taps Resume */
public void resumeWalk() {
    pausedAccumulatedMs += System.currentTimeMillis() - pauseStartEpochMs;
    walkStateLiveData.postValue(WalkState.ACTIVE);
    startTimer();
}

/** Called from Activity: user taps Stop/Finish */
public void finishWalk() {
    stopTimer();
    walkStateLiveData.postValue(WalkState.FINISHED);
    // Persist final session summary to Room (future: trigger sync)
}
```

### 4.3 Timer implementation

```java
private void startTimer() {
    timerExecutor = Executors.newSingleThreadScheduledExecutor();
    timerExecutor.scheduleAtFixedRate(() -> {
        long activeMs = (System.currentTimeMillis() - walkStartEpochMs)
                        - pausedAccumulatedMs;
        elapsedSecondsLiveData.postValue(activeMs / 1000);
    }, 0, 1, TimeUnit.SECONDS);
}

private void stopTimer() {
    if (timerExecutor != null) {
        timerExecutor.shutdown();
        timerExecutor = null;
    }
}

@Override
protected void onCleared() {
    stopTimer();
}
```

### 4.4 Pace calculation

Inside the existing `Transformations.map` lambda that builds `TrackingUiState`, add:
```java
double pace = 0.0;
if (cachedTotalDistance > 50.0 && elapsedSeconds > 0) {
    // pace in min/km
    pace = (elapsedSeconds / 60.0) / (cachedTotalDistance / 1000.0);
}
```

### 4.5 Merged `uiStateLiveData`

Use `MediatorLiveData` to merge the route `LiveData` and the timer `LiveData` into one `TrackingUiState` emission:

```java
MediatorLiveData<TrackingUiState> uiStateLiveData = new MediatorLiveData<>();
uiStateLiveData.addSource(routeAndDistanceLiveData, routeData -> rebuildUiState());
uiStateLiveData.addSource(elapsedSecondsLiveData,   seconds   -> rebuildUiState());
uiStateLiveData.addSource(walkStateLiveData,         state     -> rebuildUiState());
```

---

## Phase 5 — UI Layer: Stats Sheet + Controls + Design System

**Layer:** `ui/tracking/`, `res/layout/activity_tracking_screen.xml`

### 5.1 Layout restructure

Replace the current `ConstraintLayout` with `CoordinatorLayout` (mirrors `fragment_explore.xml`).

```
CoordinatorLayout
  ├── FrameLayout#mapContainer          (MATCH_PARENT)
  ├── ImageButton#btnBack               (top-left, bg_white_circle, 40dp, elevation 8dp)
  ├── [FAB re-center]                   (bottom-right above sheet, bg_white_circle, 48dp)
  └── LinearLayout#statsBottomSheet     (BottomSheetBehavior, peekHeight=180dp, hideable=false)
        ├── dragHandleArea              (50dp, same as ExploreFragment)
        └── NestedScrollView
              ├── statsHeaderRow        (avatar initial + partner name)
              ├── statsRow              (3 columns: distance | duration | pace)
              └── controlsRow           (btnPause/Resume | btnStop)
```

### 5.2 Stats row spec

```xml
<!-- Three equal columns -->
<LinearLayout android:orientation="horizontal" android:weightSum="3">

    <!-- Distance -->
    <LinearLayout android:layout_weight="1" android:orientation="vertical" android:gravity="center">
        <TextView android:id="@+id/txtDistance"
            android:textSize="24sp" android:textStyle="bold" android:textColor="@color/text_dark"/>
        <TextView android:text="@string/label_km"
            android:textSize="11sp" android:textColor="@color/text_muted"/>
    </LinearLayout>

    <!-- Duration -->
    <LinearLayout android:layout_weight="1" android:orientation="vertical" android:gravity="center">
        <TextView android:id="@+id/txtDuration"
            android:textSize="24sp" android:textStyle="bold" android:textColor="@color/text_dark"/>
        <TextView android:text="@string/label_time"
            android:textSize="11sp" android:textColor="@color/text_muted"/>
    </LinearLayout>

    <!-- Pace -->
    <LinearLayout android:layout_weight="1" android:orientation="vertical" android:gravity="center">
        <TextView android:id="@+id/txtPace"
            android:textSize="24sp" android:textStyle="bold" android:textColor="@color/text_dark"/>
        <TextView android:text="@string/label_pace"
            android:textSize="11sp" android:textColor="@color/text_muted"/>
    </LinearLayout>

</LinearLayout>
```

### 5.3 Controls row spec

**ACTIVE state:**
```xml
<!-- Pause: outlined orange pill -->
<MaterialButton style="@style/Widget.Material3.Button.OutlinedButton"
    android:id="@+id/btnPause"
    android:layout_width="0dp" android:layout_weight="1"
    android:text="@string/btn_pause"
    android:textColor="@color/orange_primary"
    app:strokeColor="@color/orange_primary"
    app:cornerRadius="999dp" />

<!-- Stop: solid orange pill -->
<MaterialButton
    android:id="@+id/btnStop"
    android:layout_width="0dp" android:layout_weight="1"
    android:background="@drawable/bg_gradient_orange_pill"
    android:text="@string/btn_finish"
    android:textColor="@color/white"
    app:backgroundTint="@null"
    app:cornerRadius="999dp" />
```

**PAUSED state:** swap `btnPause` label/icon to "Resume" by calling `btnPause.setText(R.string.btn_resume)` in `renderState()`.

**READY state:** show only a single full-width **Start** button (same `bg_gradient_orange_pill` style).

### 5.4 `renderState()` switch

```java
private void renderState(TrackingUiState state) {
    // Stats always update
    txtDistance.setText(String.format(Locale.getDefault(), "%.2f", state.getDistanceKm()));
    txtDuration.setText(formatDuration(state.getElapsedSeconds()));
    txtPace.setText(state.getPaceMinPerKm() > 0
            ? String.format(Locale.getDefault(), "%.1f'", state.getPaceMinPerKm())
            : "--");
    txtPartnerName.setText(state.getPartnerName());

    // Polyline
    drawPolyline(state.getMapPoints());

    // Control visibility
    switch (state.getWalkState()) {
        case READY:
            btnStart.setVisibility(View.VISIBLE);
            btnPause.setVisibility(View.GONE);
            btnStop.setVisibility(View.GONE);
            break;
        case ACTIVE:
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            btnPause.setText(R.string.btn_pause);
            btnStop.setVisibility(View.VISIBLE);
            break;
        case PAUSED:
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            btnPause.setText(R.string.btn_resume);
            btnStop.setVisibility(View.VISIBLE);
            break;
        case FINISHED:
            showSummaryCard(state);
            break;
    }
}
```

### 5.5 Button wiring

```java
btnStart.setOnClickListener(v -> {
    viewModel.startWalk();
    // startForegroundService already called in onCreate; GPS is already ready
});

btnPause.setOnClickListener(v -> {
    WalkState ws = viewModel.getUiState().getValue().getWalkState();
    if (ws == WalkState.ACTIVE) {
        viewModel.pauseWalk();
        stopService(new Intent(this, WalkTrackerService.class));
    } else if (ws == WalkState.PAUSED) {
        viewModel.resumeWalk();
        startForegroundService(buildServiceIntent(sessionId));
    }
});

btnStop.setOnClickListener(v -> finishWalk());
```

### 5.6 Service start timing change

Move `startForegroundService()` from `onCreate()` to the `btnStart` handler. This way GPS only begins when the user explicitly starts the walk, not immediately on screen open. Update `viewModel.startTrackingSession()` signature to receive the meeting-point coordinates (for initial camera position), called in `onCreate()`.

---

## String Resources to Add

```xml
<string name="btn_start_walk">Start Walk</string>
<string name="btn_pause">Pause</string>
<string name="btn_resume">Resume</string>
<string name="btn_finish">Finish</string>
<string name="label_km">km</string>
<string name="label_time">time</string>
<string name="label_pace">min/km</string>
```

---

## Dependency Check

No new libraries required. All components used are already declared in `libs.versions.toml`:
- `BottomSheetBehavior` — `material 1.13.0` ✅
- `MediatorLiveData` — `lifecycle-livedata 2.10.0` ✅
- `ScheduledExecutorService` — JDK standard ✅
- `CoordinatorLayout` — `constraintlayout 2.2.1` (already imported transitively via material) ✅

---

## Phase Execution Order

```
Phase 1 (Domain)    ──► Phase 4 (ViewModel)  ──► Phase 5 (UI)
Phase 2 (Service)   ──┘
Phase 3 (Entry)     ──────────────────────────► Phase 5 (UI)
```

Phases 1, 2, and 3 are independent and can be developed in parallel on feature branches.
