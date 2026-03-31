# Gap Analysis — Strava-like Walk Tracking UI
> **Branch:** `improve/coordination-flow`
> **Date:** 2026-03-30
> **Author:** Senior Android Engineer / UI-UX Review

---

## 1. Current State vs. Strava-like Goal

| Dimension | Current State | Strava-like Target | Gap |
|---|---|---|---|
| **Session trigger** | Hardcoded `"test-session-123"` in `TrackingScreenActivity.onCreate()` | Opened from `SessionFragment` when user taps a card with `PENDING_MEET` status; real `sessionId` passed via Intent | ❌ No real entry-point; mismatch risk if fallback ID fires |
| **Lifecycle controls** | None — service starts on `onCreate`, never pauses or stops | Start → Pause → Resume → Stop (Finish) with explicit user intent | ❌ Missing entirely |
| **Stats overlay** | Distance `TextView` in a `CardView` | Live distance + elapsed time + current pace, all visible at a glance | ❌ Only distance; no timer or pace |
| **Map interaction** | Re-center FAB + follow toggle | Same, but styled consistently with app design system | ⚠️ Functional but unstyled |
| **Service lifecycle** | No `stopService()` call; `ExecutorService` never shut down | Service stopped explicitly when walk finishes; executor cleaned up in `onDestroy` | ❌ Resource leak |
| **Session ID coupling** | `TrackingScreenActivity` owns the hardcoded ID | ID originates from `WalkSession.sessionId` selected in Matches tab | ❌ Hardcoded |
| **Visual polish** | Default Android widgets; no brand styling | Orange/white palette, pill buttons, rounded cards matching Explore UX | ❌ No design system applied |
| **State machine** | Binary (running / not running) | Four states: `READY → ACTIVE → PAUSED → FINISHED` | ❌ Missing |

---

## 2. Trigger Mechanism — Recommended

### Entry Point
`SessionFragment` → tap on a `WalkSession` card whose `status == PENDING_MEET`.

### Why this card?
`WalkSession` already carries:
- `sessionId` — maps 1-to-1 with `route_points.sessionId` in Room
- `meetingPointLat / meetingPointLng` — camera initial position
- `partnerName` — shown in the tracking header

### Navigation contract
```
SessionAdapter.OnStartWalkClickListener.onStartWalkClick(WalkSession session)
  └─ SessionFragment → Intent(TrackingScreenActivity)
       extras:
         SESSION_ID        = session.getSessionId()
         PARTNER_NAME      = session.getPartnerName()
         MEETING_POINT_LAT = session.getMeetingPointLat()
         MEETING_POINT_LNG = session.getMeetingPointLng()
```

`TrackingScreenActivity.onCreate()` reads these extras; no fallback ID generation needed.

---

## 3. Walk Lifecycle — State Machine

### States

```
READY ──[btnStart]──► ACTIVE ──[btnPause]──► PAUSED
                         ▲                      │
                         └──────[btnResume]──────┘
                         │
                    [btnStop / Finish]
                         │
                         ▼
                      FINISHED
```

| State | GPS service | Stopwatch | Controls visible |
|---|---|---|---|
| `READY` | Stopped | Reset | **Start** |
| `ACTIVE` | Running | Ticking | **Pause**, **Stop** |
| `PAUSED` | Stopped (no new points) | Frozen | **Resume**, **Stop** |
| `FINISHED` | Stopped | Frozen | None — summary card shown |

### New field in `TrackingUiState`

```java
public enum WalkState { READY, ACTIVE, PAUSED, FINISHED }

// Added to TrackingUiState
private final WalkState walkState;
private final long elapsedSeconds;   // driven by ViewModel timer
private final double currentPaceMinPerKm;
```

---

## 4. UI Pattern Extraction — from Create Intent / Explore

### 4.1 Color Palette

| Token | Hex | Used for |
|---|---|---|
| `orange_primary` | `#FF7B3A` | Primary CTAs, active slider tracks, stroke on outlined buttons |
| `text_dark` | `#332218` | Headings, partner name |
| `text_muted` | muted grey | Labels, sub-stats |
| `white` | `#FFFFFF` | Floating button backgrounds (`bg_white_circle`), card surfaces |
| Map polyline | `#4285F4` | Existing — keep |

### 4.2 Button Shapes

| Type | Style | Usage in Explore | Apply to Tracking |
|---|---|---|---|
| Primary CTA | `bg_gradient_orange_pill`, `cornerRadius=999dp` | `btnFindMatch` | **Stop / Finish** |
| Secondary CTA | `Widget.Material3.Button.OutlinedButton`, orange stroke | `btnStopSearching` | **Pause** |
| Icon action | `bg_white_circle`, 40 dp | `btnBackToWelcome`, `btnProfile` | **Re-center**, **Back** |

### 4.3 Stats Overlay Layout
Modeled after the scanning bottom sheet + the distance CardView:
- Rounded-top sheet (`bg_sheet_top_rounded`) docked at bottom, `peekHeight ≈ 180 dp`
- Three stat columns: **Distance**, **Duration**, **Pace**
- Partner avatar initial chip at sheet top (reuses `txtAvatarInitial` pattern from `SessionAdapter`)
- Control row: [Pause | Stop] centered — same vertical rhythm as `btnFindMatch` row

### 4.4 Map Overlay
- Floating back/context button: top-left, `bg_white_circle`, `elevation=8dp` (mirrors `btnBackToWelcome`)
- Re-center FAB: bottom-right, `bg_white_circle`, 48 dp (replaces current Material FAB)
- No native My-Location button (already disabled in current code — keep)

---

## 5. Identified Service Lifecycle Bugs (from Architecture Doc)

1. **`stopService()` never called** — `WalkTrackerService` runs indefinitely after `TrackingScreenActivity` is destroyed.
2. **`ExecutorService` never shut down** in `SessionTrackingService` — thread leak.
3. **Fallback session ID** (`"test-session-" + System.currentTimeMillis()`) diverges from ViewModel's ID, producing an empty polyline.

All three must be fixed as part of this work.
