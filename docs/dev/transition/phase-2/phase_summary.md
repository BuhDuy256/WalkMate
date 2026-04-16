# Phase Summary — Hotspot Search / Filter

## What was implemented

- [x] `ExploreUiState` — added `filteredHotspots` field (defaults to `hotspots`) and `withFilteredHotspots(List<Hotspot>)` builder method; added `getFilteredHotspots()` getter; added private full-constructor to support the builder pattern without touching the existing public constructor.
- [x] `ExploreViewModel` — added `filterHotspots(String query)`: case-insensitive name-substring filter; empty/null query resets to the full list; posts new state via `withFilteredHotspots()`.
- [x] `ExploreFragment` — bound `searchInputEdit` (`TextInputEditText`) in `bindViews()`; attached `TextWatcher` in `setupListeners()` that calls `viewModel.filterHotspots(s.toString())` on `onTextChanged`.
- [x] `ExploreFragment.renderState()` — replaced one-shot marker draw with `hasFilteredHotspotsChanged()` change-detection; redraws markers whenever `filteredHotspots` changes; `fitCamera=true` only on initial load.
- [x] `ExploreFragment` WELCOME case — chip population now uses `state.getFilteredHotspots()` (no stale-chip issue when filter produces zero results).
- [x] `drawHotspotMarkers` signature changed to `drawHotspotMarkers(List<Hotspot>, boolean fitCamera)`.
- [x] `onMapReady` updated to draw from `filteredHotspots` and set `lastRenderedFilteredHotspots`.

## Files modified

| File | Change |
|---|---|
| `ui/explore/ExploreUiState.java` | Added `filteredHotspots` field, private constructor, `withFilteredHotspots()`, `getFilteredHotspots()` |
| `ui/explore/ExploreViewModel.java` | Added `filterHotspots(String)`, added `ArrayList` import |
| `ui/explore/ExploreFragment.java` | Bound `searchInputEdit`, added `TextWatcher`, added `lastRenderedFilteredHotspots` tracking, updated marker draw + chip render to use `filteredHotspots`, added `hasFilteredHotspotsChanged()` helper |

**Layout unchanged** — `searchInputEdit` (`R.id.searchInputEdit`) already existed in `fragment_explore.xml`.

## States / variables the next phase must be aware of

| Item | Detail |
|---|---|
| `ExploreUiState.filteredHotspots` | Always non-null; equals `hotspots` when no search query is active. Use `getFilteredHotspots()` (not `getHotspots()`) whenever rendering a subset of hotspots. |
| `ExploreUiState.hotspots` | Still the canonical full list from the API. Use this when you need the complete dataset (e.g., finding a hotspot by ID in `selectHotspot()`). |
| `filterHotspots()` scope | Only affects `filteredHotspots`. Transitioning to SETUP/SCANNING via `selectHotspot()` / `onIntentCreated()` does **not** preserve the filter — `closeSetup()` and `resetToWelcome()` use the public constructor which resets `filteredHotspots` to `hotspots`. |
| `lastRenderedFilteredHotspots` | Fragment-level field. Reset to `null` only when the Fragment view is recreated. No ViewModel involvement. |
| Map marker set | `markerByHotspotId` and `hotspotById` reflect only the **currently drawn** (filtered) hotspots. After a filter redraw, markers for excluded hotspots are removed from the map and from these maps. |
