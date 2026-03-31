# Workphase v0.5 — Global UI Modularization (Custom View Initiative)

**Date:** 2026-03-31  
**Branch:** `improve/coordination-flow`  
**Status:** ✅ COMPLETE — Full Design System Synchronized

---

## 1. Context

This workphase initiated and completed the project-wide push to extract repeated UI patterns from Fragments and Adapters into **Custom View components** living in `core/designsystem/view/`.

Prior workphases (v0.1–v0.3) focused on **data-layer architecture** (Room, GPS tracking, service lifecycle). v0.5 is the first **presentation-layer** modularization pass, triggered by a systematic scan of all 18 layout XML files and 15+ UI Java files.

---

## 2. Deliverables — Full Rollout

| Artefact | Path | Status |
|---|---|---|
| Custom attributes | `res/values/attrs.xml` | ✅ Done |
| `WalkMateInputField` XML | `res/layout/view_walkmate_input_field.xml` | ✅ Done |
| `WalkMateInputField` Java | `core/designsystem/view/WalkMateInputField.java` | ✅ Done |
| `WalkMateButton` XML | `res/layout/view_walkmate_button.xml` | ✅ Done |
| `WalkMateButton` Java | `core/designsystem/view/WalkMateButton.java` | ✅ Done |
| `AvatarInitialView` XML | `res/layout/view_avatar_initial.xml` | ✅ Done |
| `AvatarInitialView` Java | `core/designsystem/view/AvatarInitialView.java` | ✅ Done |
| `WalkMateStatColumn` XML | `res/layout/view_walkmate_stat_column.xml` | ✅ Done |
| `WalkMateStatColumn` Java | `core/designsystem/view/WalkMateStatColumn.java` | ✅ Done |
| `TagChipGroup` Java | `core/designsystem/view/TagChipGroup.java` | ✅ Done (no layout — extends ChipGroup) |
| `WalkMateCardHeader` XML | `res/layout/view_walkmate_card_header.xml` | ✅ Done |
| `WalkMateCardHeader` Java | `core/designsystem/view/WalkMateCardHeader.java` | ✅ Done |
| `GlideHelper` Java | `core/util/GlideHelper.java` | ✅ Done (no layout — static utility) |
| SSOT Section 8 + Catalogue | `docs/single-source-of-truth/architecture/Frontend_VI.md` | ✅ Done |
| Optimization log entries #8–#11 | `docs/dev/home/optimization_decision_log.md` | ✅ Done |

---

## 3. How to Use — Component Reference Guide

### 3.1 `WalkMateInputField`

**Replaces:** Label `TextView` + `EditText` + `RelativeLayout` password wrapper + `isPasswordVisible` boolean in every form Fragment.

```xml
<!-- Plain text field -->
<com.walkmate.core.designsystem.view.WalkMateInputField
    android:id="@+id/field_email"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="20dp"
    app:wm_label="Email Address"
    app:wm_hint="hello@example.com"
    app:wm_icon="@drawable/ic_mail"
    app:wm_inputType="textEmailAddress" />

<!-- Password field — toggle managed internally -->
<com.walkmate.core.designsystem.view.WalkMateInputField
    android:id="@+id/field_password"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    app:wm_label="Password"
    app:wm_hint="Enter your password"
    app:wm_icon="@drawable/ic_lock"
    app:wm_passwordToggle="true" />
```

```java
// Fragment — renderState()
fieldEmail.setError(state.getEmailError());      // null clears error
fieldPassword.setError(state.getPasswordError());

// Fragment — submit action
viewModel.signIn(fieldEmail.getText(), fieldPassword.getText());
```

---

### 3.2 `WalkMateButton`

**Replaces:** `AppCompatButton`/`MaterialButton` with `bg_gradient_orange_pill` or `bg_button_outline_orange`, plus manual `isLoading` logic per Fragment.

```xml
<!-- Primary action (gradient fill) -->
<com.walkmate.core.designsystem.view.WalkMateButton
    android:id="@+id/btn_sign_in"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_marginTop="24dp"
    app:wm_buttonStyle="filled"
    app:wm_text="Sign In ✦" />

<!-- Secondary action (outlined) -->
<com.walkmate.core.designsystem.view.WalkMateButton
    android:id="@+id/btn_pass"
    android:layout_width="0dp"
    android:layout_height="48dp"
    android:layout_weight="1"
    app:wm_buttonStyle="outlined"
    app:wm_text="@string/btn_pass" />
```

```java
// Fragment — renderState() — one line for loading
btnSignIn.setLoading(state.isLoading());

// Fragment — click listener
btnSignIn.setOnClickListener(v -> viewModel.signIn(...));
```

---

### 3.3 `AvatarInitialView`

**Replaces:** `FrameLayout` (bg_warm_circle) + `TextView` initial + `ImageView` + Glide loading block in every adapter and Fragment.

```xml
<com.walkmate.core.designsystem.view.AvatarInitialView
    android:id="@+id/avatar"
    android:layout_width="52dp"
    android:layout_height="52dp"
    app:wm_avatarName="Thu Hà"
    app:wm_showOnlineStatus="true" />
```

```java
// Adapter — bind()
avatar.bind(user.getName(), user.getPhotoUrl());

// With online dot
avatar.bind(user.getName(), user.getPhotoUrl(), user.isOnline());

// Large profile avatar (88dp) — bigger initial text
avatar.setInitialTextSizeSp(32f);
```

---

### 3.4 `WalkMateStatColumn`

**Replaces:** The duplicated `icon → value → label` pattern in Home stats cards, Profile stats row, and Tracking screen columns.

```xml
<!-- In Home stats card (with icon) -->
<com.walkmate.core.designsystem.view.WalkMateStatColumn
    android:id="@+id/statDistance"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:wm_statIcon="@drawable/ic_distance"
    app:wm_statValue="—"
    app:wm_statLabel="km this week" />

<!-- In Home stats card (with emoji) -->
<com.walkmate.core.designsystem.view.WalkMateStatColumn
    android:id="@+id/statStreak"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:wm_statEmoji="🔥"
    app:wm_statValue="—"
    app:wm_statLabel="day streak" />

<!-- Tracking screen — larger value text -->
<com.walkmate.core.designsystem.view.WalkMateStatColumn
    android:id="@+id/statPace"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:wm_statValue="—"
    app:wm_statLabel="PACE"
    app:wm_statValueSize="26sp" />
```

```java
// Fragment — renderState()
statDistance.bind("12.5", "km this week", R.drawable.ic_distance);
statStreak.bind(String.valueOf(state.getStreak()), "day streak", "🔥");
statPace.setValue(state.getFormattedPace());
```

---

### 3.5 `TagChipGroup`

**Replaces:** `chipGroup.removeAllViews()` + `new Chip()` creation loop in ProposalAdapter, FindingAdapter, ExploreFragment, CreateIntentFragment.

```xml
<!-- Read-only display chips in proposal/finding cards -->
<com.walkmate.core.designsystem.view.TagChipGroup
    android:id="@+id/chipGroupTags"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="6dp"
    app:chipSpacingHorizontal="6dp"
    app:chipSpacingVertical="4dp"
    app:wm_chipStyle="display" />

<!-- Selectable filter chips in CreateIntentFragment -->
<com.walkmate.core.designsystem.view.TagChipGroup
    android:id="@+id/chipGroupInterests"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:wm_chipStyle="selectable"
    app:singleSelection="false" />
```

```java
// Adapter — bind() — replaces the entire manual chip loop
chipGroupTags.setTags(proposal.getCommonInterests());

// Fragment — replace ExploreFragment chip population
chipGroupHotspots.setTags(state.getHotspotNames());
```

---

### 3.6 `WalkMateCardHeader`

**Replaces:** `LinearLayout (horizontal)` + emoji `TextView` + title `TextView (layout_weight="1")` + chevron `ImageView` in Home screen cards and Profile screen.

```xml
<!-- Tappable header (chevron shown — default) -->
<com.walkmate.core.designsystem.view.WalkMateCardHeader
    android:id="@+id/headerStreak"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:wm_headerEmoji="🔥"
    app:wm_headerTitle="Walking Streak"
    app:wm_navigable="true" />

<!-- Info-only header (no chevron) -->
<com.walkmate.core.designsystem.view.WalkMateCardHeader
    android:id="@+id/headerStats"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:wm_headerEmoji="📊"
    app:wm_headerTitle="This Week"
    app:wm_navigable="false" />
```

```java
// Fragment — renderState() — dynamic title
headerStreak.setTitle(state.getStreakDays() + " / 7 days");
headerStreak.setOnClickListener(v -> /* navigate to streak detail */);

// Conditionally hide chevron
headerSessions.setNavigable(state.hasUpcomingSessions());
```

---

### 3.7 `GlideHelper`

**Replaces:** Inline Glide null-guard block in HomeFragment, ProfileFragment, QuickInviteAdapter, SessionAdapter, FindingAdapter, ProposalAdapter.

```java
// Before (6 copies of this):
if (url != null && !url.isEmpty()) {
    Glide.with(this).load(url).circleCrop()
         .placeholder(R.drawable.ic_user).into(imageView);
} else {
    imageView.setImageResource(R.drawable.ic_user);
}

// After — one line:
GlideHelper.loadCircle(imageView, user.getPhotoUrl());

// With custom placeholder:
GlideHelper.loadCircle(imageView, url, R.drawable.ic_placeholder_badge);

// Rounded corners (e.g. card thumbnails):
GlideHelper.loadRounded(imageView, url, 12); // 12dp radius

// Cancel (in onDestroyView or ViewHolder.recycle):
GlideHelper.cancel(imageView);
```

> **Note:** For circular avatars with an initials fallback, use `AvatarInitialView.bind()` instead — it handles the photo/initial switching automatically.

---

## 4. Architecture Decisions Made This Phase

1. **`<merge>` root** — Applied to WalkMateInputField, WalkMateButton, AvatarInitialView, WalkMateStatColumn, WalkMateCardHeader. Eliminates one redundant ViewGroup from each custom view's tree.

2. **Flat ConstraintLayout inside WalkMateInputField and WalkMateCardHeader** — Zero-nesting siblings constrained to each other. Eliminates the `layout_weight` double-measure pass in card headers (log entry #1 pattern).

3. **TagChipGroup extends ChipGroup (IS-A)** — No layout file needed. Documented exception to the "view_*.xml required" rule. Standard ChipGroup attributes continue to work. (log entry #10)

4. **WalkMateButton wraps AppCompatButton in FrameLayout** — Avoids `MaterialButton` backgroundTint pipeline interference with gradient drawables. ProgressBar floats centered via FrameLayout gravity. (log entry #9)

5. **AvatarInitialView: initial always rendered; photo overlaid when loaded** — Transparent Glide placeholder means the initial letter stays visible while the network request is in flight. Photo covers it on success; initial remains visible on failure. No explicit callback needed.

6. **GlideHelper in `core/util/` not `core/designsystem/view/`** — Not a View. Static utility functions belong in `util/`. Only `GlideHelper` and `AvatarInitialView` may import `com.bumptech.glide.*`. (log entry #11)

---

## 5. Files Created / Modified This Phase

**Created (new):**
- `res/values/attrs.xml`
- `res/layout/view_walkmate_input_field.xml`
- `res/layout/view_walkmate_button.xml`
- `res/layout/view_avatar_initial.xml`
- `res/layout/view_walkmate_stat_column.xml`
- `res/layout/view_walkmate_card_header.xml`
- `core/designsystem/view/WalkMateInputField.java`
- `core/designsystem/view/WalkMateButton.java`
- `core/designsystem/view/AvatarInitialView.java`
- `core/designsystem/view/WalkMateStatColumn.java`
- `core/designsystem/view/TagChipGroup.java`
- `core/designsystem/view/WalkMateCardHeader.java`
- `core/util/GlideHelper.java`

**Updated:**
- `docs/single-source-of-truth/architecture/Frontend_VI.md` — Section 8 added + catalogue expanded to all 7 components
- `docs/dev/home/optimization_decision_log.md` — Entries #8, #9, #10, #11 appended

---

## 6. Handoff Checklist

**Design System — DONE:**
- [x] All 7 components created (XML + Java)
- [x] `attrs.xml` covers all 7 styleables
- [x] SSOT Section 8 + catalogue updated
- [x] Optimization log entries written

**Integration — Pending (next sprint):**
- [ ] `LoginFragment` migrated to `WalkMateInputField` + `WalkMateButton`
- [ ] `RegisterFragment` migrated to `WalkMateInputField` + `WalkMateButton`
- [ ] `ProposalAdapter` migrated to `AvatarInitialView` + `TagChipGroup`
- [ ] `FindingAdapter` migrated to `AvatarInitialView` + `TagChipGroup`
- [ ] `HomeFragment` stats row migrated to `WalkMateStatColumn` + `WalkMateCardHeader`
- [ ] `TrackingScreenActivity` stats row migrated to `WalkMateStatColumn`
- [ ] All 6 Glide call sites replaced with `GlideHelper.loadCircle()`
- [ ] Integration smoke-test on all 5 main screens
