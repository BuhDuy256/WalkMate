# Gap Analysis — UI Transition & API Integration
> **Date:** 2026-04-01
> **Scope:** Post-login navigation, Create Intent flow, Matching Phase, Centralized Matches tab, Cancellation wiring, Home/Profile pages, Vietnamese text, FCM, Jetpack Navigation
> **Reference:** `docs/dev/session/gap_analysis.md` (structure template)

---

## How to Read This Document

Each gap is rated by **severity**:

| Severity | Meaning |
|---|---|
| 🔴 Critical | Feature is broken or completely absent; blocks the user journey |
| 🟡 Major | Feature is partially implemented but has a significant missing step |
| 🟢 Minor | Works but contains a code quality or UX issue |

---

## G-1 · Post-Login Navigation to MainActivity
**Severity:** 🔴 Critical

**File:** `ui/auth/AuthActivity.java` — `onLoginSuccess()`

**Current behavior:**
```java
public void onLoginSuccess() {
    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    // ↑ Activity does NOT navigate anywhere.
}
```
After a successful login, the user sees a toast and stays on the `AuthActivity`. `MainActivity` is never started.

**Desired behavior:** User lands on the Home Page (`MainActivity`) immediately after login.

**Root cause:** `onLoginSuccess()` was likely a placeholder that was never completed.

---

## G-2 · Create Intent: Entry Point Is Not Exclusive
**Severity:** 🟡 Major

**Files:** `ui/main/MainActivity.java`, `ui/explore/ExploreFragment.java`

**Current behavior:**
- Home "Find a WalkMate Now" button → `MainActivity.switchToExplore()` → shows `ExploreFragment` in WELCOME state (map view). The user must then tap a hotspot chip or map marker to reach the SETUP (Create Intent form) state. This is a two-step path.
- The Explore tab is also independently reachable via the bottom nav (`R.id.tab_explore` → `showTab(ExploreFragment.TAG)`), which means a user can reach the Create Intent form without ever going through the Home CTA. This contradicts the requirement that the Home CTA is the *only* trigger.

**Desired behavior:** The Home button is the sole entry point. Tapping "Find a WalkMate Now" should place the user directly in the create-intent flow (either at the SETUP state immediately, or at WELCOME with clear intent to proceed).

**Note:** The bottom nav `tab_explore` currently routes to `HomeFragment.TAG` (Phase B implementation detail in `MainActivity`). This means the nav bar item is already called "Explore" but shows the Home fragment — `ExploreFragment` is only shown by the drill-down. However there is still no guard preventing direct `showTab(ExploreFragment.TAG)` calls from bypassing Home.

---

## G-3 · Search Bar Is Not Functional
**Severity:** 🔴 Critical

**Files:** `ui/explore/ExploreFragment.java`, `res/values/strings.xml`

**Current behavior:**
- `strings.xml` defines `search_place_hint = "Search a place…"` and a search bar likely exists in `fragment_explore.xml`.
- `ExploreFragment.bindViews()` does **not** bind any search `EditText`. There is no autocomplete listener, no `TextWatcher`, and no backend call connected to the search bar.
- The search bar is purely decorative — it accepts no input that produces a result.

**Desired behavior:** The search bar should show location/hotspot recommendations powered by the backend `GET /api/v1/hotspots` endpoint (for pre-defined hotspots) and optionally a place-autocomplete API.

**API status:** `GET /api/v1/hotspots` exists and returns a flat list. A server-side fuzzy search endpoint (e.g. `GET /api/v1/hotspots?q=tao+dan`) is **not specified** in `api-specification.md`. Client-side filtering against the already-loaded hotspot list is the feasible near-term approach.

---

## G-4 · No Push Notification Infrastructure for Match Detection
**Severity:** 🔴 Critical

**Files:** `ui/explore/ExploreViewModel.java`, `ui/explore/createintent/CreateIntentViewModel.java`

**Current behavior:**
```java
// ExploreViewModel.onIntentCreated()
public void onIntentCreated(WalkIntent intent) {
    // Transitions to SCANNING state only — no match check.
    post(new ExploreUiState(false, s.getHotspots(), s.getSelectedHotspot(), AppState.SCANNING, null));
}
```
After a successful `POST /api/v1/intents`, the UI enters SCANNING state and shows a pulsing animation. There is **no mechanism** to detect when a match is found:
- No Firebase Cloud Messaging (FCM) client is set up in the project — there is no `WalkMateFcmService` class, no `google-services.json`, and `firebase-messaging` is not declared as a Gradle dependency.
- No `FirebaseMessagingService` subclass exists to receive push payloads.
- No Application-level shared event LiveData exists to broadcast an incoming FCM message to the foreground UI.

**Desired behavior:**
1. The backend sends an FCM push to the device when a match is found, with a `data` payload containing `type=MATCH_FOUND`, `intentId`, and `proposalId`.
2. `WalkMateFcmService.onMessageReceived()` receives the payload and posts it to a shared `AppEventBus` LiveData observable.
3. `ExploreViewModel` observes `AppEventBus` and, upon `MATCH_FOUND`, transitions to the matched state and triggers navigation to the Proposal tab.

**Infrastructure missing:**
- `google-services.json` in `app/`
- `com.google.gms:google-services` Gradle plugin
- `com.google.firebase:firebase-messaging` dependency
- `WalkMateFcmService extends FirebaseMessagingService`
- `<service android:name=".service.WalkMateFcmService">` in `AndroidManifest.xml`
- `AppEventBus` Application-scoped singleton with `MutableLiveData<AppEvent>`

---

## G-5 · No 10-Second Timeout or "Keep Finding" Prompt
**Severity:** 🔴 Critical

**Files:** `ui/explore/ExploreFragment.java`, `ui/explore/ExploreViewModel.java`

**Current behavior:**
- SCANNING state runs forever until the user taps `btnStopSearching`.
- `btnStopSearching` calls `viewModel.resetToWelcome()` which:
  1. Returns to WELCOME state.
  2. Does **not** navigate to the Matches tab.
  3. Does **not** call any backend cancellation API.
  4. Does **not** offer a "Move to Finding List" prompt.

**Desired behavior:**
1. After 10 seconds in SCANNING with no match, show a dialog/bottom-sheet:
   > *"No match found yet. Keep your search active while you explore?"*
   > **[ Keep Searching ]** &nbsp;|&nbsp; **[ Save to Finding List ]**
2. Tapping "Save to Finding List" → navigate to `MatchesFragment`, auto-scroll to Finding sub-tab.
3. Tapping "Keep Searching" → dismiss and continue SCANNING.

**UX copy suggestion for the action button:** `"Save to Finding List"` (replaces the vague "Move to Finding List"). The confirmation message should be:
> *"Your search is saved. We'll notify you the moment a match is found."*

---

## G-6 · btnStopSearching Does Not Cancel the Backend Intent
**Severity:** 🔴 Critical

**Files:** `ui/explore/ExploreFragment.java`, `ui/explore/ExploreViewModel.java`

**Current behavior:**
```java
btnStopSearching.setOnClickListener(v -> viewModel.resetToWelcome());
// ↑ Only resets local UI state. The WalkIntent remains OPEN on the backend.
```

**Desired behavior:** Tapping "Stop Searching" should call `intentRepository.cancelIntent(intentId)` before resetting to WELCOME. The created intent's ID must be stored in the ViewModel to make this call possible.

**Root cause:** `ExploreViewModel` does not retain the `WalkIntent` object returned by `CreateIntentViewModel`. The `onIntentCreated(WalkIntent intent)` method receives the intent but does not store it.

---

## G-7 · No Automatic Tab Switch and No Jetpack Navigation Component
**Severity:** 🟡 Major

**Files:** `ui/matches/MatchesFragment.java`, `ui/main/MainActivity.java`, all Fragments that navigate cross-tab

**Current behavior:**
- `MatchesFragment.scrollToSubTab(int index)` exists and can programmatically switch to the Proposal tab.
- Nothing ever calls `scrollToSubTab()`. There is no notification listener, no LiveData observer, and no push event that triggers it.
- When a user navigates to the Matches tab manually, they land on the Finding sub-tab (index 0) by default, even if proposals are waiting on tab index 1.
- Cross-screen navigation is implemented via direct Activity casting:
  ```java
  ((MainActivity) requireActivity()).switchToExplore();
  ((MainActivity) requireActivity()).scrollMatchesToTab(TAB_PROPOSAL);
  ```
  This creates tight coupling between fragments and the host Activity, making the navigation graph impossible to visualize, test in isolation, or extend without modifying `MainActivity`.
- **Jetpack Navigation Component is not installed.** There is no `nav_graph.xml`, no `NavHostFragment`, and `NavigationUI` is not used. `build.gradle` does not declare `androidx.navigation:navigation-fragment` or `navigation-ui`.

**Desired behavior:**
- A `nav_graph.xml` defines all destinations (Home, Explore, Matches, Profile) and actions between them.
- `BottomNavigationView` is wired to `NavController` via `NavigationUI.setupWithNavController()`.
- All cross-screen navigations use `NavController.navigate(R.id.action_...)` — no Activity casting.
- After `acceptProposal()` succeeds → `NavController` navigates to Matches, auto-scrolls to Session sub-tab.
- When the Matches tab is opened and `proposals.size() > 0` → auto-scroll to Proposal sub-tab.
- Deep-link from FCM notification → `NavDeepLink` in `nav_graph.xml` routes to the correct destination.

---

## G-8 · No Cancel Action on Proposal Tab
**Severity:** 🟡 Major

**Files:** `ui/matches/proposal/ProposalFragment.java`, `ui/matches/proposal/ProposalAdapter.java`, `ui/matches/MatchesViewModel.java`

**Current behavior:**
- `ProposalFragment` has two actions: **Pass** (soft decline, keeps searching) and **Accept**.
- There is no **Cancel** button on the Proposal tab.
- `MatchesViewModel` has `passProposal()` but no `cancelProposal()`.

**Desired behavior:**
- A "Cancel" button on each proposal card should call a backend cancellation endpoint and remove both the proposal and its parent intent from the system.
- `passProposal` (soft pass) and "Cancel" (hard cancel) serve different backend operations and must be kept separate.

**API status:** `POST /api/v1/proposals/{id}/pass` and `POST /api/v1/proposals/{id}/accept` are implied by the repository interface. A hard-cancel endpoint (e.g. `DELETE /api/v1/proposals/{id}`) is not confirmed in `api-specification.md`. Requires backend clarification.

---

## G-9 · HomeViewModel Uses All Hardcoded/Mock Data
**Severity:** 🟡 Major

**File:** `ui/home/HomeViewModel.java`

**Current behavior:**
```java
// buildReadyState() — hardcoded placeholders:
"Alex",              // greetingName — needs userRepo.getProfile()
"Ho Chi Minh City",  // locationName — needs location service
true,                // hasUnreadNotification — hardcoded
5,                   // streakDays — needs gamification repo
5,                   // nearbyHotspotCount — needs hotspot repo
buildMockInviteList() // "Minh", "Sarah", "Linh", "Tom", "Hà" — static
12.5, 3              // weekly stats — needs stats repo
```

**Missing API connections:**
- Greeting name and avatar → `GET /api/v1/users/me` (profile endpoint)
- Streak and stats → Gamification repository (exists in domain but not called by HomeViewModel)
- Nearby hotspot count → `GET /api/v1/hotspots` (already loaded in ExploreViewModel — could be shared)
- Quick Invite list → Social repository (exists in domain but not called)
- Unread notification badge → Notification repository `hasUnread()` (exists but not called)

---

## G-10 · Profile Page: All Menu Items Show "Coming Soon"
**Severity:** 🟢 Minor

**File:** `ui/profile/ProfileFragment.java`

**Current behavior:** The menu rows (Walk History & Disputes, My Badges, Settings) are clickable but show a `Toast(profile_coming_soon)`. Profile data (name, trust score, tags, stats, badges) appears to come from `ProfileViewModel`, but it is unknown if it calls real APIs or uses hardcoded data.

**Desired behavior:** Profile data should be loaded from `GET /api/v1/users/me/profile`. Walk History should navigate to a history screen. Badges should navigate to a badges screen.

---

## G-11 · Vietnamese Text in Source Code and String Resources
**Severity:** 🟢 Minor

### 11a. Vietnamese comments in `ExploreFragment.java`

| Line | Vietnamese comment | Suggested English translation |
|---|---|---|
| 301 | `// RESET: Nếu sheet quay lại nấc 1/3, khóa tính năng ẩn lại để tránh "nhạy" cho lần sau` | `// RESET: Once sheet returns to 1/3 peek, disable hideable to prevent accidental dismissal` |
| 309 | `// slideOffset: 1.0 (Full), 0.0 (1/3), -1.0 (Biến mất)` | `// slideOffset: 1.0 = fully expanded, 0.0 = peek height, -1.0 = hidden` |
| 310 | `// Ta chỉ cho phép "ẩn" khi người dùng đã kéo xuống cực sâu (ví dụ -0.8)` | `// Only allow hidden when user has dragged deeply down (e.g. offset < -0.8)` |
| 314 | `// Nếu họ lỡ tay kéo nhẹ rồi rụt lại, ta khóa hideable ngay để nó "đập" lại nấc 1/3` | `// If user drags slightly then releases, lock hideable so the sheet snaps back to peek` |
| 531 | `// Ép Container tính toán lại kích thước để nhận diện đầy đủ nội dung mới của SETUP` | `// Force container to remeasure to correctly lay out SETUP content` |
| 535 | `// Luôn cuộn về đỉnh để người dùng thấy tiêu đề và không bị "trôi" nội dung` | `// Always scroll to top so the user sees the heading and content doesn't drift` |

### 11b. Vietnamese string values in `strings.xml`

| Key | Current value | Suggested English value |
|---|---|---|
| `home_quick_invite_header` | `"Rủ rê nhanh"` | `"Quick Invite"` |
| `gender_male` | `"Nam"` | `"Male"` |
| `gender_female` | `"Nữ"` | `"Female"` |
| `gender_any` | `"Bất kỳ"` | `"Any"` |
| `tag_running` | `"Chạy bộ"` | `"Running"` |
| `tag_podcast` | `"Nghe podcast"` | `"Podcast"` |
| `tag_dog` | `"Dắt chó"` | `"Dog Walk"` |
| `tag_meditation` | `"Thiền"` | `"Meditation"` |
| `tag_slow_walk` | `"Đi bộ chậm"` | `"Slow Walk"` |
| `tag_photography` | `"Chụp ảnh"` | `"Photography"` |
| `hotspot_1` through `hotspot_4` | Vietnamese names | Keep as-is — these are proper place names |

---

## G-12 · No Firebase Cloud Messaging (FCM) Client
**Severity:** 🔴 Critical

**Files:** `build.gradle.kts`, `AndroidManifest.xml`, `service/` package (absent)

**Current behavior:**
- `firebase-messaging` SDK is not declared as a Gradle dependency.
- `google-services.json` does not exist in `app/`.
- No `FirebaseMessagingService` subclass exists anywhere in the project.
- No FCM registration token is sent to the backend at login/startup, so the backend has no channel to deliver push notifications to this device.

**Desired behavior:**
- `google-services.json` added to `app/`.
- `com.google.gms:google-services` plugin applied in `build.gradle.kts`.
- `com.google.firebase:firebase-messaging` added as a dependency.
- `WalkMateFcmService extends FirebaseMessagingService` created under `service/`:
  - `onNewToken(String token)` → calls `userRepository.updateFcmToken(token)`
  - `onMessageReceived(RemoteMessage msg)` → reads `data` map, posts to `AppEventBus`
- `<service android:name=".service.WalkMateFcmService" android:exported="false">` added to `AndroidManifest.xml` with an FCM intent filter.
- `AppEventBus` singleton (Application-scoped) exposes `MutableLiveData<AppEvent>` for foreground UI observers.

---

## G-13 · No Jetpack Navigation Component
**Severity:** 🔴 Critical

**Files:** `build.gradle.kts`, `ui/main/MainActivity.java`, all Fragments that perform cross-screen navigation

**Current behavior:**
- `androidx.navigation:navigation-fragment` and `navigation-ui` are not in `build.gradle.kts`.
- There is no `res/navigation/nav_graph.xml`.
- `MainActivity` manages tabs by manually showing/hiding Fragment instances via `FragmentManager`.
- Fragments navigate to sibling screens by casting the host Activity and calling public methods directly:
  ```java
  ((MainActivity) requireActivity()).switchToExplore();
  ```
  This creates tight coupling and makes the navigation flow fragile and untestable.

**Desired behavior:**
- Jetpack Navigation added to `build.gradle.kts`.
- `res/navigation/nav_graph.xml` defines destinations: `homeFragment`, `exploreFragment`, `matchesFragment`, `profileFragment`.
- `MainActivity`'s layout contains a `NavHostFragment` (`app:navGraph="@navigation/nav_graph"`).
- `BottomNavigationView` wired to `NavController` via `NavigationUI.setupWithNavController()`.
- Each destination's menu item ID matches the corresponding fragment ID in `nav_graph.xml`.
- All cross-destination navigations use `NavController.navigate(R.id.action_...)` — zero Activity casting.
- FCM deep links defined as `<deepLink app:uri="walkmate://matches/proposal/{proposalId}"/>` in `nav_graph.xml`.

---

## Summary Table

| ID | Area | Severity | What's Missing |
|---|---|---|---|
| G-1 | Auth → Home | 🔴 Critical | `startActivity(MainActivity)` in `onLoginSuccess()` |
| G-2 | Create Intent entry | 🟡 Major | Home CTA leads to map, not form directly; bottom nav can bypass |
| G-3 | Search bar | 🔴 Critical | Bar is decorative — no input, no API call, no results |
| G-4 | Match detection | 🔴 Critical | No FCM client, no push receiver, no AppEventBus |
| G-5 | 10s timeout + prompt | 🔴 Critical | No timer, no dialog, no "Save to Finding List" flow |
| G-6 | Stop Searching API | 🔴 Critical | `resetToWelcome()` doesn't cancel the intent on the backend |
| G-7 | Auto-tab switch + Nav | 🟡 Major | No auto-scroll on proposal arrival; no NavController wiring |
| G-8 | Proposal cancel | 🟡 Major | Only Pass + Accept on proposals; no Cancel |
| G-9 | Home mock data | 🟡 Major | All dashboard data is hardcoded; no real API calls |
| G-10 | Profile menus | 🟢 Minor | All menu items stubbed with "Coming Soon" |
| G-11 | Vietnamese text | 🟢 Minor | Code comments and 10 string values are in Vietnamese |
| G-12 | FCM client | 🔴 Critical | No Firebase SDK, no service class, no token registration |
| G-13 | Jetpack Navigation | 🔴 Critical | No nav_graph.xml, manual FragmentManager, Activity casting |
