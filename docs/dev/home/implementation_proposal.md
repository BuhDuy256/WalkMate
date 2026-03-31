# Implementation Proposal: V3.0 Home Dashboard & Profile UI Redesign

---

## Architectural Problem 1 — Domain Driven Design: UI State Layer for Home & Profile

### Decision: Introduce two new, fully self-contained UI feature packages — `ui/home/` and rebuilt `ui/profile/` — each owning its own immutable `UiState`, `ViewModel`, and `Fragment`. No business state is shared between them.

**Reasoning:**
The current Home tab (`fragment_explore.xml`) is a map-based exploration screen with a `BottomSheetBehavior` state machine (`WELCOME → SETUP → SCANNING`). The V3.0 Dashboard is a fundamentally different kind of screen: a scrollable feed of aggregated data snapshots (streak, upcoming session, stats). These two screens have different data sources, different lifecycles, and different rendering logic. Collapsing them into one Fragment or ViewModel would violate the single-responsibility rule and create a fragile god-object.

The Profile tab is currently a stub `FrameLayout` with a single placeholder `TextView`. It needs a full MVVM stack independently — it must NOT borrow state from `ExploreViewModel` or `MatchesViewModel`, even though it may display overlapping data (e.g., streak count). Each ViewModel fetches and owns its own slice of truth.

**Domain objects to create/confirm:**

```
ui/
├── home/                                    ← CREATE NEW
│   ├── HomeFragment.java                    ← Thin view; observes LiveData<HomeDashboardUiState>
│   ├── HomeViewModel.java                   ← Owns: streak, session snapshot, quick invite list, stats
│   ├── HomeViewModelFactory.java            ← Manual DI; receives WalkSessionRepository, UserRepository
│   ├── HomeDashboardUiState.java            ← Immutable; see contract below
│   └── quickinvite/
│       └── QuickInviteAdapter.java          ← RecyclerView adapter for horizontal invite list
│
└── profile/                                 ← REBUILD from stub
    ├── ProfileFragment.java                 ← Thin view; observes LiveData<ProfileUiState>
    ├── ProfileViewModel.java                ← Owns: user info, milestones, badges
    ├── ProfileViewModelFactory.java         ← Manual DI; receives UserRepository
    └── ProfileUiState.java                  ← Immutable; see contract below
```

**`HomeDashboardUiState.java` contract:**

```java
public class HomeDashboardUiState {
    private final boolean isLoading;
    private final String greetingName;            // "Alex" — displayed as "Hi, Alex! 👋"
    private final String locationName;            // "Ho Chi Minh City"
    private final boolean hasUnreadNotification;
    private final int streakDays;                 // 5
    private final int streakGoal;                 // 7
    private final int nearbyHotspotCount;         // 5 — inserted into hero subtitle format string
    private final UpcomingSessionSnapshot upcomingSession;  // null when no confirmed session
    private final List<QuickInviteUser> quickInviteList;
    private final double weeklyDistanceKm;
    private final int weeklySessionCount;
    private final String error;

    // Immutable snapshot — no link to WalkSession lifecycle
    public static class UpcomingSessionSnapshot {
        public final String partnerName;
        public final String partnerAvatarUrl;
        public final String sessionId;
        public final String timeAndPlace;     // "17:30 at Tao Dan Park"
        public final String statusLabel;      // maps to @string/home_session_status_confirmed
    }

    // Immutable snapshot for horizontal list item
    public static class QuickInviteUser {
        public final String userId;
        public final String displayName;
        public final String avatarUrl;
    }

    // Pure getters only. No setters. ViewModel calls postValue(new HomeDashboardUiState(...))
}
```

**`ProfileUiState.java` contract:**

```java
public class ProfileUiState {
    private final boolean isLoading;
    private final String name;
    private final String avatarUrl;
    private final boolean isOnline;
    private final float trustScore;             // 4.9f
    private final List<String> personalityTags; // ["Chatty", "Dog Friendly"]
    private final double totalDistanceKm;
    private final int totalSessions;
    private final int currentStreak;
    private final List<Badge> badges;
    private final String error;

    public static class Badge {
        public final String badgeId;
        public final int labelStringResId;   // R.string.profile_badge_first_walk
        public final int iconDrawableResId;  // R.drawable.ic_badge_first_walk
    }
}
```

---

## Architectural Problem 2 — `tools:` Namespace for All Dynamic View Data

### Decision: Every `TextView`, `ImageView`, or `ProgressBar` whose value is set at runtime by a `ViewModel` MUST use `tools:text`, `tools:src`, or `tools:progress` for its design-time value. The `android:text` / `android:src` attributes on those same views must be absent.

**Reasoning:**
Two concrete problems occur when dynamic views carry `android:text="..."` hardcoded values:
1. **Lint warning `HardcodedText`:** Android Studio flags every raw string in a layout as a localization violation. Using `tools:` silences this correctly because the attribute is stripped at build time and never ships in the APK.
2. **UI flicker on first render:** When a `Fragment` inflates its layout and then immediately calls `viewModel.load()`, there is a one-frame window where the hardcoded `android:text` value is visible before the `LiveData` observer fires. On slower devices this produces a visible flash of stale content. `tools:text` values never render at runtime, eliminating this entirely.

**Applied rule — the distinction:**

```xml
<!-- WRONG: android:text on a dynamic field ships in APK, causes flicker + Lint -->
<TextView
    android:id="@+id/txtGreeting"
    android:text="Hi, Alex! 👋"
    android:textColor="@color/text_dark" />

<!-- CORRECT: tools:text is design-time only, stripped at build -->
<TextView
    android:id="@+id/txtGreeting"
    tools:text="Hi, Alex! 👋"
    android:textColor="@color/text_dark" />
```

**The boundary:** Static text that is *always identical at runtime* (section labels, CTA button text) uses `android:text="@string/..."`. Data that varies per user or per state (greeting name, streak count, session partner, distance stat) uses `tools:text` for the preview and is set in the Fragment's `LiveData` observer.

**For `ImageView` avatars:**

```xml
<ImageView
    android:id="@+id/imgSessionAvatar"
    tools:src="@drawable/ic_user"
    android:scaleType="centerCrop"
    android:contentDescription="@string/home_avatar_cd" />
```

Glide sets the real avatar URL at runtime. `android:src` is intentionally absent to prevent a stale placeholder from appearing before Glide loads.

---

## Architectural Problem 3 — 100% Localization: All Static Text to `strings.xml`

### Decision: Zero hardcoded string literals in any layout XML file. Every `android:text`, `android:hint`, and `android:contentDescription` that is static must reference a `@string/` entry. `tools:text` previews may use inline strings as they are build-time only and never ship.

**Reasoning:**
The project has a Vietnamese-primary audience and potential for future i18n. More immediately, Android Lint's `HardcodedText` check flags every raw string in a layout. Violating it at scale across two new screens accumulates dozens of warnings that degrade CI signal quality and block strict builds.

**New entries to add to `res/values/strings.xml`:**

```xml
<!-- HOME DASHBOARD -->
<string name="home_notification_cd">Notifications</string>
<string name="home_streak_days_format">%1$d / %2$d days</string>
<string name="home_streak_details_cd">View streak details</string>
<string name="home_hero_subtitle_format">%1$d Hotspots are bustling nearby.</string>
<string name="home_find_walkmate_cta">Find a WalkMate Now ✨</string>
<string name="home_next_walk_label">Next Walk</string>
<string name="home_session_status_confirmed">Confirmed</string>
<string name="home_message_partner">Message</string>
<string name="home_quick_invite_header">Rủ rê nhanh</string>
<string name="home_see_all">See all</string>
<string name="home_stat_this_week">This Week</string>
<string name="home_stat_day_streak">Day Streak</string>
<string name="home_stat_distance_cd">Distance</string>
<string name="home_stat_sessions_cd">Sessions</string>
<string name="home_avatar_cd">Partner avatar</string>
<string name="home_add_badge_cd">Invite</string>

<!-- PROFILE -->
<string name="profile_screen_title">Profile</string>
<string name="profile_settings_cd">Settings</string>
<string name="profile_view_public">👁️ View as Public Profile</string>
<string name="profile_trust_score_format">⭐ %1$.1f / 5.0</string>
<string name="profile_tag_chatty">Chatty</string>
<string name="profile_tag_dog_friendly">Dog Friendly</string>
<string name="profile_stat_total_km">Total KM</string>
<string name="profile_stat_sessions">Sessions</string>
<string name="profile_stat_streak">Day Streak</string>
<string name="profile_stat_km_cd">Distance</string>
<string name="profile_stat_sessions_cd">Sessions</string>
<string name="profile_badges_section_title">Badges</string>
<string name="profile_badge_first_walk">First Walk</string>
<string name="profile_badge_social">Social Star</string>
<string name="profile_badge_streak">Streak King</string>
<string name="profile_menu_walk_history">Walk History &amp; Disputes</string>
<string name="profile_menu_my_badges">My Badges</string>
<string name="profile_menu_settings">Settings</string>
<string name="profile_menu_navigate_cd">Navigate</string>
<string name="profile_avatar_cd">Profile avatar</string>
```

**Usage rule for format strings:** Dynamic values are inserted at runtime via `getString(R.string.home_streak_days_format, 5, 7)` inside the Fragment's `LiveData` observer — never via data binding expressions (not in the tech stack).

---

## Architectural Problem 4 — View Tree Flattening: 3-Column Stats in Profile Milestones

### Decision: The 3-column stats row (`Total KM | Sessions | Day Streak`) in `fragment_profile.xml` MUST be implemented as a single flat `ConstraintLayout` using vertical per-column `Chains` and two `Guideline`s at 33.3% / 66.7%. Nested `LinearLayout`s are strictly forbidden for this section.

**Reasoning:**
The natural instinct is three horizontal `LinearLayout` children with `layout_weight="1"` each. This produces a **3-level nesting depth** and forces a *double measure* pass on every weighted child. A flat `ConstraintLayout` with `Guideline`s measures the same 9 views in a **single pass** with zero recursion.

| Approach | View count | Nesting depth | Measure passes |
|---|---|---|---|
| `LinearLayout` + weights (forbidden) | 9 views + 3 wrappers = 12 | 3 deep | Double-measure on each weighted child |
| `ConstraintLayout` + Chains + Guidelines | 9 views + 2 guidelines = 11 | **1 deep** | Single pass, no `layout_weight` |

**Flattened implementation for the Milestones stats section:**

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:id="@+id/statsRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingTop="4dp"
    android:paddingBottom="4dp">

    <androidx.constraintlayout.widget.Guideline
        android:id="@+id/guidelineCol1"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintGuide_percent="0.333" />

    <androidx.constraintlayout.widget.Guideline
        android:id="@+id/guidelineCol2"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintGuide_percent="0.667" />

    <!-- Column 1: Total KM — vertical packed chain -->
    <ImageView
        android:id="@+id/icStatKm"
        android:layout_width="24dp" android:layout_height="24dp"
        android:src="@drawable/ic_distance"
        android:contentDescription="@string/profile_stat_km_cd"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/guidelineCol1"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/txtStatKmValue"
        app:layout_constraintVertical_chainStyle="packed" />

    <TextView
        android:id="@+id/txtStatKmValue"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:textColor="@color/text_dark" android:textSize="20sp" android:textStyle="bold"
        tools:text="248"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/guidelineCol1"
        app:layout_constraintTop_toBottomOf="@id/icStatKm"
        app:layout_constraintBottom_toTopOf="@id/txtStatKmLabel" />

    <TextView
        android:id="@+id/txtStatKmLabel"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="2dp" android:gravity="center"
        android:text="@string/profile_stat_total_km"
        android:textColor="@color/text_muted" android:textSize="11sp"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/guidelineCol1"
        app:layout_constraintTop_toBottomOf="@id/txtStatKmValue"
        app:layout_constraintBottom_toBottomOf="parent" />

    <!-- Vertical divider 1 -->
    <View
        android:id="@+id/dividerStats1"
        android:layout_width="1dp" android:layout_height="0dp"
        android:background="@color/bg_tag_inactive"
        app:layout_constraintStart_toStartOf="@id/guidelineCol1"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

    <!-- Column 2: Sessions — vertical packed chain -->
    <ImageView
        android:id="@+id/icStatSessions"
        android:layout_width="24dp" android:layout_height="24dp"
        android:src="@drawable/ic_session"
        android:contentDescription="@string/profile_stat_sessions_cd"
        app:layout_constraintStart_toEndOf="@id/guidelineCol1"
        app:layout_constraintEnd_toStartOf="@id/guidelineCol2"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/txtStatSessionsValue"
        app:layout_constraintVertical_chainStyle="packed" />

    <TextView
        android:id="@+id/txtStatSessionsValue"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:textColor="@color/text_dark" android:textSize="20sp" android:textStyle="bold"
        tools:text="32"
        app:layout_constraintStart_toEndOf="@id/guidelineCol1"
        app:layout_constraintEnd_toStartOf="@id/guidelineCol2"
        app:layout_constraintTop_toBottomOf="@id/icStatSessions"
        app:layout_constraintBottom_toTopOf="@id/txtStatSessionsLabel" />

    <TextView
        android:id="@+id/txtStatSessionsLabel"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="2dp" android:gravity="center"
        android:text="@string/profile_stat_sessions"
        android:textColor="@color/text_muted" android:textSize="11sp"
        app:layout_constraintStart_toEndOf="@id/guidelineCol1"
        app:layout_constraintEnd_toStartOf="@id/guidelineCol2"
        app:layout_constraintTop_toBottomOf="@id/txtStatSessionsValue"
        app:layout_constraintBottom_toBottomOf="parent" />

    <!-- Vertical divider 2 -->
    <View
        android:id="@+id/dividerStats2"
        android:layout_width="1dp" android:layout_height="0dp"
        android:background="@color/bg_tag_inactive"
        app:layout_constraintStart_toStartOf="@id/guidelineCol2"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

    <!-- Column 3: Day Streak — vertical packed chain -->
    <TextView
        android:id="@+id/icStatStreakEmoji"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="🔥" android:textSize="22sp"
        app:layout_constraintStart_toEndOf="@id/guidelineCol2"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/txtStatStreakValue"
        app:layout_constraintVertical_chainStyle="packed" />

    <TextView
        android:id="@+id/txtStatStreakValue"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:textColor="@color/text_dark" android:textSize="20sp" android:textStyle="bold"
        tools:text="5"
        app:layout_constraintStart_toEndOf="@id/guidelineCol2"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toBottomOf="@id/icStatStreakEmoji"
        app:layout_constraintBottom_toTopOf="@id/txtStatStreakLabel" />

    <TextView
        android:id="@+id/txtStatStreakLabel"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:layout_marginTop="2dp" android:gravity="center"
        android:text="@string/profile_stat_streak"
        android:textColor="@color/text_muted" android:textSize="11sp"
        app:layout_constraintStart_toEndOf="@id/guidelineCol2"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toBottomOf="@id/txtStatStreakValue"
        app:layout_constraintBottom_toBottomOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## Step-by-Step Implementation Plan

### Phase A — Foundation Resources (Do First, Everything Depends On This)

**A1. Extend `res/values/colors.xml`**
- Add under `<!-- Semantic -->`:
  ```xml
  <color name="color_confirmed">#FF2E7D32</color>
  <color name="color_confirmed_bg">#FFE8F5E9</color>
  ```
- Add under `<!-- Misc -->`:
  ```xml
  <color name="color_orange_halo">#40FF9E67</color>
  ```
- The third entry resolves the 2 existing hardcoded-hex violations in `fragment_explore.xml` at lines 404 and 504. Do this patch now so the explore file is clean before any other work begins.

**A2. Extend `res/values/strings.xml`**
- Add all 36 new entries defined in Architectural Problem 3.
- Place them in two clearly commented blocks: `<!-- HOME DASHBOARD -->` and `<!-- PROFILE -->`.
- Do not alter any existing string keys.

**A3. Create 11 new drawable vector assets in `res/drawable/`**

| File | `fillColor` | Notes |
|---|---|---|
| `ic_bell.xml` | `@color/text_dark` | Notification bell |
| `ic_chevron_right.xml` | `@color/text_muted` | Right arrow |
| `ic_settings.xml` | `@color/text_dark` | Gear icon |
| `ic_history.xml` | `@color/text_dark` | Clock / list |
| `ic_distance.xml` | `@color/text_dark` | Ruler / footsteps |
| `ic_session.xml` | `@color/text_dark` | People / handshake |
| `ic_add_white.xml` | `@color/white` | Plus sign for invite badge |
| `ic_badge_first_walk.xml` | — | Trophy; full color asset |
| `ic_badge_social.xml` | — | Star; full color asset |
| `ic_badge_streak.xml` | — | Flame; full color asset |
| `bg_green_dot.xml` | `#FF4CAF50` | `<shape android:shape="oval">` |

- `bg_dot_orange.xml` already exists — reuse directly. No new file needed.

---

### Phase B — Screen 1: Home Dashboard

**B1. Create `res/layout/fragment_home.xml`**
- Root: `NestedScrollView`, `android:background="@color/bg_cream"`, `android:fillViewport="true"`, `android:overScrollMode="never"`.
- Single `LinearLayout` child, `orientation="vertical"`, `paddingBottom="88dp"` (clears the bottom nav bar height).
- Sections top-to-bottom:

  | Section | Root container | Static text uses `@string/` | Dynamic views use `tools:` |
  |---|---|---|---|
  | Top App Bar | `ConstraintLayout` | — | `txtGreeting`, `txtLocation`, notification badge `View` visibility |
  | Streak Widget | `MaterialCardView` (radius 16dp, elevation 2dp) | — | `txtStreakTitle`, `streakProgress`, `txtStreakDays` |
  | Hero Action | `MaterialCardView` (elevation 4dp) | `btnFindWalkMate` → `@string/home_find_walkmate_cta` | `txtHeroSubtitle` |
  | Upcoming Session | `MaterialCardView` (radius 16dp, elevation 2dp) | `@string/home_next_walk_label`, `@string/home_message_partner` | `chipSessionStatus`, `imgSessionAvatar`, `txtSessionTime`, `txtSessionPartner` |
  | Quick Invite | `LinearLayout` header + `RecyclerView` | `@string/home_quick_invite_header`, `@string/home_see_all` | populated by adapter |
  | Quick Stats | horizontal `LinearLayout` (3 equal `MaterialCardView`s) | `@string/home_stat_this_week`, `@string/home_stat_day_streak` | all value `TextView`s |

- The Upcoming Session body row (avatar + info + button) is a flat `ConstraintLayout` — **not** a nested horizontal `LinearLayout`.
- `btnFindWalkMate`: `android:background="@drawable/bg_gradient_orange_pill"`, `app:backgroundTint="@null"`, `app:cornerRadius="999dp"` — reuses the existing pill drawable, consistent with the explore CTA.
- `chipSessionStatus`: `app:chipBackgroundColor="@color/color_confirmed_bg"`, `android:textColor="@color/color_confirmed"`, `app:chipStrokeWidth="0dp"`.

**B2. Create `res/layout/item_quick_invite.xml`**
- Root: `LinearLayout`, `orientation="vertical"`, `android:gravity="center_horizontal"`, `android:layout_marginEnd="16dp"`.
- A 56×56dp `FrameLayout` containing:
  - `imgInviteAvatar`: `tools:src="@drawable/ic_user"`, `android:background="@drawable/bg_white_circle"`, `scaleType="centerCrop"`.
  - `imgAddBadge`: 20×20dp, `layout_gravity="bottom|end"`, `android:background="@drawable/bg_dot_orange"`, `android:src="@drawable/ic_add_white"`, `android:padding="4dp"`, `android:contentDescription="@string/home_add_badge_cd"`.
- `txtInviteName`: width 56dp, `tools:text="Sarah"`, `android:maxLines="1"`, `android:ellipsize="end"`, `android:gravity="center"`.

**B3. Create `ui/home/HomeDashboardUiState.java`**
- Implement the immutable contract from Architectural Problem 1 exactly.
- Provide a static factory: `public static HomeDashboardUiState loading() { return new HomeDashboardUiState(true, null, ...); }`.
- No setters. Constructor takes all fields.

**B4. Create `ui/home/HomeViewModel.java`**

```java
public class HomeViewModel extends ViewModel {
    private final MutableLiveData<HomeDashboardUiState> uiState = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final WalkSessionRepository sessionRepo;
    private final UserRepository userRepo;

    public HomeViewModel(WalkSessionRepository sessionRepo, UserRepository userRepo) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
    }

    public LiveData<HomeDashboardUiState> getUiState() { return uiState; }

    public void loadDashboard() {
        uiState.postValue(HomeDashboardUiState.loading());
        executor.execute(() -> {
            // 1. Fetch user profile (name, location, streak)
            // 2. Fetch nearest confirmed WalkSession → map to UpcomingSessionSnapshot
            // 3. Fetch quick invite candidate list
            // 4. Fetch weekly stats (km, sessions)
            // 5. postValue(new HomeDashboardUiState(false, name, location, ...))
        });
    }

    public void onFindWalkMateClicked() {
        // Navigation signal back to Fragment via a separate SingleLiveEvent<Void>
        // No Activity/Fragment reference held here
    }

    @Override protected void onCleared() { executor.shutdown(); }
}
```

- `uiState.postValue(...)` is always called from the `ExecutorService` background thread.
- The ViewModel holds zero `Context` references.

**B5. Create `ui/home/HomeViewModelFactory.java`**
- Implements `ViewModelProvider.Factory`.
- Constructor receives `WalkSessionRepository` and `UserRepository`.
- Instantiated in `HomeFragment.onViewCreated()` using singletons from `WalkMateApplication`.

**B6. Create `ui/home/HomeFragment.java`**
- Inflates `fragment_home.xml` in `onCreateView`.
- In `onViewCreated`:
  1. Instantiate `HomeViewModelFactory` via `WalkMateApplication`.
  2. Obtain `HomeViewModel` via `new ViewModelProvider(this, factory).get(HomeViewModel.class)`.
  3. Call `viewModel.loadDashboard()`.
  4. Observe `viewModel.getUiState()` → `renderState(HomeDashboardUiState state)`.
- `renderState()` is the **only** place that writes to Views:
  - Hides/shows loading indicator on `state.isLoading()`.
  - `txtStreakDays.setText(getString(R.string.home_streak_days_format, state.getStreakDays(), state.getStreakGoal()))`.
  - `txtHeroSubtitle.setText(getString(R.string.home_hero_subtitle_format, state.getNearbyHotspotCount()))`.
  - Loads session avatar via Glide if `state.getUpcomingSession() != null`; hides `upcomingSessionCard` otherwise.
  - Calls `quickInviteAdapter.submitList(state.getQuickInviteList())`.
- `btnFindWalkMate.setOnClickListener(v -> viewModel.onFindWalkMateClicked())` — no business logic in the lambda.
- `rvQuickInvite` configured with `LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)`.

**B7. Create `ui/home/quickinvite/QuickInviteAdapter.java`**
- Extends `RecyclerView.Adapter<QuickInviteAdapter.ViewHolder>`.
- `ViewHolder` binds `imgInviteAvatar` and `txtInviteName`.
- `onBindViewHolder`: loads avatar via Glide; sets name from `user.displayName`.
- Exposes `void submitList(List<QuickInviteUser> list)` — Fragment calls this inside `renderState()`.

---

### Phase C — Screen 2: Profile

**C1. Rebuild `res/layout/fragment_profile.xml`**
- Discard the current `FrameLayout` stub entirely. Replace with:
- Root: `NestedScrollView`, `android:background="@color/bg_cream"`, `android:fillViewport="true"`.
- Single `LinearLayout` child, `orientation="vertical"`, `paddingBottom="88dp"`.
- Sections in order:

  | Section | Root container | Notes |
  |---|---|---|
  | Top App Bar | `ConstraintLayout` | `txtProfileScreenTitle` centered; `btnSettings` end-constrained |
  | Identity | `LinearLayout` (centered, vertical) | Avatar `FrameLayout` 88dp + green dot; name; public profile link; trust chip; tag `ChipGroup` |
  | Milestones | `MaterialCardView` | Flat stats `ConstraintLayout` (Problem 4) + divider + badges `LinearLayout` (3 equal weighted cols — static count, acceptable) |
  | Menu List | `MaterialCardView` | 3 rows; each a `LinearLayout` with `bg_warm_circle` icon + label + chevron |

- `chipTrustScore`: `app:chipBackgroundColor="@color/bg_white"`, `app:chipStrokeColor="@color/orange_primary"`, `app:chipStrokeWidth="1.5dp"`, `android:textColor="@color/orange_primary"`, `tools:text="⭐ 4.9 / 5.0"`.
- Tag chips: `app:chipBackgroundColor="@color/bg_warm_light"`, `app:chipStrokeWidth="0dp"`, `android:textColor="@color/orange_primary"`. Text set at runtime from `state.getPersonalityTags()`.
- All menu row `LinearLayout`s: `android:clickable="true"`, `android:focusable="true"`, `android:foreground="?attr/selectableItemBackground"`.
- All dynamic views (`imgProfileAvatar`, `txtProfileName`, `chipTrustScore`, stat value `TextView`s, badge `ImageView`s) use `tools:src` / `tools:text`.

**C2. Create `ui/profile/ProfileUiState.java`**
- Implement the immutable contract from Architectural Problem 1.
- `Badge` inner class carries `labelStringResId` and `iconDrawableResId` so the Fragment uses Android resource IDs directly — no string matching at runtime.
- Static factory: `ProfileUiState.loading()`.

**C3. Create `ui/profile/ProfileViewModel.java`**

```java
public class ProfileViewModel extends ViewModel {
    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final UserRepository userRepo;

    public ProfileViewModel(UserRepository userRepo) { this.userRepo = userRepo; }

    public LiveData<ProfileUiState> getUiState() { return uiState; }

    public void loadProfile() {
        uiState.postValue(ProfileUiState.loading());
        executor.execute(() -> userRepo.getMyProfile(new DomainCallback<User>() {
            @Override public void onSuccess(User user) {
                uiState.postValue(buildStateFrom(user));
            }
            @Override public void onError(String errorCode) {
                uiState.postValue(new ProfileUiState(false, null, null, false,
                        0f, null, 0, 0, 0, null, errorCode));
            }
        }));
    }

    private ProfileUiState buildStateFrom(User user) {
        List<ProfileUiState.Badge> badges = new ArrayList<>();
        // Map user.badges → ProfileUiState.Badge using R.drawable / R.string IDs
        return new ProfileUiState(false, user.getName(), user.getAvatarUrl(),
                user.isOnline(), user.getTrustScore(), user.getPersonalityTags(),
                user.getTotalDistanceKm(), user.getTotalSessions(),
                user.getCurrentStreak(), badges, null);
    }

    @Override protected void onCleared() { executor.shutdown(); }
}
```

**C4. Create `ui/profile/ProfileViewModelFactory.java`**
- Receives `UserRepository` from `WalkMateApplication`. Standard factory pattern identical to existing factories in the project.

**C5. Create `ui/profile/ProfileFragment.java`**
- Inflates `fragment_profile.xml` in `onCreateView`.
- In `onViewCreated`:
  1. Obtain `ProfileViewModel` via factory + `WalkMateApplication`.
  2. Call `viewModel.loadProfile()`.
  3. Observe `viewModel.getUiState()` → `renderState(ProfileUiState state)`.
- `renderState()`:
  - Loads avatar via Glide into `imgProfileAvatar`.
  - `chipTrustScore.setText(getString(R.string.profile_trust_score_format, state.getTrustScore()))`.
  - Iterates `state.getPersonalityTags()` and sets chip text dynamically; hides unused chips.
  - Iterates `state.getBadges()` → `imgBadge1.setImageResource(badge.iconDrawableResId)`, `lblBadge1.setText(badge.labelStringResId)`.
  - `statsRow` values set directly: `txtStatKmValue.setText(String.valueOf((int) state.getTotalDistanceKm()))` etc.
- Menu row click listeners delegate to ViewModel: `menuWalkHistory.setOnClickListener(v -> viewModel.onWalkHistoryClicked())`.

---

### Phase D — Navigation Wiring

**D1. Update `MainActivity.java` — re-point Home tab**

```java
// BEFORE: Home tab opened ExploreFragment
if (id == R.id.tab_home) showTab(ExploreFragment.TAG);

// AFTER: Home tab now shows the Dashboard
if (id == R.id.tab_home) showTab(HomeFragment.TAG);
```

- `ExploreFragment` remains intact and fully functional. If the product spec requires access to the map, `btnFindWalkMate` in the Dashboard triggers `switchToExplore()` (see D3).
- Add `public static final String TAG = "tag_home";` to `HomeFragment`.

**D2. Register `UserRepository` singleton in `WalkMateApplication`**
- `HomeViewModelFactory` requires `WalkSessionRepository` and `UserRepository`.
- `ProfileViewModelFactory` requires `UserRepository`.
- Confirm `WalkSessionRepository` singleton already exists. If `UserRepository` is not yet a singleton in `WalkMateApplication`, add it:

  ```java
  // WalkMateApplication.java
  private UserRepository userRepository;

  public UserRepository getUserRepository() {
      if (userRepository == null) {
          userRepository = new UserRepositoryImpl(getRemoteUserApi(), getDatabase().userDao());
      }
      return userRepository;
  }
  ```

**D3. `btnFindWalkMate` → Explore navigation**
- `HomeFragment` defines a listener interface:

  ```java
  public interface OnHomeActionListener {
      void switchToExplore();
  }
  ```

- `MainActivity` implements `OnHomeActionListener` and calls `bottomNav.setSelectedItemId(R.id.tab_explore)`.
- In `HomeFragment.onAttach(Context context)`: `listener = (OnHomeActionListener) context;`.
- In `viewModel.onFindWalkMateClicked()` observer: `listener.switchToExplore()`.
- This keeps `HomeFragment` decoupled from `MainActivity` internals; no direct `getActivity()` cast.

---

### Phase E — Cleanup

**E1. Patch `fragment_explore.xml` hardcoded hex violations**
- Line 404: `app:haloColor="#40FF9E67"` → `app:haloColor="@color/color_orange_halo"`
- Line 504: `app:haloColor="#40FF9E67"` → `app:haloColor="@color/color_orange_halo"`
- `color_orange_halo` was added to `colors.xml` in Phase A1. This resolves both violations identified in the gap analysis.

**E2. Remove working draft documents**
- Delete `docs/reports/gap_analysis.md` — superseded by `docs/dev/home/gap_analysis.md`.
- Delete `docs/reports/implementation_proposal.md` — superseded by this document.

---

## Final Package Structure (Target State)

```
frontend/src/main/
│
├── java/com/walkmate/
│   │
│   ├── ui/
│   │   ├── main/
│   │   │   └── MainActivity.java              ← MODIFY: re-wire Home tab + implement OnHomeActionListener
│   │   │
│   │   ├── home/                              ← CREATE NEW (entire package)
│   │   │   ├── HomeFragment.java
│   │   │   ├── HomeViewModel.java
│   │   │   ├── HomeViewModelFactory.java
│   │   │   ├── HomeDashboardUiState.java
│   │   │   └── quickinvite/
│   │   │       └── QuickInviteAdapter.java
│   │   │
│   │   ├── profile/                           ← REBUILD (stub → full MVVM)
│   │   │   ├── ProfileFragment.java
│   │   │   ├── ProfileViewModel.java
│   │   │   ├── ProfileViewModelFactory.java
│   │   │   └── ProfileUiState.java
│   │   │
│   │   ├── explore/                           ← NO STRUCTURAL CHANGE (Phase E patch only)
│   │   │   └── (all existing files unchanged)
│   │   │
│   │   └── matches/
│   │       └── (all existing files unchanged)
│   │
│   └── domain/
│       └── (all existing domains unchanged)
│
└── res/
    │
    ├── layout/
    │   ├── fragment_home.xml                  ← CREATE NEW
    │   ├── fragment_profile.xml               ← REBUILD (stub → full layout)
    │   ├── item_quick_invite.xml              ← CREATE NEW
    │   └── fragment_explore.xml               ← PATCH (2 halo color refs only)
    │
    ├── values/
    │   ├── colors.xml                         ← ADD 3 entries (2 green + 1 orange halo)
    │   └── strings.xml                        ← ADD 36 entries across 2 comment blocks
    │
    └── drawable/
        ├── ic_bell.xml                        ← NEW
        ├── ic_chevron_right.xml               ← NEW
        ├── ic_settings.xml                    ← NEW
        ├── ic_history.xml                     ← NEW
        ├── ic_distance.xml                    ← NEW
        ├── ic_session.xml                     ← NEW
        ├── ic_add_white.xml                   ← NEW
        ├── ic_badge_first_walk.xml            ← NEW
        ├── ic_badge_social.xml                ← NEW
        ├── ic_badge_streak.xml                ← NEW
        ├── bg_green_dot.xml                   ← NEW
        └── bg_dot_orange.xml                  ← KEEP / reuse as-is (no change)
```

---

## Hard Constraints Checklist (per Architecture Standard)

| Constraint | This Plan |
|---|---|
| No RxJava / Coroutines | All async in `HomeViewModel` and `ProfileViewModel` runs inside `Executors.newSingleThreadExecutor()`; results surface via `LiveData.postValue()` on background thread |
| No Hilt / Dagger | `HomeViewModelFactory` and `ProfileViewModelFactory` use manual DI; all Repository instances retrieved from `WalkMateApplication` singletons |
| No MVI (`UiEvent` / `UiEffect`) | Pure `LiveData<UiState>` + direct ViewModel method calls from click listeners; no Action/Effect wrapper classes introduced |
| Fragment cannot include Activity layout | `fragment_home.xml` and `fragment_profile.xml` are standalone files; neither uses `<include>` of any activity layout |
| DTO cannot leak to `ui/` | `HomeViewModel` and `ProfileViewModel` receive only mapped domain objects (`User`, `WalkSession`) from Repositories; no raw `ApiResponse<T>` or DTO class referenced above the `data/` boundary |
| View cannot touch API / DB | `HomeFragment` and `ProfileFragment` contain zero imports of Retrofit, Room, or any Repository; all data flows through ViewModel → LiveData → observer |
| `tools:` namespace for dynamic data | Every runtime-variable `TextView` and `ImageView` in both new layouts uses `tools:text` / `tools:src`; no dynamic value ships via `android:text` in the APK |
| 100% Localization | All 36 new static string literals are declared in `strings.xml`; zero raw string literals appear in `android:text` attributes in `fragment_home.xml` or `fragment_profile.xml` |
| View Tree Flattening (3-column stats) | Profile Milestones stats row is a single flat `ConstraintLayout` with 2 `Guideline`s and 3 vertical `packed` chains; zero nested `LinearLayout`s in that section |
