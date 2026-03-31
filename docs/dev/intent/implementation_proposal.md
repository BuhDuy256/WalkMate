# Implementation Proposal: New Walk Intent & Proposal Architecture

---

## Architectural Problem 1 — Domain Driven Design: WalkProposal

### Decision: Yes, extract a dedicated `WalkProposal` domain.

**Reasoning:**
A `WalkProposal` is a *distinct lifecycle entity* from a `WalkIntent`. An Intent means "I want to walk" (user-controlled, OPEN/WAITLIST state). A Proposal means "the system found you a candidate" (server-controlled, PENDING state). Collapsing them into one domain object (as the current `MATCH_RESULT` AppState implicitly does) violates DDD because it forces a single object to represent two fundamentally different business states with different ownership, data, and transitions.

**Domain objects to create/confirm:**

```
domain/
├── walkintent/
│   ├── WalkIntent.java          ← KEEP. Represents user's search criteria.
│   ├── WalkIntentRepository.java ← KEEP (add: listActiveIntents, cancelIntent)
│   ├── WalkIntentErrorCode.java  ← KEEP
│   └── WalkIntentService.java    ← KEEP
│
├── walkproposal/               ← CREATE NEW
│   ├── WalkProposal.java        ← New model: proposalId, intentId, matchedUserId, matchedUserName,
│   │                               matchedUserAge, matchedUserAvatar, trustScore, overlappingTags,
│   │                               overlappingTimeWindow, status (PENDING/CONFIRMED/REJECTED)
│   ├── WalkProposalRepository.java  ← Interface: getProposals(), acceptProposal(id), passProposal(id)
│   ├── WalkProposalErrorCode.java
│   └── WalkProposalService.java
│
└── walksession/                ← CREATE NEW
    ├── WalkSession.java         ← New model: sessionId, proposalId, partnerUserId, meetingPoint,
    │                               scheduledTime, status (PENDING_MEET/ACTIVE/CANCELLED/COMPLETED)
    ├── WalkSessionRepository.java  ← Interface: getActiveSessions(), cancelSession(id, reason)
    ├── WalkSessionErrorCode.java
    └── WalkSessionService.java
```

**How `WalkProposal` connects to the flow:**

```
[Backend] detects a match for intentId
    → Updates Intent status to MATCHED
    → Creates a WalkProposal record (status = PENDING)
    → Pushes WebSocket event OR client polls endpoint

[Frontend] WalkProposalRepository.getProposals() returns a List<WalkProposal>
    → MatchesViewModel posts new state: proposalList is non-empty
    → ProposalFragment renders the first proposal card

User clicks Accept →  WalkProposalRepository.acceptProposal(proposalId)
    → Backend runs atomic transaction: CONFIRMED on both sides
    → Backend creates WalkSession
    → Frontend polls or receives push: session now exists
    → MatchesViewModel moves card to Session sub-tab

User clicks Pass → WalkProposalRepository.passProposal(proposalId)
    → Backend marks Proposal as REJECTED, re-opens Intent (WAITLIST)
    → Frontend removes proposal card, Intent reappears in Finding sub-tab
```

---

## Architectural Problem 2 — Navigation Architecture: Bottom Tab Bar

### Decision: `MainActivity` hosts a `BottomNavigationView` with 3 top-level `Fragment`s. No `ViewPager`. No `NavGraph` for top-level tabs (to stay lightweight). Sub-tabs in Matches use a `TabLayout` + `ViewPager2`.

**Reasoning:** The project standard forbids heavy frameworks. The Navigation Component is optional here — manual `FragmentTransaction` driven by `BottomNavigationView.setOnItemSelectedListener` is lean, predictable, and matches the existing codebase style perfectly.

### Host Layout: `activity_main.xml`

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout>

    <!-- Tab content area — fragments are swapped here -->
    <FrameLayout
        android:id="@+id/tabContentContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginBottom="?attr/actionBarSize" />  <!-- reserve space for nav bar -->

    <!-- Persistent Bottom Navigation -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        app:menu="@menu/menu_bottom_nav" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### `menu/menu_bottom_nav.xml`

```xml
<menu>
    <item android:id="@+id/tab_explore"  android:title="Explore"  android:icon="@drawable/ic_map" />
    <item android:id="@+id/tab_matches"  android:title="Matches"  android:icon="@drawable/ic_people" />
    <item android:id="@+id/tab_profile"  android:title="Profile"  android:icon="@drawable/ic_person" />
</menu>
```

### `MainActivity.java` — Tab Routing Logic

```java
// In onCreate():
bottomNav.setOnItemSelectedListener(item -> {
    int id = item.getItemId();
    if (id == R.id.tab_explore)  showTab(ExploreFragment.TAG);
    else if (id == R.id.tab_matches) showTab(MatchesFragment.TAG);
    else if (id == R.id.tab_profile) showTab(ProfileFragment.TAG);
    return true;
});
// Default tab on launch:
showTab(ExploreFragment.TAG);
```

```java
private void showTab(String tag) {
    FragmentManager fm = getSupportFragmentManager();
    FragmentTransaction ft = fm.beginTransaction();
    // Hide all tabs, show the requested one (create if first time)
    for (String t : new String[]{ExploreFragment.TAG, MatchesFragment.TAG, ProfileFragment.TAG}) {
        Fragment f = fm.findFragmentByTag(t);
        if (f != null) ft.hide(f);
    }
    Fragment target = fm.findFragmentByTag(tag);
    if (target == null) {
        target = createFragmentForTag(tag);
        ft.add(R.id.tabContentContainer, target, tag);
    } else {
        ft.show(target);
    }
    ft.commitNow();
}
```

**Why `hide/show` instead of `replace`:** Preserves each tab's state (map position, list scroll, etc.) across tab switches without re-creating fragments.

### `MatchesFragment` — Sub-Tab Layout (`fragment_matches.xml`)

```xml
<LinearLayout android:orientation="vertical">

    <!-- Sub-tab bar -->
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/subTabLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:tabMode="fixed" />

    <!-- Sub-tab content -->
    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/subTabPager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

`MatchesPagerAdapter` wires three Fragments: `FindingFragment` (pos 0), `ProposalFragment` (pos 1), `SessionFragment` (pos 2). `TabLayoutMediator` links the `TabLayout` to the `ViewPager2`.

**Deep-linking to a sub-tab:** When a push notification arrives (e.g., new Proposal), `MainActivity` calls `bottomNav.setSelectedItemId(R.id.tab_matches)`, then `MatchesFragment.scrollToSubTab(PROPOSAL_INDEX)`. This avoids any cross-Activity intent.

---

## Step-by-Step Implementation Plan

### Phase A — Global Shell (Do First, Everything Depends On This)

**A1. Create `MainActivity`**
- File: `ui/main/MainActivity.java`
- Inflates `activity_main.xml`
- Implements `hide/show` tab routing (code above)
- Registers as the launcher Activity in `AndroidManifest.xml` (replace or add alongside current entry)

**A2. Create `activity_main.xml`**
- `FrameLayout` (`tabContentContainer`) + `BottomNavigationView`
- Create `menu/menu_bottom_nav.xml` (3 items)

**A3. Create `ProfileFragment` stub**
- File: `ui/profile/ProfileFragment.java`
- Layout: `fragment_profile.xml` — a simple centered TextView "Coming Soon"
- No ViewModel needed at this phase

---

### Phase B — Tab 1: Refactor CoordinationActivity → ExploreFragment

**B1. Rename package and convert**
- Create `ui/explore/ExploreFragment.java` (extends `Fragment`, NOT AppCompatActivity)
- Move all logic from `CoordinationActivity.java` into `ExploreFragment.java`:
  - `onCreateView` → inflate a new `fragment_explore.xml` (content of current `activity_coordination.xml`)
  - Map setup using `getChildFragmentManager()` (not `getSupportFragmentManager()`)
  - `setupViewModel()`, `renderState()`, all map helpers, all animation helpers → copy as-is

**B2. Create `fragment_explore.xml`**
- Copy content from `activity_coordination.xml` **exactly**
- Remove `android:paddingTop="48dp"` from the TopBar — the global TopBar design may move to `MainActivity`; for now keep it

**B3. Delete the `hotspotCtaCard` from the explore layout and state machine**
- In `fragment_explore.xml`: Remove the entire `hotspotCtaCard` `LinearLayout` block
- In `ExploreUiState.java` (renamed from `CoordinationUiState`): Remove `HOTSPOT_SELECTED` state
- New `AppState` enum: `WELCOME`, `SETUP`, `SCANNING`

**B4. Add Welcome Bottom Sheet state**
- Create `fragment_welcome_sheet.xml`:
  - "Ready to walk, [Name]?" greeting text
  - Search bar (`TextInputLayout`)
  - Horizontal `RecyclerView` or `HorizontalScrollView` of 5 Hotspot chips
- Integrate as an **embedded** (non-modal) bottom sheet using `BottomSheetBehavior` directly on a `LinearLayout` anchored to the bottom of `fragment_explore.xml`
- `ExploreViewModel.onHotspotSelected()` transitions `WELCOME → SETUP`

**B5. Promote Create Intent sheet from modal to embedded**
- `CreateIntentBottomSheetFragment` is currently a `BottomSheetDialogFragment` (modal, blocks map)
- Replace with: include `fragment_create_intent.xml` directly inside the bottom sheet `LinearLayout` in `fragment_explore.xml`, shown/hidden by `BottomSheetBehavior` state
- The form content (`sliderTime`, `sliderAge`, `chipGroupDuration`, `chipGroupGender`, `chipGroupTags`, `btnFindMatch`) is **kept as-is**
- `CreateIntentViewModel`, `CreateIntentUiState`, `CreateIntentViewModelFactory` → move to `ui/explore/createintent/` (rename package only)
- Delete `CreateIntentBottomSheetFragment.java` after migration

**B6. Redesign Scanning state (State 3)**
- In `fragment_explore.xml`: Add a floating card anchored **top** of the map (not center):
  ```xml
  <LinearLayout
      android:id="@+id/scanningFloatingCard"
      android:layout_gravity="top|center_horizontal"
      android:layout_marginTop="80dp"
      android:background="@drawable/bg_white_pill"
      android:visibility="gone">
      <!-- Pulse view + "Scanning near [Hotspot]..." text -->
  </LinearLayout>
  ```
- The bottom sheet collapses to peek height (not gone) in `SCANNING` state
- Reuse pulse `ObjectAnimator` code from `MatchingOverlayFragment` directly in `ExploreFragment`
- Remove dim overlay activation during `SCANNING`
- Delete `MatchingOverlayFragment.java` and `fragment_matching_overlay.xml` after migration

**B7. Update `ExploreViewModel` (renamed from `CoordinationViewModel`)**
- Remove `HOTSPOT_SELECTED`, `MATCHING`, `MATCH_RESULT` states
- Add `WELCOME`, `SETUP`, `SCANNING` states
- `onIntentCreated()`: transitions to `SCANNING` state, then the intent is "handed off" to `MatchesViewModel` (see Phase C) — `ExploreViewModel` does NOT own the intent lifecycle after submission
- `MatchingViewModel` class becomes unnecessary — delete it after the `Thread.sleep` timer is removed
- `MatchResultViewModel` class moves to `ui/matches/proposal/` (Phase C)

---

### Phase C — Tab 2: Build Matches Feature from scratch

**C1. Create domain objects**
- `domain/walkproposal/WalkProposal.java` — fields: `proposalId`, `intentId`, `matchedUserId`, `matchedUserName`, `matchedUserAge`, `trustScore`, `overlappingTags (List<String>)`, `overlappingTimeStart`, `overlappingTimeEnd`, `status (enum: PENDING, CONFIRMED, REJECTED)`
- `domain/walkproposal/WalkProposalRepository.java` (interface):
  ```java
  void getProposals(DomainCallback<List<WalkProposal>> callback);
  void acceptProposal(String proposalId, DomainCallback<WalkSession> callback);
  void passProposal(String proposalId, DomainCallback<Void> callback);
  ```
- `domain/walksession/WalkSession.java` — fields: `sessionId`, `proposalId`, `partnerName`, `partnerAvatar`, `meetingPointLat`, `meetingPointLng`, `scheduledTime`, `status (enum: PENDING_MEET, ACTIVE, CANCELLED, COMPLETED)`
- `domain/walksession/WalkSessionRepository.java` (interface):
  ```java
  void getActiveSessions(DomainCallback<List<WalkSession>> callback);
  void cancelSession(String sessionId, String reason, DomainCallback<Void> callback);
  ```
- Add stub `data/repository/WalkProposalRepositoryImpl.java` and `data/repository/WalkSessionRepositoryImpl.java`

**C2. Create `MatchesFragment`**
- File: `ui/matches/MatchesFragment.java`
- Layout: `fragment_matches.xml` with `TabLayout` + `ViewPager2` (see layout above)
- Create `MatchesPagerAdapter.java` under `ui/matches/` with 3 fragments
- Wire `TabLayoutMediator` for labels: "Finding", "Proposal", "Session"
- `MatchesFragment` has a `MatchesViewModel` that owns `List<WalkIntent>` (for Finding sub-tab), `List<WalkProposal>` (for Proposal sub-tab), and `List<WalkSession>` (for Session sub-tab)
- The three sub-tab fragments **share** the same `MatchesViewModel` via `ViewModelProvider(requireParentFragment())` — this is the single source of truth

**C3. Create `MatchesViewModel` and `MatchesUiState`**
- File: `ui/matches/MatchesViewModel.java`
- Owns repositories: `WalkIntentRepository`, `WalkProposalRepository`, `WalkSessionRepository`
- Polls or refreshes all three lists on `loadAll()`
- `MatchesUiState.java`:
  ```java
  public class MatchesUiState {
      private final boolean isLoading;
      private final List<WalkIntent> activeIntents;      // Finding sub-tab
      private final List<WalkProposal> proposals;         // Proposal sub-tab
      private final List<WalkSession> activeSessions;     // Session sub-tab
      private final String error;
  }
  ```

**C4. Create `FindingFragment`**
- File: `ui/matches/finding/FindingFragment.java`
- Layout: `fragment_finding.xml` — `RecyclerView` of intent cards
- Each card shows: Hotspot name, time window, duration chip, age range, tags, status badge, "Cancel" button
- On "Cancel": calls `MatchesViewModel.cancelIntent(intentId)` → `WalkIntentRepository.cancelIntent()`
- Observes `MatchesUiState.getActiveIntents()`

**C5. Create `ProposalFragment` (migrated from `MatchResultFragment`)**
- File: `ui/matches/proposal/ProposalFragment.java` (regular `Fragment`, NOT `DialogFragment`)
- Layout: `fragment_proposal.xml` — reuse the card content from `fragment_match_result.xml`:
  - Avatar (`ImageView`) + Name + Trust Score
  - Overlapping Tags chips (`ChipGroup`)
  - Overlapping Time window (`TextView`)
  - Pass / Accept buttons
- Add a field for the matched user's Age
- Remove the `FrameLayout` wrapper (no longer a dialog; it's a list item or full-screen card)
- `ProposalViewModel` replaces `MatchResultViewModel`:
  - Calls `MatchesViewModel.acceptProposal(proposalId)` or `passProposal(proposalId)`
  - Does NOT own its own repository — delegates upward to `MatchesViewModel`
- The "Pass" action: ViewModel calls `passProposal()` → on success, `MatchesViewModel` removes from proposal list AND re-fetches intents (the Intent reappears in Finding)
- The "Accept" action: ViewModel calls `acceptProposal()` → on success, card moves from Proposal list to Session list; `ViewPager2` can switch to Session tab

**C6. Create `SessionFragment`**
- File: `ui/matches/session/SessionFragment.java`
- Layout: `fragment_session.xml`:
  - Small `MapView` or static map image showing meeting point
  - Partner info card (Avatar, Name, Trust Score)
  - Chat stub (a `TextView` placeholder "Chat — Coming Soon" for this phase)
  - "Cancel Session" button
- On "Cancel Session": show a dialog with reason chips → calls `MatchesViewModel.cancelSession(sessionId, reason)`
- Observes `MatchesUiState.getActiveSessions()`

---

### Phase D — Cleanup & Wiring

**D1. Update `AndroidManifest.xml`**
- Set `MainActivity` as the launcher
- Keep `CoordinationActivity` temporarily for reference (remove at the end of the phase)

**D2. Delete deprecated files (after migration is verified)**
- `CoordinationActivity.java`
- `CoordinationViewModel.java`, `CoordinationUiState.java`, `CoordinationViewModelFactory.java`
- `MatchingOverlayFragment.java`, `MatchingViewModel.java`, `MatchingUiState.java`, `MatchingViewModelFactory.java`
- `MatchResultFragment.java`, `MatchResultUiState.java`, `MatchResultViewModel.java`
- `activity_coordination.xml`
- `fragment_matching_overlay.xml`

**D3. Cross-tab communication (Intent → Matches)**
After `ExploreFragment` submits a `WalkIntent`, the `Matches` tab should show it in the Finding sub-tab. There are two clean options:
- **Option A (Recommended):** `ExploreViewModel` calls `WalkIntentRepository.createIntent()` → on success, `MatchesViewModel` is refreshed next time the user switches tabs (lazy load)
- **Option B (Real-time):** Store a shared `LiveData<WalkIntent>` in a singleton held at `WalkMateApplication` level that both ViewModels observe

Option A is simpler and aligns with the project's laziness principle. Option B is only needed if you want the badge count on the Matches tab to update instantly.

**D4. Badge on Matches tab icon**
- `MainActivity` observes a lightweight `LiveData<Integer>` (unread proposal count) from a singleton held in `WalkMateApplication`
- When `MatchesViewModel` detects new proposals, it posts to this shared counter
- `MainActivity` updates the badge: `bottomNav.getOrCreateBadge(R.id.tab_matches).setNumber(count)`

---

## Final Package Structure (Target State)

```
ui/
├── main/
│   └── MainActivity.java
├── explore/
│   ├── ExploreFragment.java
│   ├── ExploreViewModel.java
│   ├── ExploreViewModelFactory.java
│   ├── ExploreUiState.java
│   └── createintent/
│       ├── CreateIntentViewModel.java      ← moved here
│       ├── CreateIntentViewModelFactory.java
│       └── CreateIntentUiState.java
├── matches/
│   ├── MatchesFragment.java
│   ├── MatchesPagerAdapter.java
│   ├── MatchesViewModel.java
│   ├── MatchesViewModelFactory.java
│   ├── MatchesUiState.java
│   ├── finding/
│   │   └── FindingFragment.java
│   ├── proposal/
│   │   ├── ProposalFragment.java
│   │   ├── ProposalViewModel.java
│   │   └── ProposalUiState.java
│   └── session/
│       ├── SessionFragment.java
│       ├── SessionViewModel.java
│       └── SessionUiState.java
└── profile/
    └── ProfileFragment.java

domain/
├── walkintent/       ← exists, keep
├── walkproposal/     ← NEW
├── walksession/      ← NEW
└── hotspot/          ← exists, keep

res/layout/
├── activity_main.xml             ← NEW
├── fragment_explore.xml          ← NEW (from activity_coordination.xml)
├── fragment_create_intent.xml    ← KEEP (no changes to form fields)
├── fragment_welcome_sheet.xml    ← NEW
├── fragment_matches.xml          ← NEW
├── fragment_finding.xml          ← NEW
├── fragment_proposal.xml         ← MIGRATED (from fragment_match_result.xml)
├── fragment_session.xml          ← NEW
└── fragment_profile.xml          ← NEW (stub)
```

---

## Hard Constraints Checklist (per Architecture Standard)

| Constraint | This Plan |
|---|---|
| No RxJava / Coroutines | All async via `ExecutorService` in ViewModels |
| No Hilt/Dagger | Manual DI via `WalkMateApplication` for new Repositories |
| No MVI (`UiEvent`/`UiEffect`) | Pure `LiveData<UiState>` + direct ViewModel method calls |
| Fragment cannot include Activity layout | `ExploreFragment` gets its own `fragment_explore.xml` |
| DTO cannot leak to `ui/` | `WalkProposalRepositoryImpl` maps DTO → `WalkProposal` via `data/mapper/WalkProposalMapper` |
| View cannot touch API/DB | All API calls remain in `Repository` → `ViewModel` → `Fragment` chain |
