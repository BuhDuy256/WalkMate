# Gap Analysis: V3.0 UI Redesign

**Scope:** Home (Dashboard) + Profile screens  
**Baseline:** Current XML in `frontend/src/main/res/layout/`  
**Target:** V3.0 specification

---

## 1. Screen 1 — HOME (DASHBOARD)

### 1.1 Root Architecture Mismatch

| | Current | Target |
|---|---|---|
| **File** | `fragment_explore.xml` | `fragment_home.xml` (NEW) |
| **Root** | `CoordinatorLayout` + `BottomSheetBehavior` | `NestedScrollView` |
| **Nature** | Map-based exploration screen | Scrollable dashboard |

**Verdict:** The current `fragment_explore.xml` is a map fragment with a `BottomSheetBehavior` bottom panel. The V3.0 Home spec is a completely different layout type (scrollable card dashboard with no map). These two UIs are **structurally incompatible** — a new `fragment_home.xml` must be created.

> `fragment_explore.xml` itself remains valid for the map/explore flow; the Home *tab* must be re-pointed to the new dashboard fragment.

---

### 1.2 Missing Components

| # | Spec Component | Status |
|---|---|---|
| 1 | Top App Bar — stacked greeting + location `TextView`s | ❌ Missing |
| 2 | Top App Bar — Notification Bell `FrameLayout` with orange badge `View` | ❌ Missing |
| 3 | Streak Widget `MaterialCardView` (Fire icon + `ProgressBar` + "5/7 days") | ❌ Missing |
| 4 | Hero Action `MaterialCardView` (weather text + full-width orange pill `MaterialButton`) | ❌ Missing |
| 5 | Upcoming Session `MaterialCardView` (avatar + partner info + outlined Message button) | ❌ Missing |
| 6 | Quick Invite section header ("Rủ rê nhanh" + "See all" link) | ❌ Missing |
| 7 | Quick Invite horizontal `RecyclerView` | ❌ Missing |
| 8 | Quick Stats row — 3 equal `MaterialCardView`s | ❌ Missing |

---

### 1.3 Architecture Violations in `fragment_explore.xml`

These do not affect the new Dashboard but must be fixed in the explore file to meet project standards.

| Line | Violation | Rule from `Frontend_VI.md` |
|---|---|---|
| 404 | `app:haloColor="#40FF9E67"` — hardcoded hex | All colors must reference `@color/` entries |
| 504 | `app:haloColor="#40FF9E67"` — same violation repeated | Same |

**Fix:** Either use the existing `@color/shadow_orange` (`#59FF7B3A`) or add a dedicated `color_orange_halo` (`#40FF9E67`) entry to `colors.xml`.

---

## 2. Screen 2 — PROFILE

### 2.1 Root Architecture

| | Current | Target |
|---|---|---|
| **Root** | `FrameLayout` (stub) | `NestedScrollView` |
| **Content** | Single "coming soon" `TextView` | Full profile layout |

**Verdict:** `fragment_profile.xml` is a placeholder. The **entire** Profile UI must be built from scratch.

---

### 2.2 Missing Components

| # | Spec Component | Status |
|---|---|---|
| 1 | Top App Bar — centered "Profile" `TextView` + Settings `ImageView` | ❌ Missing |
| 2 | Identity — large circular avatar `ImageView` + green status dot | ❌ Missing |
| 3 | Identity — Name `TextView` (bold, large) | ❌ Missing |
| 4 | Identity — "👁️ View as Public Profile" `TextView` | ❌ Missing |
| 5 | Identity — Trust Score outlined orange `Chip` | ❌ Missing |
| 6 | Identity — Personality tag `ChipGroup` ("Chatty", "Dog Friendly") | ❌ Missing |
| 7 | Milestones `MaterialCardView` — 3-column stats row | ❌ Missing |
| 8 | Milestones — "Badges" section with 3 badge `ImageView`s + labels | ❌ Missing |
| 9 | Menu List rows (icon in warm circle + label + chevron) | ❌ Missing |

---

## 3. Cross-Cutting Gaps

### 3.1 Missing Color Entries in `colors.xml`

These are required by the V3.0 spec but absent from the current palette.

| Color Name (proposed) | Usage | Suggested Hex |
|---|---|---|
| `color_confirmed` | "Confirmed" chip text color | `#FF2E7D32` (Material Green 800) |
| `color_confirmed_bg` | "Confirmed" chip background | `#FFE8F5E9` (Material Green 50) |

> All other colors map correctly: Primary Orange → `@color/orange_primary`, Screen bg → `@color/bg_cream`, Card bg → `@color/bg_white`, etc.

### 3.2 Missing Drawable Resources

These drawables are referenced in the new layouts but do not exist yet.

| Drawable ID | Usage |
|---|---|
| `ic_bell` | Notification bell in Home top bar |
| `ic_chevron_right` | Streak card header, menu row arrows |
| `ic_settings` | Profile top bar settings icon |
| `ic_history` | "Walk History & Disputes" menu row icon |
| `ic_distance` | Quick Stats card distance icon |
| `ic_session` | Quick Stats card sessions icon |
| `ic_badge_first_walk` | Badge in Milestones card |
| `ic_badge_social` | Badge in Milestones card |
| `ic_badge_streak` | Badge in Milestones card |
| `ic_placeholder_avatar` | Default avatar for cards |
| `bg_green_dot` | Green online status dot (profile avatar) |
| `bg_orange_dot` | Orange notification badge dot |

> `bg_dot_orange.xml` already exists and **can be reused** as `bg_orange_dot` — just alias the reference.

### 3.3 Missing Item Layout

| File | Purpose |
|---|---|
| `item_quick_invite.xml` | Items for Quick Invite horizontal `RecyclerView` |

---

## 4. Summary Scoreboard

| Area | Status |
|---|---|
| Home Dashboard layout | ❌ Does not exist — must be created |
| Profile layout | ❌ Stub only — must be built from scratch |
| `fragment_explore.xml` hardcoded colors | ⚠️ 2 violations (non-blocking for V3.0 scope) |
| `colors.xml` green variants | ⚠️ 2 colors missing |
| Required new drawables | ⚠️ 12 assets missing |
| Required new item layouts | ⚠️ 1 file missing |
