# Phase Summary — Phase 1 / 2 / 3

Use this file as the contextual input for the **next** chat session.

---

## Checklist — What Was Implemented

### Phase 1 · Auth → Home Navigation ✅
- [x] `AuthActivity.observeUiState()` calls `onLoginSuccess()` on `state.isSuccess()`
- [x] `onLoginSuccess()` launches `MainActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`
- [x] Back-press from `MainActivity` exits the app; cannot return to login screen

### Phase 2 · Jetpack Navigation Component ✅
- [x] Navigation 2.7.7 + Navigation UI added to `libs.versions.toml` and `frontend/build.gradle.kts`
- [x] `res/navigation/nav_graph.xml` created with 4 destinations: `homeFragment`, `exploreFragment`, `matchesFragment`, `profileFragment`
- [x] `activity_main.xml` — `FrameLayout` replaced with `FragmentContainerView` (NavHostFragment, `defaultNavHost="true"`)
- [x] `menu_bottom_nav.xml` — IDs renamed: `homeFragment` / `matchesFragment` / `profileFragment`
- [x] `MainActivity` — manual `showTab()`, `switchToExplore()`, `switchToMatchesTab()` removed; `NavController` + `NavigationUI.setupWithNavController()` wired; `OnDestinationChangedListener` restores bottom nav when leaving ExploreFragment
- [x] `HomeFragment` — `OnHomeActionListener` interface removed; "Find WalkMate" button uses `Navigation.findNavController(root).navigate(R.id.action_home_to_explore)`
- [x] `MatchesFragment` — reads `scrollToTab` argument from `getArguments()` in `onViewCreated()`

### Phase 3 · FCM Client Setup ✅
- [x] Firebase BOM 33.1.0 + `firebase-messaging` added to `libs.versions.toml` and `frontend/build.gradle.kts`
- [x] `google-services` plugin added to root and frontend `build.gradle.kts`
- [x] `core/event/AppEvent.java` — immutable event payload (`Type.MATCH_FOUND`, `intentId`, `proposalId`)
- [x] `core/event/AppEventBus.java` — process-singleton LiveData bus with `post()` + `consumeEvent()`
- [x] `service/WalkMateFcmService.java` — handles `onNewToken()` (registers token via `UserRepository`) and `onMessageReceived()` (posts to `AppEventBus`)
- [x] `AndroidManifest.xml` — `WalkMateFcmService` registered with `MESSAGING_EVENT` intent-filter
- [x] `domain/user/UserRepository.java` (frontend) — `updateFcmToken(String, DomainCallback<Void>)` added
- [x] `data/datasource/remote/api/UserApiService.java` — NEW authenticated Retrofit interface for `PATCH /api/v1/users/me/fcm-token`
- [x] `data/datasource/remote/dto/request/user/UpdateFcmTokenRequestDto.java` — NEW DTO
- [x] `data/repository/UserRepositoryImpl.java` — `updateFcmToken()` implemented (no-ops if not authenticated)
- [x] `MainActivity` — observes `AppEventBus`; navigates to `matchesFragment` with `scrollToTab=TAB_PROPOSAL` on `MATCH_FOUND`
- [x] **Backend** `domain/user/User.java` — `fcmToken` field + `updateFcmToken(String)` method added
- [x] **Backend** `domain/user/UserRepository.java` — `updateFcmToken(UUID, String)` added
- [x] **Backend** `infrastructure/repository/user/UserJdbcRepository.java` — `updateFcmToken()` SQL + `fcm_token` added to `save()` INSERT/UPDATE and `mapRow()`
- [x] **Backend** `application/user/UpdateFcmTokenCommand.java` — NEW record
- [x] **Backend** `application/user/UserCommandService.java` — `updateFcmToken(UpdateFcmTokenCommand)` added
- [x] **Backend** `presentation/dto/request/user/UpdateFcmTokenRequest.java` — NEW DTO with `@Valid`
- [x] **Backend** `presentation/controller/user/UserProfileController.java` — `PATCH /api/v1/users/me/fcm-token` endpoint added
- [x] **Backend** `infrastructure/config/SecurityConfig.java` — explicit `.authenticated()` rule for the FCM patch endpoint

---

## Files Modified / Created

### Frontend
| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Modified — added `navigation`, `firebaseBom`, `navigationFragment`, `navigationUi`, `firebaseMessaging`, `google-services` plugin |
| `build.gradle.kts` (root) | Modified — added `google-services` plugin |
| `frontend/build.gradle.kts` | Modified — added `google-services` plugin + navigation + firebase deps |
| `frontend/src/main/res/navigation/nav_graph.xml` | **Created** |
| `frontend/src/main/res/layout/activity_main.xml` | Modified — FrameLayout → FragmentContainerView |
| `frontend/src/main/res/menu/menu_bottom_nav.xml` | Modified — menu item IDs renamed |
| `frontend/src/main/AndroidManifest.xml` | Modified — WalkMateFcmService registered |
| `ui/auth/AuthActivity.java` | Modified — `onLoginSuccess()` added |
| `ui/main/MainActivity.java` | Modified — full rewrite for NavController + AppEventBus |
| `ui/home/HomeFragment.java` | Modified — removed OnHomeActionListener, uses NavController |
| `ui/matches/MatchesFragment.java` | Modified — reads `scrollToTab` argument |
| `core/event/AppEvent.java` | **Created** |
| `core/event/AppEventBus.java` | **Created** |
| `service/WalkMateFcmService.java` | **Created** |
| `domain/user/UserRepository.java` | Modified — `updateFcmToken` added |
| `data/datasource/remote/api/UserApiService.java` | **Created** |
| `data/datasource/remote/dto/request/user/UpdateFcmTokenRequestDto.java` | **Created** |
| `data/repository/UserRepositoryImpl.java` | Modified — `updateFcmToken` implemented |

### Backend
| File | Action |
|------|--------|
| `domain/user/User.java` | Modified — `fcmToken` field + constructor + `updateFcmToken()` |
| `domain/user/UserRepository.java` | Modified — `updateFcmToken(UUID, String)` added |
| `infrastructure/repository/user/UserJdbcRepository.java` | Modified — `updateFcmToken()` SQL + `fcm_token` in save/mapRow |
| `application/user/UpdateFcmTokenCommand.java` | **Created** |
| `application/user/UserCommandService.java` | Modified — `updateFcmToken()` added |
| `presentation/dto/request/user/UpdateFcmTokenRequest.java` | **Created** |
| `presentation/controller/user/UserProfileController.java` | Modified — PATCH endpoint + `UserCommandService` injected |
| `infrastructure/config/SecurityConfig.java` | Modified — explicit auth rule for FCM PATCH |

---

## States, Variables & API Contracts the Next Phase Must Know

### Navigation
- **Bottom nav menu IDs** are now `homeFragment`, `matchesFragment`, `profileFragment` — any code referencing `R.id.tab_explore`, `R.id.tab_matches`, `R.id.tab_profile` will not compile.
- **`HomeFragment.OnHomeActionListener`** interface has been **deleted**. Any Activity that previously implemented it must be updated.
- **`MainActivity.showTab()`, `switchToExplore()`, `switchToMatchesTab()`** are **deleted**. Callers must use `NavController.navigate()`.
- `ExploreFragment` still casts `requireActivity()` to `MainActivity` for `setBottomNavVisibility()` — this is intentional and documented.

### FCM
- **Backend DB migration required**: `ALTER TABLE user_account ADD COLUMN fcm_token TEXT NULL;` — the backend will throw at runtime on `save()` calls until this migration is applied.
- **`google-services.json`** must be placed at `frontend/google-services.json` (downloaded from Firebase Console after linking the project). The build will fail without it once the `google-services` plugin is active.
- The FCM endpoint `PATCH /api/v1/users/me/fcm-token` expects JSON body `{ "fcmToken": "<token>" }` and requires a valid JWT Bearer token.

### AppEventBus
- `AppEventBus.get().observe(lifecycleOwner, event -> { ... })` — callers **must** call `AppEventBus.get().consumeEvent()` after handling to prevent sticky re-delivery on config change.
- `AppEvent.Type` currently has one value: `MATCH_FOUND`. New push types require adding an enum constant here and a handler in `WalkMateFcmService.onMessageReceived()`.

### MatchesFragment argument
- Argument key: `"scrollToTab"` (integer, default 0).
- Constants: `MatchesPagerAdapter.TAB_FINDING=0`, `TAB_PROPOSAL=1`, `TAB_SESSION=2`.
