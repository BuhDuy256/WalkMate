# Phase Summary — Phase 1 / 2 / 3 (including FCM completion)

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

### Phase 3 · FCM Client Setup (Frontend) ✅
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

### Phase 3 · FCM Backend — Token Storage ✅
- [x] **Backend** `domain/user/User.java` — `fcmToken` field + `updateFcmToken(String)` method added
- [x] **Backend** `domain/user/UserRepository.java` — `updateFcmToken(UUID, String)` added
- [x] **Backend** `infrastructure/repository/user/UserJdbcRepository.java` — `updateFcmToken()` SQL + `fcm_token` in save/mapRow
- [x] **Backend** `application/user/UpdateFcmTokenCommand.java` — NEW record
- [x] **Backend** `application/user/UserCommandService.java` — `updateFcmToken()` added
- [x] **Backend** `presentation/dto/request/user/UpdateFcmTokenRequest.java` — NEW DTO
- [x] **Backend** `presentation/controller/user/UserProfileController.java` — `PATCH /api/v1/users/me/fcm-token` endpoint
- [x] **Backend** `infrastructure/config/SecurityConfig.java` — explicit auth rule for FCM PATCH

### Phase 3 · FCM Backend — Push Dispatch ✅
- [x] **Security** `.gitignore` (root, frontend, backend) — `google-services.json` and `firebase-service-account.json` excluded from Git
- [x] **Backend** `infrastructure/config/FirebaseConfig.java` — NEW; initialises Firebase Admin SDK; reads credentials from `FIREBASE_CREDENTIALS` env var (prod) or classpath `firebase-service-account.json` (local); exposes `FirebaseMessaging` as Spring bean
- [x] **Backend** `application/notification/PushNotificationProvider.java` — NEW interface (application-layer port); declares `sendMatchFound(fcmToken, intentId, proposalId)`; no Firebase types
- [x] **Backend** `infrastructure/notification/FcmNotificationProvider.java` — NEW; implements `PushNotificationProvider`; builds data-only FCM `Message`; swallows `FirebaseMessagingException` after logging
- [x] **Backend** `application/proposal/MatchingCommandService.java` — `PushNotificationProvider` + `UserRepository` injected; after creating a `MatchProposal`, looks up matched user's FCM token and calls `pushNotificationProvider.sendMatchFound()`

---

## Complete File Inventory

### Frontend
| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Modified — navigation, firebase, google-services plugin |
| `build.gradle.kts` (root) | Modified — google-services plugin |
| `frontend/build.gradle.kts` | Modified — google-services plugin + navigation + firebase deps |
| `frontend/src/main/res/navigation/nav_graph.xml` | **Created** |
| `frontend/src/main/res/layout/activity_main.xml` | Modified — FrameLayout → FragmentContainerView |
| `frontend/src/main/res/menu/menu_bottom_nav.xml` | Modified — IDs renamed |
| `frontend/src/main/AndroidManifest.xml` | Modified — WalkMateFcmService registered |
| `ui/auth/AuthActivity.java` | Modified — `onLoginSuccess()` added |
| `ui/main/MainActivity.java` | Modified — NavController + AppEventBus |
| `ui/home/HomeFragment.java` | Modified — NavController replaces interface |
| `ui/matches/MatchesFragment.java` | Modified — reads `scrollToTab` arg |
| `core/event/AppEvent.java` | **Created** |
| `core/event/AppEventBus.java` | **Created** |
| `service/WalkMateFcmService.java` | **Created** |
| `domain/user/UserRepository.java` | Modified — `updateFcmToken` added |
| `data/datasource/remote/api/UserApiService.java` | **Created** |
| `data/datasource/remote/dto/request/user/UpdateFcmTokenRequestDto.java` | **Created** |
| `data/repository/UserRepositoryImpl.java` | Modified — `updateFcmToken` implemented |
| `.gitignore` (root) | Modified — firebase credentials excluded |
| `frontend/.gitignore` | Modified — `google-services.json` excluded |

### Backend
| File | Action |
|------|--------|
| `domain/user/User.java` | Modified — `fcmToken` field + `updateFcmToken()` |
| `domain/user/UserRepository.java` | Modified — `updateFcmToken(UUID, String)` |
| `infrastructure/repository/user/UserJdbcRepository.java` | Modified — `updateFcmToken()` SQL + `fcm_token` in save/mapRow |
| `application/user/UpdateFcmTokenCommand.java` | **Created** |
| `application/user/UserCommandService.java` | Modified — `updateFcmToken()` |
| `presentation/dto/request/user/UpdateFcmTokenRequest.java` | **Created** |
| `presentation/controller/user/UserProfileController.java` | Modified — PATCH endpoint |
| `infrastructure/config/SecurityConfig.java` | Modified — auth rule for FCM PATCH |
| `infrastructure/config/FirebaseConfig.java` | **Created** |
| `application/notification/PushNotificationProvider.java` | **Created** |
| `infrastructure/notification/FcmNotificationProvider.java` | **Created** |
| `application/proposal/MatchingCommandService.java` | Modified — FCM push on match found |
| `backend/.gitignore` | Modified — `firebase-service-account.json` excluded |

---

## Critical Pre-Requisites Before Running

| # | What | Where |
|---|------|--------|
| 1 | Place `google-services.json` | `frontend/google-services.json` |
| 2 | Place `firebase-service-account.json` | `backend/src/main/resources/firebase-service-account.json` |
| 3 | DB migration for fcm_token column | `ALTER TABLE user_account ADD COLUMN fcm_token TEXT NULL;` |
| 4 | Set env var for production | `FIREBASE_CREDENTIALS=<json content>` |

---

## States, Variables & API Contracts the Next Phase Must Know

### Navigation
- Bottom nav menu IDs: `homeFragment`, `matchesFragment`, `profileFragment` — old `tab_*` IDs are gone
- `HomeFragment.OnHomeActionListener` interface **deleted**
- `MainActivity.showTab()`, `switchToExplore()`, `switchToMatchesTab()` **deleted**
- `ExploreFragment` still casts `requireActivity()` → `MainActivity` for `setBottomNavVisibility()` (intentional, documented)

### FCM Architecture
- **`PushNotificationProvider`** interface is at `application/notification/` — the only place Firebase types are allowed is `infrastructure/notification/FcmNotificationProvider.java`
- **`FirebaseConfig`** exposes `FirebaseMessaging` bean — inject this, never call `FirebaseMessaging.getInstance()` statically in other classes
- **`AppEventBus`** sticky-event guard: callers must call `consumeEvent()` after handling

### FCM Data Payload Contract
```json
{
  "type":       "MATCH_FOUND",
  "intentId":   "<matched-user's-intent-uuid>",
  "proposalId": "<newly-created-proposal-uuid>"
}
```
- No `notification` block — guarantees `onMessageReceived()` fires in all app states
- `intentId` is the **recipient's** own intent UUID (not the initiating user's)

### Endpoint
- `PATCH /api/v1/users/me/fcm-token` — requires Bearer JWT; body: `{ "fcmToken": "<string>" }`

### MatchesPagerAdapter tab constants
- `TAB_FINDING = 0`, `TAB_PROPOSAL = 1`, `TAB_SESSION = 2`
