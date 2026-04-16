# Optimization Decisions — Hotspot Search / Filter Phase

## 1. `filteredHotspots` stored in `ExploreUiState`, not separately in ViewModel

**Decision:** Added `filteredHotspots` as a first-class field in `ExploreUiState` alongside the existing `hotspots` field.

**Why:** Keeping both lists in the immutable `UiState` means the Fragment always has a single, consistent snapshot to render — no race condition between "full list" and "filtered list" that could arise if they lived in separate LiveData objects. It also means `withFilteredHotspots()` is a pure builder call with zero side-effects, fully aligned with the MVVM / immutable-state contract documented in `Frontend_VI.md §4`.

**Trade-off:** Each `filterHotspots()` call allocates a new `ExploreUiState` + a new `ArrayList`. For the expected hotspot count (< 50) this is negligible; no pooling or diffing needed.

---

## 2. `drawHotspotMarkers` receives `boolean fitCamera` instead of always moving the camera

**Decision:** Added a `fitCamera` parameter. Camera is only fitted on the very first draw (`lastRenderedFilteredHotspots == null`). Subsequent filter-driven redraws do **not** move the camera.

**Why:** Without this guard, typing a single character would animate the camera to fit the remaining markers — a jarring UX. The original camera-fit behaviour is preserved for the initial load; after that the user's manual pan/zoom is respected.

---

## 3. Change-detection via `hasFilteredHotspotsChanged()` — ID-sequence comparison

**Decision:** Instead of re-drawing markers on every `renderState` call, a helper compares the current filtered ID sequence against `lastRenderedFilteredHotspots`. Only when the sequence actually differs is a new `drawHotspotMarkers` call issued.

**Why:** `renderState` is called for every LiveData emission (e.g., error consumed, selection changed). Re-drawing on each call would flash markers unnecessarily and cause `googleMap.clear()` to briefly blank the map. The ID-sequence check is O(n) but the list is small.

**Note:** This also covers the "reset filter → show all markers" path automatically: when the user clears the search box `filterHotspots("")` posts `hotspots` as the filtered list, the sequence changes, and all markers are redrawn.

---

## 4. `populateHotspotChips` always called in WELCOME — removed the `!isEmpty()` guard

**Decision:** Removed the `if (!state.getHotspots().isEmpty())` guard and now call `populateHotspotChips(state.getFilteredHotspots())` unconditionally in the WELCOME case.

**Why:** When the user types a query that matches nothing, `filteredHotspots` is empty. The old guard would leave stale chips from the previous query visible. Calling unconditionally (with an empty list) clears the chip group, giving correct feedback that no results matched.

---

## 5. TextWatcher attached in `setupListeners()`, no explicit removal in `onDestroyView`

**Decision:** The `TextWatcher` lambda is registered directly on `searchInputEdit`. No stored reference is removed in `onDestroyView`.

**Why:** `searchInputEdit` is a view that is fully destroyed when the Fragment's view hierarchy is torn down. The EditText (and its list of TextWatchers) is garbage-collected at that point. The lambda captures only `viewModel`, which is a `ViewModel` that safely outlives the view — calling `viewModel.filterHotspots()` after the view is gone has no effect (no LiveData observer is registered). No memory-leak path exists here.
