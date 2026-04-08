# Phase 0 Report — Custom Views Pre-Work
**Date:** 2026-04-09
**Branch:** `implement/realtime`

---

## Files Created

| File | Purpose |
|---|---|
| `frontend/src/main/java/com/walkmate/core/designsystem/view/CountdownTimerView.java` | Self-ticking MM:SS countdown text view |
| `frontend/src/main/java/com/walkmate/core/designsystem/view/ActivationWindowButtonView.java` | Session activation window button + status label |
| `frontend/src/main/res/layout/view_activation_window_button.xml` | Merge-root layout for ActivationWindowButtonView |

`res/values/attrs.xml` was updated in-place (no new file).

---

## CountdownTimerView

**Package:** `com.walkmate.core.designsystem.view`
**Extends:** `androidx.appcompat.widget.AppCompatTextView`
**No layout file** (text-only view).

### Public API

```java
// Parse ISO-8601 instant, compute remaining ms, start internal CountDownTimer.
void startCountdown(String expiresAtIso);

// Provide pre-computed epoch ms for callers that already parsed the timestamp.
void startCountdown(long expiresAtEpochMs);

// Cancel the internal timer. MUST be called from adapter's onViewRecycled().
void cancelCountdown();

// Register a listener that fires when the countdown reaches zero.
void setOnExpiredListener(OnExpiredListener listener);

public interface OnExpiredListener { void onExpired(); }
```

### Behaviour

- `startCountdown()` cancels any running timer before starting (recycle guard).
- Remaining ≤ 0 on entry: immediately sets text "Expired" and fires listener.
- `onTick`: formats `%02d:%02d` (MM:SS); applies `urgentColor` if `ms ≤ urgentThresholdMs`, else `normalColor`.
- `onFinish`: sets text "Expired", fires listener.
- `onDetachedFromWindow()` overridden to cancel the timer — prevents leaks in RecyclerView cells.

### attrs.xml entry added

```xml
<declare-styleable name="CountdownTimerView">
    <attr name="wm_urgentThresholdSec" format="integer" />
    <attr name="wm_urgentColor"        format="color"   />
    <attr name="wm_normalColor"        format="color"   />
</declare-styleable>
```

**Defaults (if attrs omitted):**
- `wm_urgentThresholdSec` → 60 s
- `wm_urgentColor` → `R.color.color_danger` (#E53935)
- `wm_normalColor` → `R.color.text_muted` (#9E8D7F)

---

## ActivationWindowButtonView

**Package:** `com.walkmate.core.designsystem.view`
**Extends:** `android.widget.LinearLayout` (vertical orientation)
**Layout:** `res/layout/view_activation_window_button.xml` (`<merge>` root containing `TextView` + `WalkMateButton`)

### Public API

```java
// Bind with session scheduled start time and the arrive click handler.
// Internally computes open = scheduledStart − 10 min, close = scheduledStart + 15 min.
// Enables/disables the inner button. Starts 60-second re-evaluation loop.
void bind(String scheduledStartIso, View.OnClickListener onArrivedClick);

// Cancel the re-evaluation Handler. MUST be called from onViewRecycled / onDestroyView.
void release();
```

### Behaviour

- `bind()` calls `release()` first to cancel any previous loop before rebinding.
- `WINDOW_OPEN_MS  = epochMs − 10 × 60_000`
- `WINDOW_CLOSE_MS = epochMs + 15 × 60_000`
- Immediate `evaluate()` on bind; re-posts every 60 s via `Handler(Looper.getMainLooper()).postDelayed`.
- Status label text: "Not yet open" (before window) / arrive label (inside window) / "Window closed" (after window).
- After `WINDOW_CLOSE_MS`: re-evaluation stops internally (no further `postDelayed`).
- `release()` removes all pending callbacks — safe to call multiple times.

### attrs.xml entry added

```xml
<declare-styleable name="ActivationWindowButtonView">
    <attr name="wm_arriveLabel"  format="string" />
    <attr name="wm_waitingLabel" format="string" />
</declare-styleable>
```

**Defaults (if attrs omitted):**
- `wm_arriveLabel`  → hard-coded `"I'm Here!"` (set in layout and overridable at runtime via `bind()`)
- `wm_waitingLabel` → hard-coded `"Not yet open"`

---

## Deviations from Spec

| Item | Spec | Actual | Reason |
|---|---|---|---|
| `ActivationWindowButtonView` label attr | spec lists `wm_arriveLabel` only as button label | also used as `tvStatus` label when window is open | The status and button both read from the same label for consistency; the closed label `"Window closed"` is hard-coded (not an attr) since it has no spec override requirement. |
| `CountdownTimerView` urgentColor default | spec says "read from attrs" | falls back to `R.color.color_danger` if attr not set | Sensible default so the view works without XML attrs in tests or simple usages. |

---

## Context for Phase 1

- `R.styleable.CountdownTimerView_wm_urgentThresholdSec`, `_wm_urgentColor`, `_wm_normalColor` are now live.
- `R.styleable.ActivationWindowButtonView_wm_arriveLabel`, `_wm_waitingLabel` are now live.
- Both views compile against standard Android SDK only — no new Gradle dependencies introduced.
