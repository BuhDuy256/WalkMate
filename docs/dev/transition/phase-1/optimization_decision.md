# Optimization Decisions — Phase 1/2/3

## Phase 1 · Auth → Home

### Decision: Removed success Toast before navigation
The original code showed a `Toast.makeText(this, "Login successful!", ...)` before navigating.
With `FLAG_ACTIVITY_CLEAR_TASK`, `AuthActivity` is destroyed immediately after `startActivity()`.
On some devices the Toast is dismissed early because its host window is gone.
The Toast was removed; the login success is now self-evident from arriving at `MainActivity`.

---

## Phase 2 · Jetpack Navigation

### Decision: Bottom nav menu IDs renamed to match nav graph destinations
The old menu used `tab_explore`, `tab_matches`, `tab_profile`.
`NavigationUI.setupWithNavController()` requires menu item IDs to equal destination IDs so it can
highlight the correct item and invoke the correct navigation automatically.
The IDs were renamed to `homeFragment`, `matchesFragment`, `profileFragment` to mirror nav_graph.xml.
No runtime references to the old IDs existed outside of `MainActivity`, which was rewritten.

### Decision: Dropped `HomeFragment.OnHomeActionListener` interface
The interface was introduced to decouple `HomeFragment` from `MainActivity`. With Jetpack Navigation,
`Navigation.findNavController(view).navigate(R.id.action_home_to_explore)` is the correct
decoupling mechanism — the Fragment expresses intent through the graph, not through Activity contracts.
The interface added boilerplate (`onAttach` cast, `onDetach` null) with no remaining benefit.

### Decision: ExploreFragment still casts `requireActivity()` to `MainActivity`
`setBottomNavVisibility()` is a UI-control call (not navigation). Defining an interface for it
would add a contract class purely to avoid one downcast. Since `ExploreFragment` is always hosted
inside `MainActivity` in this app, the cast is safe and well-understood.
A `OnDestinationChangedListener` was added to `MainActivity` to **restore** bottom nav visibility
automatically when the user navigates away from ExploreFragment via the bottom nav bar — this fixes
a latent bug where switching tabs while Explore was in SETUP/SCANNING would leave the nav bar hidden.

### Decision: Fragment state preservation (multiple back stacks)
Navigation 2.7.7 ships with multiple back-stack support enabled by default when using
`NavigationUI.setupWithNavController()`. Fragment states (map position, scroll position, ViewPager
tab) are saved/restored automatically when switching bottom-nav tabs, replicating the previous
hide/show behaviour without manual FragmentTransaction management.

### Decision: ExploreFragment remains a sub-destination, not a bottom-nav tab
In the old code `tab_explore` mapped to `HomeFragment` (confusingly named). `ExploreFragment` was
only reachable via `HomeFragment`'s "Find a WalkMate Now" button. This relationship is preserved:
`ExploreFragment` is a regular destination in the nav graph, pushed onto the Home back stack.
Back-press from ExploreFragment naturally pops back to HomeFragment via NavController.

### Decision: AppEventBus observation in MainActivity, not MatchesFragment
`MatchesFragment` may not be active when an FCM event arrives. Observing in `MainActivity` ensures
the event is always handled regardless of which tab is currently visible. `MainActivity` then
navigates to `matchesFragment` via NavController with `scrollToTab` as an argument. `MatchesFragment`
reads the argument in `onViewCreated()` and calls `scrollToSubTab()`.

### Decision: `consumeEvent()` on AppEventBus to prevent sticky re-delivery
`MutableLiveData` is sticky: a new observer immediately receives the last value. Without consuming,
a device rotation while on the Matches screen would re-trigger the navigation. The `consumeEvent()`
call (setting value to null) follows the same pattern as `consumeError()` in every `UiState`
class across this codebase.

---

## Phase 3 · FCM Client Setup

### Decision: Service Locator access in `WalkMateFcmService` (architecture fix)
The original plan snippet used `UserRepositoryFactory.create(this)` which violates the project's
Manual DI / Service Locator pattern. The implementation uses
`((WalkMateApplication) getApplicationContext()).getUserRepository()` — the canonical pattern
documented in `Frontend_VI.md` and used everywhere else.

### Decision: `updateFcmToken` is a no-op when the user is not authenticated
`onNewToken()` fires on first install before the user logs in. Sending the token without an access
token would result in a 401 or send it to no user account. The implementation checks
`sessionManager.getAccessToken() == null` and silently returns. FCM guarantees token delivery
retries, so the token will be registered the next time it rotates or on re-install.

### Decision: Targeted `updateFcmToken(UUID, String)` in backend `UserRepository`
A full load-update-save cycle for a single-field change would execute a SELECT + a full UPSERT.
A targeted `UPDATE user_account SET fcm_token = ? WHERE user_id = ?` is a single statement and
avoids holding a stale `User` snapshot in memory. This is acceptable in DDD-lite where
infrastructure performance concerns are balanced against domain purity.

### Decision: `fcm_token` added to `User` domain entity
Even though FCM token is infrastructure-adjacent, storing it on the `User` aggregate makes it
a first-class citizen of the user's data. The `updateFcmToken(String)` method on `User` provides
a clear, named mutation point — consistent with `authenticate()`, `applyTrustScore()`, etc.
The field is nullable (`TEXT NULL`) since it is absent for new accounts and server-side users.

### Decision: Database migration required (not automated)
The `user_account` table requires an `ALTER TABLE` migration to add the `fcm_token TEXT NULL`
column. This is handled outside the Java code (Flyway/Liquibase migration script or manual DDL).
The backend will fail at runtime on `save()` / `updateFcmToken()` until the migration is applied.

### Decision: Separate `UserApiService` for authenticated user-management endpoints
`AuthApiService` uses the public Retrofit client (no auth header). The FCM token endpoint
requires a Bearer token. A new `UserApiService` interface backed by
`ApiClient.buildAuthenticatedRetrofit(sessionManager)` keeps the auth boundary explicit and
mirrors the pattern already used by `UserProfileApiService`.

### Decision: FCM endpoint placed in `UserProfileController` (backend)
The endpoint `PATCH /api/v1/users/me/fcm-token` is an authenticated, user-scoped operation.
`UserProfileController` already handles all authenticated `/api/v1/users/me/*` and
`/api/v1/profile/*` routes. Adding it here avoids a new controller class for a single endpoint.
`UserCommandService` is injected alongside the existing `UserProfileCommandService`.
