# Profile Cache — Implementation Plan

**Strategy:** Offline-first with a Room DB cache for `UserProfile`.
**Constraint source:** `Frontend_VI.md` (ExecutorService only, no RxJava/Coroutines, Manual DI via `WalkMateApplication`).

---

## 1. Traced Data Flow (Current — Network Only)

```
ProfileFragment.onViewCreated()
  └─ viewModel.loadProfile()
       └─ uiState.postValue(loading)           ← Fragment ignores this (returns early, blank screen)
       └─ profileRepo.getMyProfile(callback)
            └─ ExecutorService.execute()
                 └─ Retrofit .execute()  →  Backend API
                      └─ onSuccess(UserProfile)
                           └─ viewModel.loadSupplementalData(profile)
                                └─ 3x parallel ExecutorService (Badges, Stats, Reviews)
                                     └─ AtomicInteger(3) barrier
                                          └─ uiState.postValue(ProfileUiState)
                                               └─ Fragment renderState() → UI populated
```

**Root problem:** The entire pipeline is remote-only. There is nothing returned to the UI until all 4+
network calls finish. The "pause" is real network latency dressed up as a UI freeze because `renderState()`
silently returns early on `isLoading`, showing no progress indicator.

---

## 2. Target Data Flow (Offline-First)

```
ProfileFragment.onViewCreated()
  └─ viewModel.loadProfile()
       └─ uiState.postValue(loading)           ← Fragment NOW shows ProgressBar/skeleton
       └─ profileRepo.getMyProfile(callback)

            ── Phase A: Instant Cache Read (background thread) ──────────────────
            └─ userProfileDao.getMyProfile()    ← Room query, background thread
                 └─ if cached row exists:
                      └─ callback.onSuccess(cachedDomainObject)  ← Fire #1
                           └─ viewModel.loadSupplementalData()
                                └─ uiState.postValue(...)  ← UI populated in < 50 ms

            ── Phase B: Silent Background Refresh ───────────────────────────────
            └─ Retrofit .execute()  →  Backend API
                 └─ onSuccess(freshDto):
                      └─ userProfileDao.upsert(freshEntity)  ← Write cache
                      └─ callback.onSuccess(freshDomainObject)  ← Fire #2
                           └─ viewModel.loadSupplementalData()
                                └─ uiState.postValue(...)  ← UI silently updates to fresh data

                 └─ onNetworkError (no connection):
                      └─ if Phase A already fired → swallow error silently (user sees cached data)
                      └─ if Phase A had no cache  → callback.onError(e) → show error Toast
```

> **Dual-fire pattern:** `DomainCallback.onSuccess()` may be called **twice** on a cache-hit —
> once with stale data, once with fresh. The ViewModel (`loadSupplementalData`) and the Fragment
> (`renderState`) already handle repeated `postValue` calls correctly; no extra wiring is needed.

---

## 3. Step-by-Step Implementation Plan

### Step 1 — Create `UserProfileEntity` (Room entity)

**File:** `data/datasource/local/entity/UserProfileEntity.java`

Fields to persist (mirror `UserProfile` domain object + a cache timestamp):

| Column             | Room Type | Notes                                             |
|--------------------|-----------|---------------------------------------------------|
| `userId`           | `TEXT`    | `@PrimaryKey` — the authenticated user's ID       |
| `fullName`         | `TEXT`    |                                                   |
| `gender`           | `TEXT`    | nullable                                          |
| `dateOfBirth`      | `TEXT`    | "YYYY-MM-DD", nullable                            |
| `avatarUrl`        | `TEXT`    | nullable                                          |
| `bio`              | `TEXT`    | nullable                                          |
| `searchRadius`     | `INTEGER` |                                                   |
| `trustScore`       | `INTEGER` |                                                   |
| `totalDistanceKm`  | `REAL`    |                                                   |
| `totalSessions`    | `INTEGER` |                                                   |
| `tags`             | `TEXT`    | comma-separated string (e.g. `"Chatty,Dog Friendly"`) — avoids TypeConverter complexity |
| `cachedAtEpochMs`  | `INTEGER` | `System.currentTimeMillis()` at write time — reserved for future TTL/staleness checks |

> **Why comma-separated for tags?** The existing codebase has no `@TypeConverter` infrastructure.
> Adding a converter for a single field introduces ceremony disproportionate to the gain.
> The tag list is short (≤ 5 items) and never queried by individual tag value in this cache,
> so a simple split/join at the mapper level is sufficient.

### Step 2 — Create `UserProfileDao`

**File:** `data/datasource/local/dao/UserProfileDao.java`

Required queries:

```
@Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
UserProfileEntity getProfileById(String userId);

@Insert(onConflict = OnConflictStrategy.REPLACE)
void upsert(UserProfileEntity entity);
```

> Only these two operations are needed. No delete, no LiveData queries —
> the offline-first orchestration is driven by the Repository, not by Room observers.

### Step 3 — Create `UserProfileEntityMapper`

**File:** `data/mapper/UserProfileEntityMapper.java`

Two static methods:

- `toEntity(UserProfile domain, long cachedAtEpochMs) → UserProfileEntity`
  - Joins `List<String> tags` into a comma-separated `String`.
- `toDomain(UserProfileEntity entity) → UserProfile`
  - Splits the comma-separated `tags` back into a `List<String>`.
  - Guard against null/empty tag string → return `Collections.emptyList()`.

> This mapper lives alongside `UserProfileMapper` (DTO → Domain) in `data/mapper/`
> and follows the same static-method-only pattern.

### Step 4 — Add Migration & Register Entity in `WalkMateDatabase`

**File:** `data/datasource/local/WalkMateDatabase.java`

Changes required:

1. Add `UserProfileEntity.class` to the `@Database entities` array.
2. Bump `version` from `2` to `3`.
3. Add `MIGRATION_2_3` that creates the `user_profile` table:

```sql
CREATE TABLE IF NOT EXISTS `user_profile` (
    `userId`           TEXT NOT NULL,
    `fullName`         TEXT,
    `gender`           TEXT,
    `dateOfBirth`      TEXT,
    `avatarUrl`        TEXT,
    `bio`              TEXT,
    `searchRadius`     INTEGER NOT NULL DEFAULT 0,
    `trustScore`       INTEGER NOT NULL DEFAULT 0,
    `totalDistanceKm`  REAL NOT NULL DEFAULT 0,
    `totalSessions`    INTEGER NOT NULL DEFAULT 0,
    `tags`             TEXT,
    `cachedAtEpochMs`  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`userId`)
)
```

4. Declare `public abstract UserProfileDao userProfileDao();` on the abstract class.
5. Pass `MIGRATION_2_3` into `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

> `fallbackToDestructiveMigration()` is already present as a safety net — no change needed there.

### Step 5 — Refactor `UserProfileRepositoryImpl` (Core Change)

**File:** `data/repository/UserProfileRepositoryImpl.java`

**Constructor change:** Accept `UserProfileDao dao` as a new parameter (injected from
`WalkMateApplication` — see Step 6).

**Refactor `getMyProfile(DomainCallback<UserProfile> callback)`:**

Replace the current single-fetch pattern with the offline-first two-phase pattern:

```
Phase A (instant):
  1. On the executor background thread, call dao.getProfileById(myUserId).
     Problem: We need the userId to query by key. But the userId is only known after a
     successful first fetch — OR it can be retrieved from SessionManager (which holds the
     JWT token / stored user id from the last login).
     → Read userId from SessionManager before querying.
     → If SessionManager has no userId yet (first login), skip Phase A.
  2. If a cached entity exists:
     a. Map entity → domain via UserProfileEntityMapper.toDomain().
     b. callback.onSuccess(cachedProfile).   ← UI renders immediately.

Phase B (silent refresh):
  3. Make the Retrofit call (same as before).
  4. On API success:
     a. Map DTO → domain via UserProfileMapper.toDomain().
     b. Map domain → entity via UserProfileEntityMapper.toEntity(fresh, currentTimeMs).
     c. dao.upsert(entity).
     d. callback.onSuccess(freshProfile).    ← UI silently re-renders with fresh data.
  5. On API error:
     a. If Phase A delivered a cached result (cacheDelivered = true): swallow the error silently.
     b. If no cache was available: callback.onError(e).
```

Use a local `boolean[] cacheDelivered = {false}` flag (accessible in the lambda) to track
whether Phase A already fired a successful callback before deciding whether to surface the
network error in Phase B.

**Refactor `updateProfile(...)` (write-through):**

After the API call succeeds and `UserProfileMapper.toDomain()` runs:
- Also call `dao.upsert(UserProfileEntityMapper.toEntity(freshProfile, now))`.
- This keeps the cache consistent after every profile edit.

**No change needed for `uploadAvatar(...)`** — it calls `loadProfile()` on success, which will
trigger the offline-first flow through `getMyProfile()`.

**`getProfile(String userId, ...)` (public profiles):**

Do NOT cache public profiles in this phase. The current remote-only flow is acceptable for
viewing another user's profile. Caching foreign profiles adds complexity (cache key by userId,
eviction policy) that is out of scope.

### Step 6 — Wire the DAO in `WalkMateApplication` (DI)

**File:** `WalkMateApplication.java`

In the `userProfileRepository` lazy initializer, update the constructor call:

```java
// Before
userProfileRepository = new UserProfileRepositoryImpl(this);

// After
userProfileRepository = new UserProfileRepositoryImpl(
    this,
    database.userProfileDao()   // database is the existing WalkMateDatabase singleton
);
```

> `database` (the `WalkMateDatabase` singleton) is already field-level in `WalkMateApplication`.
> No new dependency is introduced — just one extra constructor argument.

### Step 7 — Fix the Loading State in `ProfileFragment`

**File:** `ui/profile/ProfileFragment.java` — `renderState(ProfileUiState state)`

Current behavior:
```java
if (state.isLoading()) {
    return;  // ← blank screen, no feedback
}
```

Required behavior:
```java
if (state.isLoading()) {
    progressBar.setVisibility(View.VISIBLE);
    profileContentRoot.setVisibility(View.GONE);
    return;
}
progressBar.setVisibility(View.GONE);
profileContentRoot.setVisibility(View.VISIBLE);
// ... rest of renderState unchanged
```

This requires:
1. Adding a `ProgressBar` (or a skeleton shimmer view) to `fragment_profile.xml`.
2. Binding it in `bindViews()`.
3. Toggling its visibility in `renderState()`.

> After Step 5 is in place, the loading state will only be visible for the very first
> app launch before any cache exists. On all subsequent opens the cached data renders
> instantly and the loading indicator is never shown.

### Step 8 — UI & Logic Cleanup (Removing Unused Features)

To simplify the project, we are intentionally removing the **Current Streak**, **Online Status**, and **Visibility Mode** features.

1. **`res/layout/fragment_profile.xml` & `res/layout/fragment_public_profile.xml`**:
   - Delete `viewOnlineStatus` (the green dot).
   - Delete `txtStatStreakValue` and its surrounding column layout.
   - Delete `switchVisibility` (the PUBLIC/PRIVATE toggle).
2. **`ui/profile/ProfileFragment.java` & `PublicProfileFragment.java`**:
   - Remove variables `viewOnlineStatus`, `txtStatStreakValue`, `switchVisibility`.
   - Remove `findViewById` bindings in `bindViews()`.
   - Remove logic updating these views in `renderState()`.
3. **`ui/profile/ProfileUiState.java`**:
   - Remove fields `isOnline` and `currentStreak`, their constructor parameters, and getter methods.
4. **`ui/profile/ProfileViewModel.java`**:
   - In `loadSupplementalData()`, remove the hardcoded `false` and `0` parameters when creating `ProfileUiState`.

---

## 4. File Change Summary

| File | Action |
|---|---|
| `data/datasource/local/entity/UserProfileEntity.java` | **CREATE** |
| `data/datasource/local/dao/UserProfileDao.java` | **CREATE** |
| `data/mapper/UserProfileEntityMapper.java` | **CREATE** |
| `data/datasource/local/WalkMateDatabase.java` | **MODIFY** (version 2→3, MIGRATION_2_3, new DAO) |
| `data/repository/UserProfileRepositoryImpl.java` | **MODIFY** (inject DAO, offline-first logic) |
| `WalkMateApplication.java` | **MODIFY** (pass DAO to repository constructor) |
| `ui/profile/ProfileFragment.java` | **MODIFY** (show ProgressBar, remove unused views) |
| `res/layout/fragment_profile.xml` | **MODIFY** (add ProgressBar, remove unused views) |
| `ui/profile/ProfileUiState.java` | **MODIFY** (remove unused fields) |
| `ui/profile/ProfileViewModel.java` | **MODIFY** (remove unused parameters) |

---

## 5. Architecture Compliance Checklist

| Rule from `Frontend_VI.md` | Compliance |
|---|---|
| No RxJava / No Coroutines | ✅ ExecutorService only |
| Room DB as local storage | ✅ New `UserProfileEntity` + DAO |
| Singleton DB via WalkMateApplication | ✅ DAO injected through existing `database` field |
| No Hilt/Dagger | ✅ Manual DI only |
| Repository maps DTO → Domain via Mapper | ✅ `UserProfileMapper` (DTO→Domain) + new `UserProfileEntityMapper` (Entity↔Domain) |
| DTO must not leak past `data/` layer | ✅ Entity never leaves `data/`; only `UserProfile` domain object is passed to ViewModel |
| View must not import Room/Retrofit | ✅ Fragment only observes `LiveData<ProfileUiState>` |
| Async on background thread | ✅ Both DAO read and Retrofit call run inside `executor.execute()` |
