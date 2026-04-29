# WalkMate Android Design Tokens
Reference for translating Figma / React (Tailwind) → Android XML.

---

## Colors (`res/values/colors.xml`)

| Figma / Tailwind / Hex     | Android token               | Usage                          |
|----------------------------|-----------------------------|--------------------------------|
| `#F97316` / `orange-500`   | `@color/orange_primary`     | Primary brand, CTAs, icons     |
| `#FB923C` / `orange-400`   | `@color/orange_end`         | Gradient end, secondary tint   |
| `#FEF9F5`                  | `@color/bg_cream`           | Screen background              |
| `#FFFFFF`                  | `@color/bg_white`           | Card / surface background      |
| `#FFFFFF`                  | `@color/white`              | Text on dark / icon on dark    |
| `#000000`                  | `@color/black`              | Rare absolute black            |
| `#1C1917` / `stone-900`    | `@color/text_dark`          | Primary text                   |
| `#A8A29E` / `stone-400`    | `@color/text_muted`         | Secondary / caption text       |
| `#78716C` / `stone-500`    | `@color/text_label`         | Form labels, sub-labels        |
| `#F5EDE4`                  | `@color/bg_tag_inactive`    | Inactive chip bg, divider fill |
| `#E53935`                  | `@color/color_danger`       | Error states                   |
| `#2E7D32`                  | `@color/color_confirmed`    | Success / confirmed badge text |
| `#E8F5E9`                  | `@color/color_confirmed_bg` | Success / confirmed badge bg   |
| `#40000000` (40% black)    | `@color/overlay_dim`        | Map overlay, modal scrim       |

---

## Corner Radius (`res/values/dimens.xml` + patterns)

| Figma / Tailwind      | Android value  | Used for                                  |
|-----------------------|----------------|-------------------------------------------|
| `rounded-full` / pill | `999dp`        | Chips, old-style pill buttons             |
| `rounded-3xl` / 24px  | `24dp`         | Bottom sheets (`radius_sheet`)            |
| `rounded-2xl` / 20px  | `20dp`         | Hero CTA card (`bg_hero_cta`)             |
| `rounded-xl` / 16px   | `16dp`         | Input fields, buttons, stat cards        |
| `rounded-lg` / 14px   | `14dp`         | Item cards, notification button           |
| `rounded-md` / 12px   | `12dp`         | Avatar initials, icon containers          |
| `rounded` / 6–8px     | `6dp`          | Small chips (`radius_small`)              |
| `rounded-none`        | `0dp`          | Full-bleed images                         |

---

## Spacing / Padding

| Tailwind / Figma  | dp       | Usage                                   |
|-------------------|----------|-----------------------------------------|
| `p-5` / 20px      | `20dp`   | Screen horizontal padding               |
| `p-4` / 16px      | `16dp`   | Card internal padding                   |
| `p-3` / 12px      | `12dp`   | Compact card padding                    |
| `gap-5` / 20px    | `20dp`   | Section vertical gap                    |
| `gap-4` / 16px    | `16dp`   | Between form fields                     |
| `gap-3` / 12px    | `12dp`   | Between items in a row                  |
| `gap-2` / 8px     | `8dp`    | Tight item spacing                      |
| `gap-1` / 4dp     | `4dp`    | Label → value micro-gap                 |

---

## Typography

| Figma role         | `textSize` | `textStyle` / `fontFamily`    | Color token          |
|--------------------|------------|-------------------------------|----------------------|
| Screen title       | `28sp`     | `sans-serif-black`            | `@color/text_dark`   |
| Section heading    | `20–22sp`  | `bold`                        | `@color/text_dark`   |
| Card title         | `15–16sp`  | `bold`                        | `@color/text_dark`   |
| Body               | `14sp`     | normal                        | `@color/text_dark`   |
| Secondary body     | `13–14sp`  | normal                        | `@color/text_muted`  |
| Caption / meta     | `11–12sp`  | normal                        | `@color/text_muted`  |
| Label (form)       | `13sp`     | normal                        | `@color/text_label`  |
| CTA badge (all-cap)| `10sp`     | `bold`, `letterSpacing=0.1`   | `#BFFFFFFF`          |
| Stat value (large) | `22sp`     | `bold`                        | `@color/text_dark`   |
| `letterSpacing`    | `-0.012` for titles, `0.1` for ALL-CAPS badges | | |

---

## Shape Drawables (`res/drawable/`)

| Drawable                      | Description                                           |
|-------------------------------|-------------------------------------------------------|
| `bg_gradient_button`          | Filled orange gradient, `16dp` radius — primary CTA   |
| `bg_button_outline_orange`    | Transparent fill, `1.5dp` orange stroke, `16dp` radius|
| `bg_input_field`              | White fill, `1.5dp #E7E5E4` stroke, `16dp` radius     |
| `bg_hero_cta`                 | Orange gradient `315°`, `20dp` radius — hero banner   |
| `bg_notification_btn`         | White fill, `1.5dp #F3F2F0` stroke, `14dp` radius     |
| `bg_icon_container_orange`    | `#FFF7ED` fill, `12dp` radius — icon wrapper chip     |
| `bg_cta_icon_btn`             | `#33FFFFFF` (20% white) fill, `14dp` radius           |
| `bg_logo_card`                | 3-stop orange gradient, `22dp` radius — auth logo     |
| `bg_dot_orange`               | Orange oval — notification badge dot                  |
| `bg_white_circle`             | White oval — decorative background circles            |
| `bg_chip_active`              | Orange fill chip                                      |
| `bg_chip_inactive`            | `#F5EDE4` fill chip                                   |

**Card pattern (used everywhere):**
```xml
app:cardCornerRadius="16dp"   <!-- or 18dp for stat cards -->
app:cardElevation="0dp"
app:strokeColor="#F3F2F0"
app:strokeWidth="1.5dp"
app:cardBackgroundColor="@color/bg_white"
```

---

## Custom Components (`core/designsystem/view/`)

### `WalkMateButton`
```xml
<com.walkmate.core.designsystem.view.WalkMateButton
    android:layout_width="match_parent"
    android:layout_height="54dp"
    app:wm_buttonStyle="filled"     <!-- or "outlined" -->
    app:wm_text="Label text" />
```

### `WalkMateInputField`
```xml
<com.walkmate.core.designsystem.view.WalkMateInputField
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:wm_label="Field Label"
    app:wm_hint="Placeholder…"
    app:wm_icon="@drawable/ic_mail"
    app:wm_inputType="textEmailAddress"
    app:wm_passwordToggle="true" />   <!-- only for password fields -->
```

### `GoogleSignInButton`
```xml
<com.walkmate.core.designsystem.view.GoogleSignInButton
    android:layout_width="match_parent"
    android:layout_height="52dp" />
```

### `AvatarInitialView`
```xml
<com.walkmate.core.designsystem.view.AvatarInitialView
    android:layout_width="48dp"
    android:layout_height="48dp"
    app:wm_avatarName="Nguyen Minh"
    app:wm_showOnlineStatus="true" />
```

---

## Icons (`res/drawable/ic_*.xml`)

| Icon                  | Usage                         |
|-----------------------|-------------------------------|
| `ic_bell`             | Notification button           |
| `ic_distance`         | Distance stat, hero CTA       |
| `ic_session`          | Sessions stat                 |
| `ic_badge_streak`     | Leaderboard entry, streak     |
| `ic_chevron_right`    | Row navigation arrow          |
| `ic_hotspot_pin`      | Location row pin              |
| `ic_back`             | Back navigation               |
| `ic_settings`         | Settings menu                 |
| `ic_history`          | Walk history                  |
| `ic_my_location`      | Map re-center                 |
| `ic_google`           | Google sign-in button         |
| `ic_mail`             | Email input field             |
| `ic_lock`             | Password input field          |
| `ic_user`             | Full name input field         |

---

## Navigation Actions (from `homeFragment`)

| Destination           | Action ID                                  |
|-----------------------|--------------------------------------------|
| Explore               | `R.id.action_home_to_explore`              |
| Notifications         | `R.id.action_home_to_notifications`        |
| Leaderboard           | `R.id.action_home_to_leaderboardFragment`  |
| Session History       | `R.id.action_home_to_sessionHistoryFragment` |
| Public Profile        | `R.id.action_home_to_publicProfileFragment`  |

---

## Bottom Nav Visibility Rule

**Decision:** Bottom nav is shown **only** for the three top-level tab destinations. It is hidden automatically for every other destination.

```java
// MainActivity.java — OnDestinationChangedListener
if (isTopLevelTab(destId)) {
    setBottomNavVisibility(true);
} else if (destId != R.id.exploreFragment) {   // explore self-manages
    setBottomNavVisibility(false);
}

private boolean isTopLevelTab(int destId) {
    return destId == R.id.homeFragment
        || destId == R.id.matchesFragment
        || destId == R.id.profileFragment;
}
```

New sub-page destinations are hidden automatically — no per-screen code needed.

---

## Sub-page Header Component (`layout_sub_page_header.xml`)

**Decision:** All sub-pages (reached by navigating away from a top-level tab) share one reusable header include.

```xml
<include layout="@layout/layout_sub_page_header" />
```

**Spec:**

| Property       | Value                                    |
|----------------|------------------------------------------|
| Height         | `56dp`                                   |
| Background     | `@color/bg_white`                        |
| Elevation      | `4dp` (shadow below header)              |
| Padding H      | `8dp` start / end                        |
| Back button ID | `@+id/btnSubPageBack`                    |
| Title ID       | `@+id/txtSubPageTitle`                   |

**Back button spec (`btnSubPageBack`):**
- Type: `ImageButton`
- Size: `40 × 40dp`
- Background: `@drawable/bg_btn_back` (`#F5F5F4` fill, `12dp` corners)
- Icon: `@drawable/ic_back`, padding `8dp`

**Title is always set programmatically in the host fragment:**
```java
((TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Walk History");
```

**Pages currently using this include:**
`fragment_session_history`, `fragment_friends`, `fragment_blocked_users`,
`fragment_leaderboard`, `fragment_submit_review`, `fragment_report_incident`

**Pages with custom headers (back button updated to `bg_btn_back` only):**
`fragment_public_profile` (has overflow menu button),
`fragment_notifications` (has unread count + mark-all-read action)

---

## Back Button Drawable (`bg_btn_back`)

**Decision:** Replace `?attr/selectableItemBackgroundBorderless` on sub-page back buttons with a visible rounded-square background matching the Figma spec.

```xml
<!-- res/drawable/bg_btn_back.xml -->
<shape android:shape="rectangle">
    <solid android:color="#F5F5F4" />
    <corners android:radius="12dp" />
</shape>
```

| Property     | Value       |
|--------------|-------------|
| Fill         | `#F5F5F4`   |
| Corner radius | `12dp`     |
| Button size  | `40 × 40dp` |
| Icon padding | `8dp`       |

---

## Session History Card (`item_session_history.xml`)

### Card container

```xml
app:cardCornerRadius="20dp"
app:cardElevation="4dp"
app:cardBackgroundColor="@color/bg_white"
app:strokeColor="#F3F2F0"
app:strokeWidth="1dp"
```

### Row order
Partner row is always rendered **first** (top), "You" row **second** (bottom).
The adapter uses `summary.getPartnerParticipant(currentUserId)` /
`summary.getCallerParticipant(currentUserId)` — never positional index.

### Status badge
Status text + pill background are applied **programmatically** via `GradientDrawable`
(no separate drawable file needed):

| Status      | Background  | Text color  |
|-------------|-------------|-------------|
| `ACTIVE`    | `#FFF7ED`   | `#F97316`   |
| `COMPLETED` | `#F0FDF4`   | `#16A34A`   |
| `CANCELLED` | `#FEF2F2`   | `#EF4444`   |
| Other       | `#F3F2F0`   | `#78716C`   |

```java
GradientDrawable bg = new GradientDrawable();
bg.setShape(GradientDrawable.RECTANGLE);
bg.setCornerRadius(100f);   // pill
bg.setColor(bgColor);
badge.setBackground(bg);
```

### Stat cell pattern (distance / duration)
Each stat is a horizontal `LinearLayout`: `14dp ImageView` + `TextView`.

```xml
<LinearLayout android:orientation="horizontal" android:gravity="center_vertical">
    <ImageView android:layout_width="14dp" android:layout_height="14dp"
               android:src="@drawable/ic_pin_small" android:layout_marginEnd="3dp" />
    <TextView android:id="@+id/txtParticipant1Distance"
              android:textSize="12sp" android:textStyle="bold"
              android:textColor="@color/text_label" />
</LinearLayout>
```

New icons: `ic_pin_small` (map pin, 14dp, `#A8A29E`), `ic_clock_small` (clock, 14dp, `#A8A29E`).

### Action row (Leave a Review / Report)
- Uses `MaterialButton` with `style="@style/Widget.Material3.Button.TextButton"`
- Width: `match_parent` (centered text); mutually exclusive — only one shown at a time
- "Leave a Review": `@color/orange_primary`; "Report": `@color/text_muted`
- A `View` divider (`id="dividerAction"`, `1dp`, `#F3F2F0`) is shown/hidden alongside the button

---

## "You" Avatar in History Card

**Decision:** The caller's avatar uses `AvatarInitialView` with an orange background override
(instead of the default cream `bg_warm_circle`) so it is visually distinct from the partner avatar.

```java
avatarSelf.setBackground(
    ContextCompat.getDrawable(context, R.drawable.bg_circle_orange));
avatarSelf.setInitialTextColor(Color.WHITE);   // new API added to AvatarInitialView
avatarSelf.bind(callerName, caller.getAvatarUrl());
```

New drawable: `bg_circle_orange` — solid orange (`@color/orange_primary`) oval.
New `AvatarInitialView` method: `setInitialTextColor(int color)` — overrides the default orange initial text for use on dark/colored backgrounds.

---

## New Drawables Added

| Drawable          | Description                                                   |
|-------------------|---------------------------------------------------------------|
| `bg_btn_back`     | `#F5F5F4` fill, `12dp` corners — sub-page back button bg     |
| `bg_circle_orange`| Orange oval — "You" avatar background in history card        |
| `ic_pin_small`    | 14dp map-pin vector, `#A8A29E` — distance stat icon          |
| `ic_clock_small`  | 14dp clock vector, `#A8A29E` — duration stat icon            |

---

## History API — `caller_avatar_url`

**Decision:** `SessionSummaryResponse` exposes `caller_avatar_url` as an explicit top-level field (in addition to the per-participant `avatar_url` already inside `participants[]`). This lets clients show the current user's photo in the "You" row without iterating the participants list.

```json
{
  "session_id": "...",
  "status": "COMPLETED",
  "participants": [...],
  "caller_avatar_url": "https://..."
}
```

The frontend DTO (`SessionSummaryResponse.java`) deserialises it via `@SerializedName("caller_avatar_url")`.
The adapter uses `caller.getAvatarUrl()` (from `getCallerParticipant()`) which is equivalent.
