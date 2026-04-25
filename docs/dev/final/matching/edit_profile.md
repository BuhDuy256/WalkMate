# E2E Static Code Analysis: Edit Profile Feature

## Scope

| Layer | File |
|---|---|
| Layout | fragment_edit_profile.xml |
| Fragment | EditProfileFragment.java |
| ViewModel | EditProfileViewModel.java |
| UI State | EditProfileUiState.java |
| Domain repo interface | UserProfileRepository.java (domain) |
| Frontend DTO | UpdateProfileRequestDto.java |
| Frontend repo impl | UserProfileRepositoryImpl.java |
| Backend controller | UserProfileController.java |
| Backend command | UpdateProfileCommand.java / UpdateProfileRequest.java |
| Backend service | UserProfileCommandService.java |
| Backend repo impl | UserProfileJdbcRepository.java |

---

## 1. Data Loading (Eager Fetching)

**Verdict:** Correctly implemented, with one structural caveat.

`EditProfileViewModel.loadCurrentProfile()` fires two parallel background calls immediately:

```text
profileRepo.getMyProfile(...)  -> loads currentTagNames + all other profile fields
profileRepo.getMasterTags(...) -> loads the master chip list
```

Cross-preservation is correctly handled. The profile callback reads `current.masterTags` at call time and carries it forward into the new state object. The `masterTags` callback calls `currentState().withMasterTags(tags)`, which carries the profile fields forward. Neither overwrites the other's data.

Both results flow into `uiState` (`MutableLiveData<EditProfileUiState>`), which `EditProfileFragment` observes via `viewModel.getUiState().observe(...)`.

Structural caveat - `postValue()` race: both callbacks call `uiState.postValue()` from executor background threads. `postValue()` internally coalesces pending posts; if both threads read `currentState()` before either dispatch runs on the main thread, the later post will overwrite the earlier one and one field set (profile fields or master tags) will be lost for that emission.

In practice the two network calls almost never complete simultaneously, but this is a latent concurrency defect in the pattern. The correct fix is to merge state on the main thread via `setValue()`.

---

## 2. UI Rendering and Pre-selection

**Verdict:** Fully correct. One dead-code field.

`renderState()` contains this guard:

```java
if (!state.masterTags.isEmpty() && chipGroupTags.getChildCount() == 0) {
    populateTagChips(state.masterTags, state.currentTagNames);
    tagsPreSelected = true;
}
```

The guard condition `chipGroupTags.getChildCount() == 0` ensures chips are only inflated once, preventing duplicate rows on subsequent state emissions. The `tagsPreSelected = true` assignment is dead code because the field is declared but never read. The actual guard is the child-count check.

`populateTagChips()`:

```java
Chip chip = new Chip(requireContext());
chip.setText(tag.getTagName());
chip.setTag(tag.getTagId());
chip.setCheckable(true);
chip.setChecked(selectedNames != null && selectedNames.contains(tag.getTagName()));
```

UUID assignment to `chip.setTag()` is correct. Pre-selection compares tag names (`selectedNames = state.currentTagNames = profile.getTags() = names returned by findTagsByUserId JOIN query`). Since the backend populates `currentTagNames` with the canonical `tag_name` from `profile_tag_master`, and master chips are also labeled with the same canonical name, the `contains()` match is reliable. No UUID-vs-name mismatch exists at this step.

The `ChipGroup` XML lacks explicit selection attributes. `fragment_edit_profile.xml` declares `chipGroupTags` with no `app:singleSelection` or `app:selectionRequired`. Material defaults these to `false` / `false`, so multi-select is active and un-selecting all chips is permitted. This is intended behavior.

---

## 3. Data Collection on Save

**Verdict:** Correct. Extracts UUIDs, not names.

```java
private List<String> collectSelectedTagIds() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
        View child = chipGroupTags.getChildAt(i);
        if (child instanceof Chip && ((Chip) child).isChecked()) {
            Object tagId = child.getTag();
            if (tagId instanceof String) result.add((String) tagId);
        }
    }
    return result;
}
```

`child.getTag()` returns the UUID string stored by `chip.setTag(tag.getTagId())`. The `instanceof String` guard prevents `ClassCastException` if any non-Chip view (for example, a divider) appears as a child. The resulting list is UUID strings, not display names.

Empty-selection case: if the user deselects all chips, `collectSelectedTagIds()` returns an empty list. `EditProfileViewModel.save()` does not reject an empty tag list (validation only rejects lists over 10 entries). The empty list flows through to `replaceTagsByIds(userId, [])`, which executes only `DELETE` and skips the `INSERT` loop, producing a clean wipe. This is valid UX.

---

## 4. API Payload

**Verdict:** Fully aligned. No serialization mismatch.

| Layer | Field name | Type |
|---|---|---|
| Frontend DTO (`UpdateProfileRequestDto`) | `@SerializedName("tagIds")` | `List<String>` |
| JSON wire | `"tagIds": ["uuid-str", ...]` | - |
| Backend record (`UpdateProfileRequest`) | `tagIds` | `List<UUID>` |

Gson serializes the frontend field as `tagIds` (via `@SerializedName`). Jackson on the backend deserializes `tagIds` into `List<UUID>`; Jackson's default UUID deserializer accepts UUID-formatted strings transparently. No mapping annotation is needed on the record side.

No mismatch found on the tag field. The other fields (`fullName`, `gender`, `dateOfBirth`, `bio`, `searchRadius`) likewise share identical names front-to-back.

---

## 5. Backend Persistence

**Verdict:** Correctly implemented wipe-and-replace inside a transaction.

`UserProfileCommandService.updateProfile()` is `@Transactional`:

```java
UserProfile saved = profileRepository.save(profile);
List<UUID> tagIds = command.tagIds() != null ? command.tagIds() : List.of();
profileRepository.replaceTagsByIds(command.callerId(), tagIds);
```

`replaceTagsByIds()` in JDBC repo (also `@Transactional`, participates in outer transaction):

```sql
DELETE FROM user_profile_tag_map WHERE user_id = :userId;

INSERT INTO user_profile_tag_map (user_id, tag_id)
VALUES (:userId, :tagId)
ON CONFLICT DO NOTHING;
```

The delete-before-insert pattern eliminates all stale mappings atomically. `ON CONFLICT DO NOTHING` is a defensive measure (PK on junction table is `(user_id, tag_id)`), adding robustness against concurrent duplicate inserts. If any insert fails for a non-conflict reason, the outer transaction rolls back the entire operation, including user-profile UPSERT.

The response from controller re-fetches tags via `queryService.getTagsByUserId(callerId)` after update:

```java
UserProfile updated = commandService.updateProfile(command);
List<String> tags = queryService.getTagsByUserId(callerId);
return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(updated, user, tags)));
```

This ensures the response reflects persisted state, not request payload.

---

## 6. Final Verdict

The core tag refactor is correctly implemented end-to-end. UUID-based tag selection, chip rendering, pre-selection, UUID collection, serialization, and wipe-and-replace persistence form a clean unbroken chain.

However, there are two real bugs and two code quality issues not related to the tag refactor itself.

### Bug 1: Gender string mismatch (functional breakage for two of four options)

Frontend `GENDER_OPTIONS`:

```text
{"Male", "Female", "Non-binary", "Prefer not to say"}
```

Backend `parseGender()`:

```java
Gender.valueOf(raw.toUpperCase())
// "Male"              -> "MALE"              OK
// "Female"            -> "FEMALE"            OK
// "Non-binary"        -> "NON-BINARY"        IllegalArgumentException
// "Prefer not to say" -> "PREFER NOT TO SAY" IllegalArgumentException
```

Selecting "Non-binary" or "Prefer not to say" throws `DomainException` on backend and user gets save error. Correct values to send should be `OTHER` and `PREFER_NOT_TO_SAY` (matching enum constants). This needs a mapping step in `EditProfileFragment` or repository before raw string reaches API.

Additionally, when loading profile, `state.gender` arrives as backend enum name (for example, `MALE`) and is set directly via `spinnerGender.setText("MALE", false)`. This does not match adapter items (`Male`, `Female`, ...), so displayed value becomes raw enum text rather than user-friendly label. Display direction and send direction are both broken for values that do not coincidentally match labels.

### Bug 2: Search radius input accepts decimals but parser uses parseInt

```xml
android:inputType="numberDecimal"
```

```java
private static int parseRadius(String raw) {
    try { return Integer.parseInt(raw); }
    catch (NumberFormatException e) { return 0; }
}
```

If user enters `5.5`, `Integer.parseInt("5.5")` throws and parser silently returns `0`. Backend `@Min(1)` then rejects `searchRadius = 0` with validation error, which is confusing. Input type should be integer-only, or parser should handle decimal safely.

### Code quality: tagsPreSelected is dead code

```java
private boolean tagsPreSelected = false;
// set to true after chip population, but never read
```

Recommendation: remove this field.

### Code quality: Parallel postValue() is a latent lost-update race

As noted in Section 1, both `getMyProfile` and `getMasterTags` callbacks call `uiState.postValue()` from background threads. Better pattern is merging state on main thread or using a combined reactive source (`MediatorLiveData` / equivalent). Under normal network latency this rarely reproduces, but it remains fragile.

---

## Summary Table

| # | Area | Status | Severity |
|---|---|---|---|
| Core tag UUID chip render | Section 2 | Correct | - |
| Core tag UUID collection | Section 3 | Correct | - |
| Core tag DTO serialization | Section 4 | Correct | - |
| Core tag wipe-and-replace | Section 5 | Correct | - |
| Eager dual fetch + cross-preservation | Section 1 | Correct | - |
| postValue() race condition | Section 1 | Latent risk | Low |
| tagsPreSelected dead code | Section 2 | Dead code | Low |
| Gender string to enum mismatch | Section 6 - Bug 1 | Functional bug | High |
| numberDecimal + parseInt | Section 6 - Bug 2 | Functional bug | Medium |