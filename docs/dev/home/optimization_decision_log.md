# Optimization Decision Log — Phases C & D (Profile + Navigation)

**Date:** 2026-03-31  
**Branch:** `improve/coordination-flow`

---

## 1. View Tree — 3-Column Stats Row

**Problem:** The natural implementation of a 3-column stat row (`Total KM | Sessions | Day Streak`) uses three horizontal `LinearLayout` children with `layout_weight="1"`. This produces a 3-level nesting depth and triggers a **double measure pass** on every weighted child (Android measures each `layout_weight` view twice: once to calculate available space, once to allocate it).

**Optimization:** A single flat `ConstraintLayout` with two `Guideline`s at 33.3%/66.7% and vertical packed **Chains** for each column. This resolves 9 content views in **one measure pass** with zero nesting.

| Metric | `LinearLayout` + weights | `ConstraintLayout` + Guidelines |
|---|---|---|
| View count | 12 (9 content + 3 wrappers) | 11 (9 content + 2 guidelines) |
| Nesting depth | 3 levels | **1 level** |
| Measure passes | Double on each weighted child | **Single pass** |

**Key Takeaway:** `ConstraintLayout` + `Guideline`s is the canonical way to divide a row into equal-width columns with zero measure overhead; `layout_weight` is only acceptable for a **fixed, small count of columns** in a non-performance-critical container — and even then, only one level deep.

---

## 2. Badge Columns — Deliberate `layout_weight` Exception

**Problem:** The badge section also has 3 equal-width columns. Applying the same `ConstraintLayout` + `Guideline` pattern here requires 3 additional vertical chains, adding 6 more constraint declarations for a section that only renders 3 static slots.

**Optimization:** The badges row uses `LinearLayout` + `layout_weight="1"` (3 children). This is a **conscious, documented exception** because:
1. The column count is static (never dynamic). No adapter inflates these.
2. The parent `LinearLayout` renders only once and is never re-measured by scroll or animation.
3. The complexity trade-off is asymmetric: `ConstraintLayout` would add ~30 lines of XML for a negligible gain on a view that measures exactly once.

**Key Takeaway:** Premature optimization is a code smell. Apply `ConstraintLayout` flattening where the view tree is deep **and** frequently measured (e.g., RecyclerView items, animated containers). Static, single-measure containers with 2–3 equal columns don't justify the XML overhead.

---

## 3. Dynamic Views — `tools:` Namespace vs. `android:text`

**Problem:** Layout XML views that receive runtime data from a ViewModel, if given `android:text="..."` hardcoded values, cause two concrete defects:
1. **Lint `HardcodedText`** — flagged as a localization violation; degrades CI signal.
2. **UI flicker** — on slower devices, the hardcoded value is visible for one frame before the LiveData observer fires and writes the real value.

**Optimization:** All dynamic views (`txtProfileName`, `chipTrustScore`, stat value `TextView`s, badge `ImageView`s) use `tools:text` / `tools:src` for design-time preview values. The `tools:` namespace is stripped at compile time and never ships in the APK.

**Key Takeaway:** `tools:` attributes cost zero APK bytes and eliminate both the lint warning and the flicker — use them on every view whose value is set at runtime.

---

## 4. Badge Resource IDs in `ProfileUiState.Badge`

**Problem:** A naive implementation stores badge labels as `String` values (e.g., `"First Walk"`). The Fragment then does a `switch` or `String.equals()` comparison to look up the correct drawable and string resource at render time — fragile, untestable, and locale-blind.

**Optimization:** `ProfileUiState.Badge` stores `int labelStringResId` and `int iconDrawableResId` — Android compile-time constants. The ViewModel maps badge identifiers to `R.drawable.*` and `R.string.*` IDs once, in `buildMockState()`. The Fragment calls `setImageResource(badge.iconDrawableResId)` and `setText(badge.labelStringResId)` directly — no string matching, no switch, fully locale-aware.

**Key Takeaway:** Use `@DrawableRes int` / `@StringRes int` fields in UiState inner classes whenever the ViewModel knows the exact resource at map-time. This removes presentation logic from the Fragment and makes the badge binding a single, untestable-free line per slot.

---

## 5. Personality Tags — Static Slot Binding vs. Dynamic ChipGroup

**Problem:** Personality tags are a variable-length list (0–3 tags). One approach is to clear the `ChipGroup` and add `Chip`s programmatically in `renderState()`. This allocates new `Chip` objects every time the state updates, causing GC pressure and brief layout jank.

**Optimization:** Three static `Chip` slots (`chipTag1`, `chipTag2`, `chipTag3`) are declared in XML and set to `visibility="gone"` by default. `renderState()` binds each slot's text and shows/hides it. Zero allocation per render call.

**Key Takeaway:** For lists with a known, small upper bound (≤ 3–5 items), static slot binding outperforms dynamic inflation — no allocation, no layout re-measure of newly inflated children.

---

## 6. Loading State — Return-Early Pattern

**Problem:** If `renderState()` writes to Views while `state.isLoading() == true`, it writes null/zero values (from the `loading()` factory) to the UI, causing a flash of blank fields before real data arrives.

**Optimization:** `renderState()` opens with `if (state.isLoading()) return;`. The method does nothing until a non-loading state arrives. The layout's `tools:` preview values remain visible in the editor; no content is ever visible at runtime from the loading state.

**Key Takeaway:** The return-early pattern in `renderState()` is the idiomatic LiveData guard — pair it with `tools:` placeholders to get a clean, flicker-free first render without a dedicated skeleton/shimmer overlay.

---

## 7. Navigation Decoupling — Listener Interface vs. Direct Activity Cast

**Problem:** `HomeFragment` needs to tell the host (`MainActivity`) to navigate to the Explore screen when the user taps "Find a WalkMate Now". The naive implementation is:

```java
// WRONG — Fragment is tightly coupled to a concrete Activity class
((MainActivity) getActivity()).showExplore();
```

This produces three concrete defects:
1. **ClassCastException at runtime** if the Fragment is ever hosted in a different Activity (e.g., a test harness, a deep-link entry point, or a future `SingleFragmentActivity`).
2. **Untestability** — unit-testing `HomeFragment` in isolation requires constructing a full `MainActivity`, making the test scope impossibly large.
3. **Violation of the Dependency Inversion Principle** — a lower-level module (`HomeFragment`) depends on a higher-level concrete type (`MainActivity`) rather than an abstraction.

**Optimization:** `HomeFragment` defines an inner `OnHomeActionListener` interface with a single method `switchToExplore()`. The host Activity implements it. The Fragment acquires the reference in `onAttach(Context)` and nulls it in `onDetach()`.

```java
// HomeFragment.java
public interface OnHomeActionListener {
    void switchToExplore();
}

@Override
public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (!(context instanceof OnHomeActionListener)) {
        throw new IllegalStateException(context.getClass().getSimpleName()
                + " must implement HomeFragment.OnHomeActionListener");
    }
    listener = (OnHomeActionListener) context;
}

@Override
public void onDetach() {
    super.onDetach();
    listener = null;  // prevent Activity leak across config changes
}
```

The `onDetach()` null is critical: without it, the `listener` field keeps a strong reference to the Activity after it is destroyed (e.g., on rotation), preventing garbage collection until the Fragment is also destroyed — a classic memory leak.

The `IllegalStateException` in `onAttach()` is a **fail-fast contract**: the crash happens at the earliest possible moment (Fragment attachment) rather than silently at the click site, giving a clear stack trace that points to the missing `implements` declaration.

**Why not `SingleLiveEvent<Void>` in the ViewModel instead?**  
A `SingleLiveEvent` navigation approach is valid and commonly used in MVI architectures. The listener pattern is preferred here because:
- Navigation from Home → Explore is a pure UI concern; the ViewModel has no reason to know about it.
- It avoids the `SingleLiveEvent` vs `StateFlow` debate entirely.
- The interface is visible and enforced at compile-link time via the `implements` declaration on `MainActivity`.

**Key Takeaway:** Fragments should communicate with their host via an interface contract defined inside the Fragment itself. Acquire in `onAttach`, null in `onDetach`, and throw `IllegalStateException` — not `ClassCastException` — to surface missing implementations at the earliest possible moment.

---

## 8. Custom Views vs. XML `<include>` — Why Logic-Heavy Patterns Need a Class

**Decision Date:** 2026-03-31  
**Branch:** `improve/coordination-flow`

**Problem:** The input field pattern (label + EditText + password toggle + error display) appears in at least 6 locations across `LoginFragment`, `RegisterFragment`, and future form screens. The naive approach is `<include layout="@layout/partial_input_field" />` with each Fragment managing toggle state and error clearing itself.

This produces three concrete defects:

1. **Duplicated state:** Each Fragment reimplements an `isPasswordVisible` boolean, a `togglePasswordVisibility()` helper, and a `setSelection()` cursor fix. Any bug (e.g., cursor jumping to position 0 after toggling) must be fixed in N places.
2. **Fragile binding:** `<include>` requires the Fragment to `findViewById` all inner views by ID. Adding a sub-view to the partial layout silently breaks every Fragment that forgot to bind it.
3. **Untestable:** Password toggle logic lives in `onViewCreated()` — impossible to unit-test without a rooted Fragment.

**Decision:** Promote to `WalkMateInputField extends LinearLayout` (Custom View) with `<merge>` root layout.

| Criterion              | XML `<include>`                               | `WalkMateInputField` Custom View                    |
| ---------------------- | --------------------------------------------- | --------------------------------------------------- |
| Internal state         | Fragment holds `isPasswordVisible` per field  | Encapsulated — Fragment never sees it               |
| Error display          | Fragment calls `tvError.setVisibility()`      | `field.setError(msg)` — 1 line, null clears it      |
| Custom XML attributes  | None — all config is hardcoded in the partial | `app:wm_label`, `app:wm_hint`, `app:wm_icon`, etc. |
| Bug fix propagation    | Must fix in every Fragment                    | Fix once in `WalkMateInputField.java`               |
| Cursor-end fix on toggle | Each Fragment reimplements `setSelection()` | Owned by `togglePasswordVisibility()` internally    |
| Testability            | Requires Fragment instrumentation test        | Unit-testable in isolation                          |

**Implementation Details:**
- Layout: `res/layout/view_walkmate_input_field.xml` — `<merge>` root, flat `ConstraintLayout` container (EditText + toggle icon side-by-side, no nesting).
- Class: `core/designsystem/view/WalkMateInputField.java` — reads `TypedArray` attributes in `init()`, exposes `getText()`, `setError(@Nullable String)`, `clearError()`, `addTextChangedListener()`.
- Attributes: `res/values/attrs.xml` → `declare-styleable name="WalkMateInputField"`.

**When `<include>` is still appropriate:**
Use `<include>` only for **pure layout reuse** — e.g., a static divider, a fixed header graphic, or a layout sub-tree that never changes at runtime and has no logic. As soon as a view needs to show/hide, change color, or hold a boolean flag, it earns a Custom View class.

**Key Takeaway:** `<include>` is a copy-paste mechanism with a filename. Custom Views are encapsulation units. Use `<include>` for static partials; use Custom Views the moment state or logic enters the picture. The boundary is sharp: if a Fragment needs to `setVisibility()` on anything inside an included layout, that layout should be a Custom View instead.

---

## 9. WalkMateButton — Why a FrameLayout Wrapper Beats Subclassing MaterialButton

**Decision Date:** 2026-03-31

**Problem:** The app has two button variants (gradient-filled orange pill, outlined orange stroke) that both need a loading state (ProgressBar overlay). The naive options are:

1. Subclass `MaterialButton` → `MaterialButton` has an internal background tinting pipeline that intercepts `setBackgroundResource()` and always applies a solid `backgroundTint` on top of the drawable. Getting a gradient drawable to render correctly requires nulling the tint programmatically — brittle and version-sensitive.

2. Use `FrameLayout` containing `AppCompatButton` + `ProgressBar` → `AppCompatButton` has no `backgroundTint` interference. `setBackgroundResource()` works cleanly. The `ProgressBar` floats centered via `FrameLayout`'s default gravity system.

**Decision:** `WalkMateButton extends FrameLayout` with a `<merge>` root layout. The inner `AppCompatButton` fills the FrameLayout, and `ProgressBar` (24dp, `layout_gravity="center"`) overlays it.

**Loading state implementation:**
- `setLoading(true)`: `btnAction.setText("")` (stores original first), `btnAction.setEnabled(false)`, `pbLoading.setVisibility(VISIBLE)`.
- `setLoading(false)`: restores stored text, re-enables, hides spinner.
- Spinner tint: white for FILLED (readable on orange gradient), orange for OUTLINED (readable on transparent).

**Key Takeaway:** When a Material component's own theming pipeline conflicts with your custom drawable requirements, wrap it in a FrameLayout rather than fighting the internal tinting system.

---

## 10. TagChipGroup — Extending ChipGroup vs. Wrapping It

**Decision Date:** 2026-03-31

**Problem:** Should `TagChipGroup` extend `ChipGroup` directly (making it IS-A ChipGroup) or extend `LinearLayout` / `FrameLayout` and contain a `ChipGroup` (HAS-A)?

**Decision:** Extend `ChipGroup` directly.

**Why IS-A wins here:**
1. All standard ChipGroup XML attributes (`app:chipSpacingHorizontal`, `app:singleSelection`, etc.) work without any delegation boilerplate.
2. The view IS functionally a ChipGroup with a smarter `setTags()` method — adding a wrapper layer would break the Liskov substitution principle (anywhere a `ChipGroup` reference is accepted, a `TagChipGroup` should work).
3. No layout file is needed — one fewer file to maintain.

**The exception to the "layout required" rule:** Custom Views need a `view_*.xml` only when they compose multiple child views. A single-concern subclass of an existing ViewGroup (like `TagChipGroup extends ChipGroup`) has no composition to describe.

**Chip creation via `ContextThemeWrapper`:**
```java
Chip chip = new Chip(new ContextThemeWrapper(getContext(), R.style.Widget_WalkMate_Chip_Active));
```
This is the canonical pattern for applying an XML `<style>` to a View created programmatically. It avoids manually setting each property (corner radius, text size, padding, stroke, etc.) and means future style changes propagate automatically.

**Key Takeaway:** IS-A (extend) is correct when the custom class is semantically the same type with added behaviour. HAS-A (wrap) is correct when the custom class composes multiple sub-views. Don't wrap single-concern subclasses — you only create delegation boilerplate.

---

## 11. GlideHelper — Centralising the Image Loading Boundary

**Decision Date:** 2026-03-31

**Problem:** Six call sites (HomeFragment, ProfileFragment, QuickInviteAdapter, SessionAdapter, FindingAdapter, ProposalAdapter) each duplicate the same 5-line Glide null-guard + chain. Any Glide configuration change (timeout, cache strategy, error drawable, migration to Coil) requires touching all 6.

**Decision:** `GlideHelper` in `core/util/` — a `final` utility class with a private constructor, exposing only static methods (`loadCircle`, `loadRounded`, `load`, `cancel`).

**Why `core/util/` not `core/designsystem/view/`:** It's not a View — it's a stateless function. The `designsystem/view/` package is reserved for `View` subclasses. Utility functions that don't produce a UI element belong in `core/util/`.

**Architectural contract enforced:**
- Only `GlideHelper` and `AvatarInitialView` may import `com.bumptech.glide.*`.
- `AvatarInitialView` is exempted because it owns its own Glide lifecycle (cancellation on `INVISIBLE`, transparent placeholder for the initial-letter fallback).
- All other `ImageView` loads go through `GlideHelper`.

**Key Takeaway:** The "one file to touch on migration" principle. Isolate every third-party library call behind a project-owned façade so a future library swap is a refactor of one file, not a grep-and-replace across the entire codebase.
