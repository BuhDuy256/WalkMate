# Phase 11 Report — Home Page Feature
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 1.1 (hotspot count hardcoded), 1.2 (stats hardcoded), 1.3 (friend list hardcoded), 1.4 (no onResume refresh)

---

## Mock Data Removed

All hardcoded values in `buildReadyState()` and `buildMockInviteList()` have been replaced:

| Field | Was | Now |
|---|---|---|
| `locationName` | `"Ho Chi Minh City"` | `cachedLocationName` (defaults to `"Your area"`, updated via `onLocationResolved()`) |
| `nearbyHotspotCount` | `5` | `cachedHotspotCount` — populated by `hotspotRepo.getHotspots()` |
| `weeklyDistanceKm` | `12.5` | `cachedDistanceKm` — populated by `gamificationRepo.getStats()` → `getTotalDistanceKm()` |
| `weeklySessionCount` | `3` | `cachedSessionCount` — populated by `gamificationRepo.getStats()` → `getCompletedSessions()` |
| `quickInviteList` | `buildMockInviteList()` (5 hardcoded users) | `cachedInviteList` — mapped from `socialRepo.getFriends()` → `List<UserSummary>` |
| `streakDays` | `5` | `5` — **unchanged**; `// TODO: No backend endpoint for streaks yet — hardcoded.` |

`buildMockInviteList()` has been deleted.

---

## New HomeViewModel Constructor Signature

```java
public HomeViewModel(
    WalkSessionRepository  sessionRepo,
    UserRepository         userRepo,
    UserProfileRepository  profileRepo,
    NotificationRepository notificationRepo,
    HotspotRepository      hotspotRepo,      // NEW
    GamificationRepository gamificationRepo, // NEW
    SocialRepository       socialRepo)       // NEW
```

`HomeViewModelFactory` updated to match. `HomeFragment.setupViewModel()` now passes all 7 singletons from `WalkMateApplication`.

---

## Parallel Load Architecture

`loadDashboard()` fires **4 logical units** concurrently via `AtomicInteger(4)`:

1. **Profile → Stats (chained):** `profileRepo.getMyProfile()` → on success, calls `gamificationRepo.getStats(profile.getUserId(), ...)`. Stats is chained so it always has a real `userId`. Both profile + stats count as 1 logical unit in the barrier.
2. **Sessions:** `sessionRepo.getActiveSessions()`
3. **Hotspots:** `hotspotRepo.getHotspots()`
4. **Friends:** `socialRepo.getFriends()`

When all 4 complete → `loadNotificationsAndPublish()` → final state emitted. All failures are non-fatal: defaults (0 / empty list) are used so the dashboard always renders.

---

## Location Name Resolution

Location resolution is decoupled from the main load barrier:

- **`LocationHelper`** (`core/util/LocationHelper.java`) — static utility with one public entry point:
  ```java
  LocationHelper.resolveCity(Context context, Location location, LocationNameCallback callback)
  ```
  Runs `Geocoder.getFromLocation()` on a background `ExecutorService`. Delivers result on main thread. Falls back to `"Your area"` on `IOException`.

- **`HomeFragment.resolveLocationName()`** — called from `onResume()`. Uses `FusedLocationProviderClient.getLastLocation()`. On success, calls `LocationHelper.resolveCity()` → `viewModel.onLocationResolved(cityName)`.

- **`HomeViewModel.onLocationResolved(String)`** — updates `cachedLocationName`. If the dashboard is already in ready state, immediately re-emits with the new location name. Otherwise the cached name is picked up on the next `buildReadyState()` call.

---

## onResume Refresh Added in HomeFragment

```java
@Override
public void onResume() {
    super.onResume();
    viewModel.loadDashboard(); // Gap 1.4: refresh data on every resume
    resolveLocationName();
}
```

The previous `onViewCreated` guard (`if (getUiState().getValue() == null)`) has been removed — `onResume` supersedes it and fires on every return to the tab.

---

## LocationHelper Public API

**File:** `frontend/src/main/java/com/walkmate/core/util/LocationHelper.java`

```java
public final class LocationHelper {
    public interface LocationNameCallback { void onResolved(String cityName); }

    public static void resolveCity(Context context, Location location,
                                   LocationNameCallback callback);
}
```

---

## Files Changed

| File | Change |
|---|---|
| `frontend/.../ui/home/HomeViewModel.java` | Added 3 repo fields + constructor params; replaced all mock data with repo calls; added `onLocationResolved()`; deleted `buildMockInviteList()` |
| `frontend/.../ui/home/HomeViewModelFactory.java` | Added 3 new repo params to constructor + `create()` |
| `frontend/.../ui/home/HomeFragment.java` | Added `onResume()` with `loadDashboard()` + location resolution; updated `setupViewModel()` with 3 new repos; added `resolveLocationName()` helper |
| `frontend/.../core/util/LocationHelper.java` | **New file** — static geocoding utility |
