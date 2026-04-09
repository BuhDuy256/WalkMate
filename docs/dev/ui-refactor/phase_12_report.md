# Phase 12 Report — Profile Page Feature
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 2.1 (badges/stats never loaded), 2.2 (isOnline hardcoded to true), 2.3 (Edit Profile screen missing), 2.4 (avatar upload missing), 2.5 (reviews not shown)

---

## Gap Status

### Gap 2.2 — isOnline Always False (CLOSED — already in codebase)

`ProfileViewModel.java` line 155 in `loadSupplementalData()`:

```java
false,  // Gap 2.2: isOnline — no presence system yet; always false
```

The comment documents the intent. `ProfileUiState.isOnline` is always `false`; `ProfileFragment.renderState()` hides `viewOnlineStatus` when `isOnline()` is false. No polling, no guessing.

---

### Gap 2.1 — Badges and Stats Never Loaded (CLOSED — already in codebase)

`ProfileViewModel.loadSupplementalData()` fires three parallel calls after the base profile is fetched:

1. **Badges** — `gamificationRepository.getBadges(userId, callback)` → `badgesHolder`
2. **Stats** — `gamificationRepository.getStats(userId, callback)` → `statsHolder`
3. **Reviews** — `reviewRepository.getReviewsForUser(userId, callback)` → `reviewsHolder`

An `AtomicInteger(3)` barrier ensures state is published only after all three calls complete (or fail non-fatally). Stats values fall back to `UserProfile` fields if the gamification API call fails.

---

### Gap 2.5 — Reviews Not Shown (CLOSED — already in codebase)

`ReviewRepository.getReviewsForUser()` was already present in the domain interface:

```java
void getReviewsForUser(String userId, DomainCallback<List<WalkReview>> callback);
```

`ProfileViewModel` already called it via `reviewRepo.getReviewsForUser()`. No interface changes were needed.

---

### Gap 2.3 — Edit Profile Screen Missing (CLOSED — new files)

Four new files created under `ui/profile/edit/`:

| File | Purpose |
|---|---|
| `EditProfileUiState.java` | Immutable state snapshot; copy-mutators `withLoading()`, `withError()`, `withSaveSuccess()`, `withAvatarUrl()` |
| `EditProfileViewModel.java` | `loadCurrentProfile()`, `save()` (validation + updateProfile), `uploadAvatar()` (byte read on executor + upload) |
| `EditProfileViewModelFactory.java` | Manual DI factory; takes `UserProfileRepository` |
| `EditProfileFragment.java` | Form UI; system image picker via `ActivityResultLauncher`; observes `saveSuccess` → pops back stack |

---

### Gap 2.4 — Avatar Upload Missing (CLOSED — part of EditProfileFragment)

`EditProfileFragment` taps `imgAvatar` → launches `Intent.ACTION_PICK` via `ActivityResultLauncher<Intent>`. On result:

```java
viewModel.uploadAvatar(imageUri, requireContext().getContentResolver());
```

`EditProfileViewModel.uploadAvatar()` reads bytes from the URI on a background `ExecutorService`, then calls:

```java
profileRepo.uploadAvatar(bytes, filename, mimeType, callback);
```

On success, calls `loadCurrentProfile()` to refresh all form fields with the new avatar URL.

---

## Navigation Wiring (ProfileFragment → EditProfileFragment)

### ProfileViewModel — new navigation signal

```java
private final MutableLiveData<Void> navigateToEditEvent = new MutableLiveData<>();

public LiveData<Void> getNavigateToEditEvent()  { return navigateToEditEvent; }
public void consumeNavigateToEdit()             { navigateToEditEvent.postValue(null); }
public void onEditProfileClicked()              { navigateToEditEvent.postValue(null); }
```

### ProfileFragment — observer + launch

```java
viewModel.getNavigateToEditEvent().observe(getViewLifecycleOwner(), unused -> {
    viewModel.consumeNavigateToEdit();
    requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, new EditProfileFragment(), EditProfileFragment.TAG)
            .addToBackStack(null)
            .commit();
});
```

A `btnEditProfile` view (optional, null-checked) in `fragment_profile.xml` calls `viewModel.onEditProfileClicked()`.

---

## ProfileViewModel Constructor Signature (for Phase 14)

```java
public ProfileViewModel(
    UserProfileRepository  profileRepo,
    GamificationRepository gamificationRepo,
    ReviewRepository       reviewRepo)
```

No changes from what was already in the codebase.

---

## getReviewsForUser() — Interface Status

`ReviewRepository.getReviewsForUser(String userId, DomainCallback<List<WalkReview>> callback)` was **already present** in the domain interface. No interface change was needed.

---

## Files Changed

| File | Change |
|---|---|
| `frontend/.../ui/profile/ProfileViewModel.java` | Added `navigateToEditEvent` LiveData + `onEditProfileClicked()` + `consumeNavigateToEdit()` |
| `frontend/.../ui/profile/ProfileFragment.java` | Added `btnEditProfile` field; wired click listener; observe `navigateToEditEvent` → FragmentManager transaction |
| `frontend/.../ui/profile/edit/EditProfileUiState.java` | **New file** |
| `frontend/.../ui/profile/edit/EditProfileViewModel.java` | **New file** |
| `frontend/.../ui/profile/edit/EditProfileViewModelFactory.java` | **New file** |
| `frontend/.../ui/profile/edit/EditProfileFragment.java` | **New file** |
