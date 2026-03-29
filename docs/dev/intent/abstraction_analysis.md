# Abstraction Analysis: Current State vs. New Re-Design

---

## Part 1 — Current UI & Workflow (Abstracted)

### The Mental Model

The current implementation treats the entire "walk coordination" flow as a **single, modal Activity** (`CoordinationActivity`). The screen is permanently a full-screen Google Map. Everything else — sheets, overlays, dialogs — layers *on top* of that map. There is no concept of switching screens or tabs; the user is always "inside" the Coordination activity.

The flow is driven by a single `AppState` enum inside `CoordinationViewModel`. It acts as a state machine with exactly five steps in a linear sequence:

```
IDLE → HOTSPOT_SELECTED → CREATE_INTENT → MATCHING → MATCH_RESULT
```

Each transition is triggered by a user action or timer, and `CoordinationActivity` renders the appropriate fragment/view on top of the map for each state.

### The 5-State Machine (Current)

| State | What is rendered | How to enter | How to exit |
|---|---|---|---|
| `IDLE` | Map + markers only | App start / reset | Tap a marker |
| `HOTSPOT_SELECTED` | Map + bottom CTA card (hotspot name + "Set Walking Intent" button) | Tap marker | Tap "Set Walking Intent" or tap map |
| `CREATE_INTENT` | Bottom Sheet Dialog over map (Time slider, Age slider, Duration chips, Gender chips, Tags chips) + dim overlay | Tap "Set Walking Intent" | Submit form or tap dim |
| `MATCHING` | Pulsing pill overlay at center of screen over map + dim | Form submitted successfully | 3-second fake timer fires |
| `MATCH_RESULT` | Centered dialog card (Avatar, Name, Tags, Time, Pass/Accept buttons) + dim | Timer fires | Tap Accept or Pass (both go to IDLE) |

### Sub-Feature Architecture (Current)

Under `ui/coordination/`:
- `CoordinationActivity` — host, state machine controller, map manager
- `CoordinationViewModel` — drives AppState, owns Hotspot loading
- `createintent/` — `BottomSheetDialogFragment` + its own ViewModel for API call
- `matching/` — `DialogFragment` + ViewModel with a 3-second `Thread.sleep()` timer
- `matchresult/` — `DialogFragment` + ViewModel that holds Accept/Pass action state

### Data Flow (Current)

```
User taps marker → CoordinationViewModel.selectHotspot()
User taps "Set Walking Intent" → CoordinationViewModel.openCreateIntent()
User taps "Find Match" (form submit) → CreateIntentViewModel.submit() → API → callback to CoordinationActivity.onIntentCreated() → CoordinationViewModel.onIntentCreated()
3-second timer fires → MatchingOverlayFragment.listener.onMatchTimerComplete() → CoordinationViewModel.onMatchTimerComplete()
User taps Accept/Pass → MatchResultFragment listener → CoordinationViewModel.resetToIdle()
```

The `WalkIntent` domain object is created and passed around as a callback, but after `MATCHING` state it is never consumed again. The `MATCH_RESULT` dialog shows **hardcoded/placeholder data**; it has no connection to a real server-matched profile.

---

## Part 2 — Gap Analysis: Current vs. New Re-Design

### Structural Gap: Single Activity vs. 3-Tab App

This is the **most fundamental gap**. The new design introduces a persistent **Bottom Tab Bar** with three tabs (`Explore`, `Matches`, `Profile`). The current code has zero concept of this. `CoordinationActivity` maps roughly to only **Tab 1 (Explore)**, but it is implemented as a standalone Activity — not a fragment suitable for hosting inside a tab.

| Dimension | Current | New Design | Gap |
|---|---|---|---|
| Global shell | None (single Activity IS the screen) | `MainActivity` with `BottomNavigationView` | Must be created from scratch |
| Tab 1 | `CoordinationActivity` (fullscreen) | `ExploreFragment` (tab content) | Activity → Fragment conversion |
| Tab 2 | Does not exist | `MatchesFragment` with 3 sub-tabs | Must be built from scratch |
| Tab 3 | Does not exist | `ProfileFragment` (blocked/placeholder) | Must be built from scratch |

---

### Feature-Level Gap Map

#### Tab 1: Explore

| Current Element | Status | Notes |
|---|---|---|
| `IDLE` state → map with markers | **Keep** | Becomes Explore State 1 (Welcome) |
| `HOTSPOT_SELECTED` → bottom CTA card (`hotspotCtaCard` in `activity_coordination.xml`) | **Deprecate** | This simplistic card is replaced by the full "Create Intent" Bottom Sheet in State 2 (Setup). The card's `txtHotspotName` + `btnSetIntent` flow collapses into the new bottom sheet header |
| `CREATE_INTENT` → `fragment_create_intent.xml` Bottom Sheet | **Keep & Promote** | The form content is identical to the new design. Must be promoted from a `BottomSheetDialogFragment` to a persistent embedded Bottom Sheet (non-modal) to allow map interaction behind it |
| `MATCHING` → `fragment_matching_overlay.xml` center pill | **Redesign** | New design shows a "Floating Card" anchored at the top of the map + radar animation on map. The current centered pill and dim overlay are wrong. The overlay must be transparent (no dim). |
| `MATCH_RESULT` AppState | **Deprecate** | This entire AppState is removed from the Explore screen. A match result no longer blocks the map as a modal dialog. |
| `dimOverlay` in `activity_coordination.xml` | **Deprecate for Explore** | In the new design, Explore State 3 (Scanning) is non-blocking; no dim needed. The dim may be reused only in a welcome/setup state. |
| `AppState` enum in `CoordinationUiState` | **Refactor** | Remove `MATCH_RESULT`. Rename to `ExploreUiState`. States become: `WELCOME`, `SETUP`, `SCANNING`. |

#### Tab 2: Matches

| Current Element | Status | Notes |
|---|---|---|
| `fragment_match_result.xml` (dialog) | **Migrate & Promote** | The Pass/Accept UI card is kept but **promoted from a modal dialog to a full Fragment** inside the `Proposal` sub-tab |
| `MatchResultFragment` | **Migrate** | Becomes `ProposalFragment`, a regular `Fragment` (not `DialogFragment`) hosted in the Matches tab |
| `MatchResultViewModel` | **Migrate & Expand** | Needs to load real Proposal data from a `WalkProposalRepository` instead of showing hardcoded data |
| Finding sub-tab | **Does not exist** | New: A list view showing active `WalkIntent` entries in OPEN/WAITLIST state with a "Cancel" action |
| Session sub-tab | **Does not exist** | New: Shows confirmed `WalkSession` with map, chat, and Cancel Session button |

---

### Domain Gap: Missing `WalkProposal` and `WalkSession`

The current `domain/` layer only has `WalkIntent`. The new design introduces two distinct lifecycle stages *after* an Intent is created:

| Domain Object | Current | New Design | Gap |
|---|---|---|---|
| `WalkIntent` | Exists in `domain/walkintent/` | Tab 2 Finding sub-tab | **Keep**; it is correctly defined |
| `WalkProposal` | Does not exist (implicit in `MATCH_RESULT` state) | Tab 2 Proposal sub-tab | **Must create** as a dedicated domain |
| `WalkSession` | Does not exist | Tab 2 Session sub-tab | **Must create** as a dedicated domain |

The entire `MATCH_RESULT` state in the current code is a **fake, timer-triggered placeholder** for what should be a server-driven `WalkProposal` pushed via WebSocket or polling. The domain does not model this correctly.

---

### Summary: Keep / Redesign / Deprecate / Build

| Item | Action |
|---|---|
| Google Map setup, marker drawing, `zoomToHotspot`, `createMarkerIcon` logic | **Keep** (move to `ExploreFragment`) |
| `fragment_create_intent.xml` (all form fields) | **Keep** (move to embedded Bottom Sheet in Explore) |
| `CreateIntentViewModel`, `CreateIntentUiState`, `CreateIntentViewModelFactory` | **Keep** (move to `ui/explore/createintent/`) |
| Pulse animation code in `MatchingOverlayFragment` | **Keep** (reuse in Explore Scanning state) |
| `CoordinationActivity` as the root screen | **Deprecate** → becomes `ExploreFragment` |
| `hotspotCtaCard` in `activity_coordination.xml` | **Deprecate** |
| `fragment_matching_overlay.xml` (centered pill + dim) | **Redesign** → floating card anchored to top, no full-screen dim |
| `MATCH_RESULT` `AppState`, `MatchResultFragment` as a modal dialog | **Deprecate** → re-home to `ui/matches/proposal/` as a regular Fragment |
| `MatchingViewModel`'s 3-second `Thread.sleep` | **Deprecate** → replace with real backend polling/WebSocket |
| `WalkProposal` domain | **Build from scratch** |
| `WalkSession` domain | **Build from scratch** |
| `MainActivity` shell + `BottomNavigationView` | **Build from scratch** |
| `MatchesFragment` + 3 sub-tab architecture | **Build from scratch** |
| `ProfileFragment` (stub) | **Build from scratch** |
