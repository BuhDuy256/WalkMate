# Implementation Proposal — UI Transition & API Integration
> **Date:** 2026-04-01
> **Scope:** All 13 gaps identified in `gap_analysis.md`
> **Architecture decisions:** Jetpack Navigation Component for all cross-screen navigation; Firebase Cloud Messaging (FCM) for match detection; no HTTP polling

---

## Dependency Graph

```
Phase 1 (Auth → Home)           ──┐
Phase 2 (Jetpack Navigation)    ──┼──► Phase 4 (Scanning + FCM consumer)
Phase 3 (FCM Client Setup)      ──┘         │
                                             ▼
                                    Phase 5 (Matches auto-nav)
Phase 6 (Search Bar)            — independent
Phase 7 (Proposal Cancel + i18n)— independent
Phase 8 (Home real data)        — independent
```

**Recommended sprint order:** Phase 1 → Phase 2 → Phase 3 → Phase 6 → Phase 7 → Phase 4 → Phase 5 → Phase 8

Phases 1, 2, 3, 6, 7, 8 can each be done independently. Phase 4 requires Phases 2 and 3. Phase 5 requires Phase 4.

---

## Phase 1 · Auth → Home Navigation
**Gaps closed:** G-1
**Estimated effort:** 30 minutes

### What to do

In `AuthActivity.java`, update `onLoginSuccess()` to launch `MainActivity` and clear the back stack so the user cannot press Back to return to login:

```java
private void onLoginSuccess() {
    Intent intent = new Intent(this, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
}
```

Call `onLoginSuccess()` from the `loginViewModel` UI state observer wherever `state.isSuccess()` returns true.

**Why `FLAG_ACTIVITY_CLEAR_TASK`:** Removes `AuthActivity` from the back stack entirely. Pressing Back from `MainActivity` will exit the app, not return to login.

---

## Phase 2 · Jetpack Navigation Component Setup
**Gaps closed:** G-7 (tight coupling), G-13
**Estimated effort:** 2–3 hours

### 2a. Add dependencies

In `frontend/build.gradle.kts` add:
```kotlin
val navVersion = "2.7.7"
implementation("androidx.navigation:navigation-fragment:$navVersion")
implementation("androidx.navigation:navigation-ui:$navVersion")
```

### 2b. Create `res/navigation/nav_graph.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/homeFragment">

    <fragment android:id="@+id/homeFragment"
        android:name="com.walkmate.ui.home.HomeFragment"
        android:label="Home" />

    <fragment android:id="@+id/exploreFragment"
        android:name="com.walkmate.ui.explore.ExploreFragment"
        android:label="Explore">
        <argument android:name="openSetupDirectly"
            app:argType="boolean"
            android:defaultValue="false" />
    </fragment>

    <fragment android:id="@+id/matchesFragment"
        android:name="com.walkmate.ui.matches.MatchesFragment"
        android:label="Matches">
        <argument android:name="scrollToTab"
            app:argType="integer"
            android:defaultValue="0" />
        <deepLink app:uri="walkmate://matches/proposal/{proposalId}" />
    </fragment>

    <fragment android:id="@+id/profileFragment"
        android:name="com.walkmate.ui.profile.ProfileFragment"
        android:label="Profile" />
</navigation>
```

### 2c. Update `MainActivity` layout

Replace the current FrameLayout/container approach with a `NavHostFragment`:

```xml
<!-- In activity_main.xml -->
<androidx.fragment.app.FragmentContainerView
    android:id="@+id/nav_host_fragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintBottom_toTopOf="@id/bottom_nav"
    app:defaultNavHost="true"
    app:navGraph="@navigation/nav_graph" />
```

### 2d. Wire `BottomNavigationView` in `MainActivity`

Each menu item's `android:id` must match the corresponding fragment ID in `nav_graph.xml` (e.g. `R.id.homeFragment`, `R.id.matchesFragment`, etc.).

```java
// MainActivity.onCreate()
NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
        .findFragmentById(R.id.nav_host_fragment);
NavController navController = navHostFragment.getNavController();
NavigationUI.setupWithNavController(bottomNav, navController);
```

Remove all manual `showTab()`, `switchToExplore()`, and `scrollMatchesToTab()` methods from `MainActivity`. These are replaced by `NavController.navigate()` calls from within each Fragment.

### 2e. Replace all Activity-casting navigation

Any Fragment currently doing:
```java
((MainActivity) requireActivity()).switchToExplore();
```
should be replaced with:
```java
Bundle args = new Bundle();
args.putBoolean("openSetupDirectly", true);
Navigation.findNavController(requireView()).navigate(R.id.exploreFragment, args);
```

And any Fragment navigating to Matches:
```java
((MainActivity) requireActivity()).scrollMatchesToTab(TAB_PROPOSAL);
```
becomes:
```java
Bundle args = new Bundle();
args.putInt("scrollToTab", MatchesPagerAdapter.TAB_PROPOSAL);
Navigation.findNavController(requireView()).navigate(R.id.matchesFragment, args);
```

`MatchesFragment.onViewCreated()` reads the argument and calls `scrollToSubTab()` if it is non-zero.

---

## Phase 3 · Firebase Cloud Messaging (FCM) Client Setup
**Gaps closed:** G-4, G-12
**Estimated effort:** 2–3 hours

### 3a. Firebase project setup

1. Add `google-services.json` to `frontend/` (download from Firebase Console after creating/linking the project).
2. In `frontend/build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services")
   }
   dependencies {
       implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
       implementation("com.google.firebase:firebase-messaging")
   }
   ```
3. In the root `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services") version "4.4.1" apply false
   }
   ```

### 3b. Create `AppEventBus`

A singleton that lets `WalkMateFcmService` post events which foreground ViewModels can observe:

```java
// com.walkmate.core.event.AppEventBus
public class AppEventBus {
    private static AppEventBus instance;
    private final MutableLiveData<AppEvent> events = new MutableLiveData<>();

    public static AppEventBus get() {
        if (instance == null) instance = new AppEventBus();
        return instance;
    }

    public LiveData<AppEvent> observe() { return events; }

    public void post(AppEvent event) {
        // Post on main thread (called from background FCM thread)
        new Handler(Looper.getMainLooper()).post(() -> events.setValue(event));
    }
}
```

```java
// com.walkmate.core.event.AppEvent
public class AppEvent {
    public enum Type { MATCH_FOUND }
    public final Type type;
    public final String intentId;
    public final String proposalId;

    public AppEvent(Type type, String intentId, String proposalId) {
        this.type = type;
        this.intentId = intentId;
        this.proposalId = proposalId;
    }
}
```

### 3c. Create `WalkMateFcmService`

```java
// com.walkmate.service.WalkMateFcmService
public class WalkMateFcmService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        // Register the FCM token with the backend so it can push to this device.
        UserRepository userRepo = UserRepositoryFactory.create(this);
        userRepo.updateFcmToken(token, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {}
            @Override public void onError(String message) {}
        });
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String type = data.get("type");

        if ("MATCH_FOUND".equals(type)) {
            String intentId   = data.get("intentId");
            String proposalId = data.get("proposalId");
            AppEventBus.get().post(
                    new AppEvent(AppEvent.Type.MATCH_FOUND, intentId, proposalId));
        }
    }
}
```

### 3d. Register in `AndroidManifest.xml`

```xml
<service
    android:name="com.walkmate.service.WalkMateFcmService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

### 3e. Backend requirements

The backend must:
1. Accept `PATCH /api/v1/users/me/fcm-token` (or equivalent) to store the device token per user.
2. Send an FCM push with a **`data`-only payload** (not `notification`) when a match is found:
   ```json
   {
     "type": "MATCH_FOUND",
     "intentId": "<uuid>",
     "proposalId": "<uuid>"
   }
   ```
   Using a `data`-only payload ensures `onMessageReceived()` is called even when the app is in the foreground.

---

## Phase 4 · Scanning Flow: FCM Consumer + Timeout + Cancel Fix
**Gaps closed:** G-4, G-5, G-6
**Depends on:** Phase 2 (NavController), Phase 3 (FCM + AppEventBus)
**Estimated effort:** 3–4 hours

### 4a. Store the created intent in `ExploreViewModel`

```java
// ExploreViewModel
private String activeIntentId = null;

public void onIntentCreated(WalkIntent intent) {
    activeIntentId = intent.getId();   // ← store the ID
    post(new ExploreUiState(false, s.getHotspots(), s.getSelectedHotspot(),
            AppState.SCANNING, null));
    startScanningTimeout();
}
```

### 4b. 10-second timeout with `Handler`

Use a plain `Handler` on the main thread — no executor, no threading complexity:

```java
private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
private static final long TIMEOUT_MS = 10_000;

private final Runnable timeoutRunnable = () -> {
    if (currentState().getAppState() == AppState.SCANNING) {
        post(currentState().withScanTimedOut(true));
    }
};

private void startScanningTimeout() {
    timeoutHandler.removeCallbacks(timeoutRunnable);
    timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
}

private void cancelScanningTimeout() {
    timeoutHandler.removeCallbacks(timeoutRunnable);
}

@Override
protected void onCleared() {
    cancelScanningTimeout();
    AppEventBus.get().observe().removeObserver(appEventObserver);
    super.onCleared();
}
```

Add a `scanTimedOut` boolean to `ExploreUiState`. `ExploreFragment` observes it and shows the bottom-sheet when it becomes `true`.

### 4c. Observe FCM events in `ExploreViewModel`

```java
// In ExploreViewModel constructor
AppEventBus.get().observe().observeForever(appEventObserver);

private final Observer<AppEvent> appEventObserver = event -> {
    if (event == null) return;
    if (event.type == AppEvent.Type.MATCH_FOUND
            && event.intentId.equals(activeIntentId)) {
        cancelScanningTimeout();
        post(currentState().withMatchFound(event.proposalId));
    }
};
```

### 4d. Navigate to Matches on match found

`ExploreFragment` observes `uiState`. When `state.getMatchFoundProposalId() != null`:

```java
if (state.getMatchFoundProposalId() != null) {
    Bundle args = new Bundle();
    args.putInt("scrollToTab", MatchesPagerAdapter.TAB_PROPOSAL);
    Navigation.findNavController(requireView())
              .navigate(R.id.matchesFragment, args);
    viewModel.consumeMatchFound();
}
```

### 4e. Fix `btnStopSearching` — cancel the backend intent

```java
// ExploreFragment
btnStopSearching.setOnClickListener(v -> viewModel.stopSearching());
```

```java
// ExploreViewModel
public void stopSearching() {
    cancelScanningTimeout();
    if (activeIntentId != null) {
        intentRepository.cancelIntent(activeIntentId, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {}
            @Override public void onError(String message) {}
        });
        activeIntentId = null;
    }
    resetToWelcome();
}
```

### 4f. "Save to Finding List" dialog

When `ExploreFragment` observes `state.isScanTimedOut() == true`:

```java
new MaterialAlertDialogBuilder(requireContext())
    .setTitle("Still looking…")
    .setMessage("No match found yet. Keep your search active while you explore?")
    .setPositiveButton("Keep Searching", (d, w) -> viewModel.dismissTimeout())
    .setNegativeButton("Save to Finding List", (d, w) -> {
        viewModel.dismissTimeout();
        Bundle args = new Bundle();
        args.putInt("scrollToTab", MatchesPagerAdapter.TAB_FINDING);
        Navigation.findNavController(requireView())
                  .navigate(R.id.matchesFragment, args);
    })
    .setCancelable(false)
    .show();
```

`viewModel.dismissTimeout()` resets `scanTimedOut` to `false` so the dialog does not re-appear on rotation.

---

## Phase 5 · Matches Auto-Navigation
**Gaps closed:** G-7 (auto-scroll)
**Depends on:** Phase 4
**Estimated effort:** 1 hour

### 5a. Read `scrollToTab` argument in `MatchesFragment`

```java
// MatchesFragment.onViewCreated()
int scrollToTab = getArguments() != null ? getArguments().getInt("scrollToTab", 0) : 0;
if (scrollToTab > 0) {
    viewPager.post(() -> scrollToSubTab(scrollToTab));
}
```

### 5b. Auto-scroll to Proposal tab when proposals exist

```java
matchesViewModel.getProposals().observe(getViewLifecycleOwner(), proposals -> {
    if (!proposals.isEmpty() && !hasAutoScrolledToProposal) {
        hasAutoScrolledToProposal = true;
        scrollToSubTab(MatchesPagerAdapter.TAB_PROPOSAL);
    }
});
```

### 5c. After `acceptProposal()` → scroll to Session tab

`MatchesViewModel` exposes a `SingleLiveEvent<Integer> scrollToTabEvent`. After accept succeeds, post `TAB_SESSION`. `MatchesFragment` observes it and calls `scrollToSubTab()`.

---

## Phase 6 · Search Bar (Client-Side Hotspot Filter)
**Gaps closed:** G-3
**Estimated effort:** 1.5 hours

### What to do

`ExploreViewModel` already loads hotspots from `GET /api/v1/hotspots`. Add a `filterHotspots(String query)` method:

```java
public void filterHotspots(String query) {
    List<Hotspot> all = currentState().getHotspots();
    if (query == null || query.isEmpty()) {
        post(currentState().withFilteredHotspots(all));
        return;
    }
    String lower = query.toLowerCase();
    List<Hotspot> filtered = new ArrayList<>();
    for (Hotspot h : all) {
        if (h.getName().toLowerCase().contains(lower)) filtered.add(h);
    }
    post(currentState().withFilteredHotspots(filtered));
}
```

In `ExploreFragment.bindViews()`, bind the search `EditText` and attach a `TextWatcher` that calls `viewModel.filterHotspots(s.toString())` on `onTextChanged`. The hotspot chip list and map markers re-render whenever `filteredHotspots` changes in `ExploreUiState`.

---

## Phase 7 · Proposal Cancel + i18n Fixes
**Gaps closed:** G-8, G-11
**Estimated effort:** 2 hours

### 7a. Add Cancel to ProposalAdapter

Add a "Cancel" button to the proposal card layout. Extend the adapter callback:

```java
public interface ProposalActionListener {
    void onPass(String proposalId);
    void onAccept(String proposalId);
    void onCancel(String proposalId);   // ← new
}
```

### 7b. Add `cancelProposal()` to `MatchesViewModel`

```java
public void cancelProposal(String proposalId) {
    proposalRepository.cancelProposal(proposalId, new DomainCallback<Void>() {
        @Override public void onSuccess(Void result) { refreshProposals(); }
        @Override public void onError(String message) { errorEvent.postValue(message); }
    });
}
```

**Backend requirement:** `DELETE /api/v1/proposals/{id}` — hard cancel that removes the proposal and closes the parent intent. Coordinate with backend team before wiring.

### 7c. Fix Vietnamese strings

In `res/values/strings.xml`, update:

| Key | New value |
|---|---|
| `home_quick_invite_header` | `"Quick Invite"` |
| `gender_male` | `"Male"` |
| `gender_female` | `"Female"` |
| `gender_any` | `"Any"` |
| `tag_running` | `"Running"` |
| `tag_podcast` | `"Podcast"` |
| `tag_dog` | `"Dog Walk"` |
| `tag_meditation` | `"Meditation"` |
| `tag_slow_walk` | `"Slow Walk"` |
| `tag_photography` | `"Photography"` |

### 7d. Fix Vietnamese code comments

Replace the 6 Vietnamese comments in `ExploreFragment.java` (lines 301, 309, 310, 314, 531, 535) with their English equivalents as listed in G-11a of the gap analysis.

---

## Phase 8 · Home Real Data
**Gaps closed:** G-9
**Estimated effort:** 3 hours

### What to do

Replace `buildReadyState()` in `HomeViewModel` with 5 parallel repository calls. Use an `AtomicInteger` latch to fire a single `HomeUiState` post once all calls complete:

```java
private void loadHomeData() {
    AtomicInteger pending = new AtomicInteger(5);
    String[] name         = {""};
    boolean[] hasUnread   = {false};
    int[] streak          = {0};
    int[] nearbyCount     = {0};
    List<QuickInviteItem>[] invites = new List[]{Collections.emptyList()};

    Runnable checkDone = () -> {
        if (pending.decrementAndGet() == 0) {
            post(HomeUiState.ready(name[0], hasUnread[0], streak[0],
                    nearbyCount[0], invites[0]));
        }
    };

    userRepository.getProfile(new DomainCallback<UserProfile>() {
        @Override public void onSuccess(UserProfile p) { name[0] = p.getFirstName(); checkDone.run(); }
        @Override public void onError(String msg)      { checkDone.run(); }
    });
    notificationRepository.hasUnread(new DomainCallback<Boolean>() {
        @Override public void onSuccess(Boolean b) { hasUnread[0] = b; checkDone.run(); }
        @Override public void onError(String msg)  { checkDone.run(); }
    });
    gamificationRepository.getStreak(new DomainCallback<Integer>() {
        @Override public void onSuccess(Integer s) { streak[0] = s; checkDone.run(); }
        @Override public void onError(String msg)  { checkDone.run(); }
    });
    hotspotRepository.getNearbyCount(new DomainCallback<Integer>() {
        @Override public void onSuccess(Integer c) { nearbyCount[0] = c; checkDone.run(); }
        @Override public void onError(String msg)  { checkDone.run(); }
    });
    socialRepository.getQuickInvites(new DomainCallback<List<QuickInviteItem>>() {
        @Override public void onSuccess(List<QuickInviteItem> l) { invites[0] = l; checkDone.run(); }
        @Override public void onError(String msg)                { checkDone.run(); }
    });
}
```

All five calls fire immediately. The final post fires exactly once, from whichever callback finishes last.

---

## Acceptance Criteria

| Phase | Done when… |
|---|---|
| 1 | Login success → `MainActivity` appears; pressing Back exits the app |
| 2 | `BottomNavigationView` uses `NavController`; zero `((MainActivity)` casts remain in any Fragment |
| 3 | FCM token sent to backend on first launch; `MATCH_FOUND` data payload triggers `AppEventBus` post |
| 4 | 10 s in SCANNING → dialog appears; "Save" → Matches Finding tab; "Stop" → `cancelIntent` called; FCM match → Proposal tab |
| 5 | Opening Matches with pending proposals auto-selects Proposal tab; `acceptProposal` auto-selects Session tab |
| 6 | Typing in search bar filters hotspot chips in real time |
| 7 | Cancel on proposal card hard-cancels on backend and removes the card; 10 string resources are in English |
| 8 | Home dashboard shows real name, streak, hotspot count, invite list from API; no hardcoded values remain |
