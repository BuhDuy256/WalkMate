# Phase 11 Part 2 Report — Profile Page Data Wiring
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 2.1 (badges not loaded), 2.2 (isOnline hardcoded true), 2.5 (reviews feed missing)

---

## Gap 2.2 — isOnline Fixed

`ProfileViewModel.toUiState()` previously passed `true` for `isOnline`. Changed to `false`
throughout — hardcoded `true` was replaced in the new `loadSupplementalData()` publisher with:

```java
false, // Gap 2.2: isOnline — no presence system yet; always false
```

The online dot in `ProfileFragment` is now hidden until a real presence service is available.

---

## Gap 2.1 & 2.5 — Parallel Supplemental Fetches

After `profileRepo.getMyProfile()` succeeds, `loadSupplementalData(profile)` fires **3 calls in parallel** using `AtomicInteger(3)` as a completion barrier:

| Call | Repository method | Holder |
|---|---|---|
| Badges | `gamificationRepo.getBadges(userId, callback)` | `AtomicReference<List<Badge>>` |
| Stats | `gamificationRepo.getStats(userId, callback)` | `AtomicReference<UserStats>` |
| Reviews | `reviewRepo.getReviewsForUser(userId, callback)` | `AtomicReference<List<WalkReview>>` |

When all 3 complete, a single `ProfileUiState` is built and posted. All 3 failures are
non-fatal — defaults (empty list / profile fallback) are used so the screen always renders.

### Stats fallback
If `getStats()` fails, distance and session count fall back to the values already present
on `UserProfile` (`getTotalDistanceKm()` / `getTotalSessions()`).

---

## ProfileUiState Changes

1. **`Badge.labelStringResId` removed** — replaced with `String label`. Previously used an
   Android string resource ID, which assumed badges were hardcoded. Now that badge data
   comes from the backend, a plain string label is the correct type.
   `Badge.iconDrawableResId` is kept as `int`; `0` means "no icon yet" (Phase 14 will
   wire specific drawables per badge name).

2. **`List<WalkReview> reviews` field added** — new getter `getReviews()`. The Fragment
   stores the data; a dedicated reviews RecyclerView will be added in a future phase.

3. **Both static factories updated** (`loading()` and `error()`) to pass `null` for reviews.

---

## New ProfileViewModel Constructor Signature

```java
public ProfileViewModel(
    UserProfileRepository  profileRepo,
    GamificationRepository gamificationRepo,  // NEW
    ReviewRepository       reviewRepo)         // NEW
```

`loadSupplementalData(UserProfile)` is the new private method that orchestrates the
parallel fan-out. `toUiState()` has been removed — its logic is now inlined into the
`publish` Runnable inside `loadSupplementalData()` so all data is available at once.

---

## Badge Name Formatting

`formatBadgeName()` converts backend badge names to display labels:
```
"FIRST_WALK" → "First Walk"
"SPEED_DEMON" → "Speed Demon"
```
Icon drawables are `0` for now. Phase 14 will add a `badgeName → drawableResId` lookup
once the asset library is finalised.

---

## ProfileFragment Changes

- `setupViewModel()` now passes all 3 repos from `WalkMateApplication`
- `renderBadges()` updated: `labelSlots[i].setText(badge.label)` (was `.setText(badge.labelStringResId)`); guards `iconDrawableResId != 0` before calling `setImageResource()` to avoid a crash on unknown badges

---

## WalkMateApplication Changes

Added `ReviewRepository` singleton:
```java
private ReviewRepository reviewRepository;

public ReviewRepository getReviewRepository() {
    if (reviewRepository == null) {
        reviewRepository = new ReviewRepositoryImpl(this);
    }
    return reviewRepository;
}
```

---

## Files Changed

| File | Change |
|---|---|
| `frontend/.../ui/profile/ProfileUiState.java` | `Badge.label` replaces `labelStringResId`; added `reviews` field + getter |
| `frontend/.../ui/profile/ProfileViewModel.java` | Added `GamificationRepository` + `ReviewRepository`; `loadSupplementalData()` with AtomicInteger(3) barrier; `isOnline=false`; `mapBadges()` + `formatBadgeName()` helpers; removed `toUiState()` |
| `frontend/.../ui/profile/ProfileViewModelFactory.java` | Added 2 new repo params |
| `frontend/.../ui/profile/ProfileFragment.java` | `setupViewModel()` passes 3 repos; `renderBadges()` uses `badge.label` string + icon guard |
| `frontend/.../WalkMateApplication.java` | Added `ReviewRepository` field + `getReviewRepository()` singleton |
