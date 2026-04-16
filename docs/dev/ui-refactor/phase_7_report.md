# Phase 7 Report — Walk Intent Feature
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 3.2 (expiresAt countdown on intents), 3.5 (MATCHING vs OPEN differentiation), 3.6 (findMatch exposed in VM), 3.7 (204 No Content handling)

---

## Layout Changes — `item_finding_card.xml`

Three additions to the existing card layout:

| Addition | Position | ID |
|---|---|---|
| `ImageView` (lock icon) | Row 1 header, between hotspot name and status chip | `@+id/imgLock` |
| `CountdownTimerView` | Below `txtTimeWindow`, above chips row | `@+id/countdownTimer` |
| `MaterialButton` (primary action) | Above `btnCancelIntent` | `@+id/btnFindMatch` |

- Lock icon uses `@drawable/ic_lock`, `visibility="gone"` by default.
- `CountdownTimerView` uses `wm_urgentThresholdSec="300"` (5 min) and `wm_urgentColor="@color/color_danger"`.
- `btnCancelIntent` margin-top changed from `14dp` to `8dp` (now secondary below `btnFindMatch`).

Two string resources added to `strings.xml`:
- `btn_view_proposal` — "View Proposal"
- `no_match_found_toast` — "No match found yet. You'll be notified when one is found!"

---

## FindingAdapter Changes

### New interface: `OnIntentActionListener`
```java
public interface OnIntentActionListener {
    void onFindMatchClicked(String intentId);
    void onViewProposalClicked(String intentId);
}
```
Registered via `setOnIntentActionListener(listener)`. Existing `OnCancelClickListener` unchanged.

### ViewHolder — new fields
- `CountdownTimerView countdown`
- `ImageView imgLock`
- `MaterialButton btnFindMatch`

### `bind()` — OPEN vs MATCHING logic
```java
if (intent.isMatching()) {
    btnFindMatch.setText(R.string.btn_view_proposal);
    btnFindMatch → actionListener.onViewProposalClicked(intent.getId())
    btnCancelIntent.setVisibility(GONE)
    imgLock.setVisibility(VISIBLE)
} else { // OPEN
    btnFindMatch.setText(R.string.find_match)
    btnFindMatch → actionListener.onFindMatchClicked(intent.getId())
    btnCancelIntent.setVisibility(VISIBLE)
    imgLock.setVisibility(GONE)
    btnCancelIntent → cancelListener.onCancelClick(intent)
}
```

Countdown: `startCountdown(expiresAt)` if non-null (VISIBLE); `cancelCountdown()` + GONE otherwise.

### `onViewRecycled()` — countdown cancel (spec requirement)
```java
@Override
public void onViewRecycled(@NonNull ViewHolder holder) {
    super.onViewRecycled(holder);
    holder.countdown.cancelCountdown();
}
```

---

## MatchesViewModel Changes

### `noMatchFoundEvent` (gap 3.7)
```java
private final MutableLiveData<Boolean> noMatchFoundEvent = new MutableLiveData<>(null);
public LiveData<Boolean> getNoMatchFoundEvent() { ... }
public void consumeNoMatchFoundEvent() { noMatchFoundEvent.postValue(null); }
```

### `triggerMatch(String intentId)` (gap 3.6)
Calls `intentRepository.findMatch()`:
- **Case A** (`result != null`): proposal created → `loadAll()` then scroll to `TAB_PROPOSAL`.
- **Case B** (`result == null`): 204 No Content → `noMatchFoundEvent.postValue(true)`.
- **Error**: posts error message to `uiState`.

### `navigateToTab(int tabIndex)`
Public convenience method posts directly to the existing `scrollToTabEvent`. Used by `FindingFragment` for the "View Proposal" button.

---

## FindingFragment Changes

### `OnIntentActionListener` wired
- `onFindMatchClicked` → `matchesViewModel.triggerMatch(intentId)`
- `onViewProposalClicked` → `matchesViewModel.navigateToTab(MatchesPagerAdapter.TAB_PROPOSAL)`
- `onIntentExpired` → `matchesViewModel.loadAll()` (see correction below)

### `noMatchFoundEvent` observed
```java
matchesViewModel.getNoMatchFoundEvent().observe(getViewLifecycleOwner(), noMatch -> {
    if (noMatch != null && noMatch) {
        Toast.makeText(requireContext(), R.string.no_match_found_toast, Toast.LENGTH_LONG).show();
        matchesViewModel.consumeNoMatchFoundEvent();
    }
});
```

### Countdown expiry handling (correction applied post-review)
The original implementation used a 60-second `Handler` loop calling `adapter.notifyDataSetChanged()`. This was removed as redundant and performance-harmful — `CountdownTimerView` ticks internally.

Instead, `setOnExpiredListener` is called in `FindingAdapter.bind()`. When a timer fires, the adapter calls `actionListener.onIntentExpired()` → `matchesViewModel.loadAll()`. The expired intent is removed from the server-side list and the UI refreshes cleanly.

---

## Gap Closure Summary

| Gap | Description | Status |
|---|---|---|
| 3.2 | `WalkIntent.expiresAt` countdown on intent cards | ✅ Closed |
| 3.5 | MATCHING vs OPEN states differentiated in `FindingAdapter` | ✅ Closed |
| 3.6 | `findMatch()` exposed via `MatchesViewModel.triggerMatch()` | ✅ Closed |
| 3.7 | 204 No Content handled as "no match yet" toast, not error | ✅ Closed |
