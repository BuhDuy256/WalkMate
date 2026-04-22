# Matches UI Consistency Proposal
**Date:** 2026-04-22  
**Scope:** `ui/matches/finding`, `ui/matches/proposal`, `ui/matches/session`  
**Role:** Senior Android UI/UX Analysis + Architecture Design

---

## 1. Executive Summary

The three sub-fragment cards (`item_finding_card`, `item_proposal_card`, `item_session_card`) each evolved independently. They share the same lifecycle narrative — *one walk, three stages* — but present structurally alien UIs to the user. The header zone, time display, status communication, and action hierarchy all use different patterns per card.

**The goal:** Introduce a unified 5-zone card anatomy ("Match Card Box") shared across all three sub-fragments. The shared zone is enforced by a new Custom View (`MatchCardHeaderView`) for Zone 1, and a consistent layout DNA (section order, label typography, action hierarchy) for Zones 2–5.

No new features. No additional data fetching. The fix is structural — same data, radically more coherent presentation.

---

## 2. Current State Audit

### 2.1 Visual Anatomy Comparison

| Zone | Finding Card | Proposal Card | Session Card |
|---|---|---|---|
| **Top identifier** | Raw UUID (bold text, left) | Avatar + Name row | 130dp map → Avatar + raw UUID |
| **Status signal** | `chipStatus` chip (top-right) | Implicit (button visibility) | None |
| **Countdown** | Below time text (left-aligned) | Top-right of avatar row | Absent |
| **Time display** | Plain muted text `10:00–15:00` | Labeled section + warm pill | `🕐 03:00` emoji prefix |
| **Tags** | `TagChipGroup`, no label | "Common interests" label + group | Absent |
| **Primary CTA** | Full-width orange pill | `Pass` + `Accept ✦` (2:1 weight) | `I'm Here!` full-width |
| **Destructive action** | Outlined orange button | Red text button | Outlined red button |
| **Divider** | None | 1dp line after avatar row | 1dp line after avatar row |

### 2.2 Screenshots Cross-Analysis

**Finding tab (MATCHING state):**
- The hotspot identifier is a raw UUID (`550e8400-e29b-41d4-a71...`). User cannot tell which hotspot this is.
- The lock icon + "MATCHING" chip are in the same row but visually compete; lock icon is 18dp, the chip is 28dp — the row is unbalanced.
- Countdown (`319:22`) sits directly below the time text with no label, making it ambiguous — does it count down to expiry or to the walk time?
- No consistent visual section break between metadata and actions.

**Proposal tab (PENDING — needs decision):**
- Partner's header section is the strongest design of the three: avatar + name + countdown all in one coherent row. This is the reference to harmonize towards.
- "Common time window" and "Common interests" are labeled sub-sections — good pattern, but absent in the other cards.
- The `Pass | Accept` split-button is clear. However, the "Cancel Proposal" red text below it is a destructive action treated as an afterthought — no consistent destructive action slot exists across cards.

**Proposal tab (PENDING — waiting for partner):**
- The `txtWaitingOverlay` ("Waiting for partner to accept...") replaces the Pass/Accept row. There is no status badge to anchor what state the user is in. Without the countdown, the user has no sense of urgency.

**Session tab (PENDING — activate):**
- The 130dp map placeholder is the most disruptive divergence. It is a 130dp gray rectangle at the top of the card, inconsistent with all other cards, and currently non-functional ("Coming Soon"). It takes visual priority over the partner's identity.
- The avatar shows a raw digit (`8`) from the session UUID's numeric prefix — not the partner's initials. The name beside it is the truncated session UUID, not the partner's name (`txtPartnerName` is being bound with `session.getPartnerName()` in code, but the screenshot shows a UUID — suggests the `partnerName` is null at the time the test data was captured; the UUID is the `sessionId` bleeding through `AvatarInitialView`'s numeric-fallback rendering path).
- Meeting coordinates shown as raw `10,7770°N, 106,6953°E` — hotspot name would be more meaningful.
- No status badge, no countdown, no tags. The session card gives the user the fewest orientation cues of all three.

### 2.3 Root Cause

The cards were built independently at different points in the development cycle. Each author optimized locally without a shared structural contract. The result is:

1. **No shared header zone contract** — status and countdown are placed wherever was convenient at the time.
2. **Session card was designed around the map widget** — the map became the structural anchor, forcing everything else below it into a cramped secondary block.
3. **Action hierarchy has no consistent tier model** — what counts as "primary", "secondary", and "destructive" is implicit and varies per card.

---

## 3. Proposed Shared "Match Card Box" Anatomy

Every card across all three sub-fragments must follow this 5-zone box:

```
┌─────────────────────────────────────────────────────┐
│  Zone 1 — HEADER BAR          (MatchCardHeaderView)  │
│  [Status Badge]                    [Countdown Timer] │
├─────────────────────────────────────────────────────┤  ← divider
│  Zone 2 — IDENTITY BLOCK                            │
│  Who is this card about? (context-specific)         │
├─────────────────────────────────────────────────────┤  ← divider
│  Zone 3 — WHEN & WHERE BLOCK                        │
│  Shared label typography. Content differs per card. │
├─────────────────────────────────────────────────────┤
│  Zone 4 — TAGS (optional, same ChipGroup style)     │
├─────────────────────────────────────────────────────┤
│  Zone 5 — ACTION FOOTER                             │
│  [Secondary Outlined]   [Primary Orange Pill]       │
│         [Destructive Text Button — bottom]          │
└─────────────────────────────────────────────────────┘
```

### Zone 1 mapping per card

| Status (domain) | `wm_statusStyle` | Label text | Badge color |
|---|---|---|---|
| Intent `OPEN` | `open` | "Searching…" | `bg_warm_light` / `orange_end` text |
| Intent `MATCHING` | `matching` | "Matched 🔒" | `bg_warm_light` / `orange_end` text |
| Proposal `PENDING` (my turn) | `proposal_pending` | "Decide Now" | `bg_info_light` / `color_info` text |
| Proposal `PENDING` (waiting) | `proposal_waiting` | "Waiting…" | `bg_tag_inactive` / `text_label` text |
| Session `PENDING` | `session_pending` | "Ready to Walk" | `bg_warm_light` / `orange_end` text |
| Session `ACTIVE` | `session_active` | "Walk Active" | `bg_success_light` / `color_success` text |

**Countdown visibility rule (Zone 1):**
- Finding card: countdown is shown (expiry countdown). Label: `Expires in`
- Proposal card: countdown is shown (5-min TTL). No label (urgency is self-evident).
- Session card: **no countdown** — `CountdownTimerView` is hidden. Session pending-TTL is 24h (S-3), which is irrelevant to show on the card.

### Zone 2 mapping per card

**Finding:**
```
📍 [Hotspot Name]                    (txtHotspotName — bind hotspot name, not UUID)
   [Duration chip]  [Age range chip]
```
*The UUID must be replaced by the readable hotspot name from the domain model. 
The lock icon is absorbed into the status badge label ("Matched 🔒") in Zone 1.*

**Proposal:**
```
[AvatarInitialView 52dp]  [Partner Name · Age]
                          [⭐ Trust score]
```
*(This is already the strongest header; keep it as-is in Zone 2.)*

**Session:**
```
[AvatarInitialView 48dp]  [Partner Name]
                          [📍 Hotspot Name]
```
*Remove the 130dp map placeholder entirely from Zone 2.
Replace coordinates with the hotspot name (or abbreviated address). 
The map placeholder is non-functional and breaks the shared anatomy.
A small "View on Map →" text link can appear in Zone 3 (When & Where) as a 
secondary affordance once map integration is implemented.*

### Zone 3 mapping per card

All use the same label typography: `textSize="12sp"`, `textStyle="bold"`, `textColor="@color/text_label"`, `letterSpacing="0.04"`.

**Finding:**
```
TIME WINDOW
[10:00 – 15:00]    ← warm-bg pill (matching Proposal style, currently just plain text)
```
*Elevate the time display to match Proposal's warm-pill style. This is the
main visual alignment win for Finding ↔ Proposal.*

**Proposal:**
```
COMMON TIME WINDOW
[10:00 – 15:00]    ← already using warm-bg pill ✓
```

**Session:**
```
MEETING
[🕐 17:30]  [📍 Hotspot Name]    ← two inline info chips, same warm-bg pill style
```
*Replace the raw `🕐 03:00` with a labeled section. Show meeting time and 
hotspot name (not coordinates) as sibling info pills.*

### Zone 4 mapping per card

**Finding:** `TagChipGroup` (no label needed; tags describe the user's own intent). Keep as-is, just ensure consistent `marginTop="8dp"`.

**Proposal:** `TagChipGroup` under "COMMON INTERESTS" label. Keep as-is ✓.

**Session:** Tags are not applicable (session is a confirmed agreement). Zone 4 is **omitted** for session cards.

### Zone 5 mapping per card

**Action hierarchy tier model (universal):**
- **Tier 1 — Primary CTA:** Full-width orange pill (`bg_gradient_orange_pill`), 52dp height.
- **Tier 2 — Secondary action:** Outlined pill (`Widget.Material3.Button.OutlinedButton`), 48dp, `text_muted` text + `handle_bar` stroke.
- **Tier 3 — Destructive:** Text button (`Widget.Material3.Button.TextButton`), `color_danger`, always at the very bottom of the card.

**Finding (OPEN state):**
```
Tier 2: [ Cancel Intent (outlined, orange stroke) ]    ← currently this is Tier 2 visual but
                                                         uses orange stroke — change to handle_bar
                                                         to match standard secondary
```
*There is no Tier 1 CTA when OPEN — the user is just waiting. 
Consider adding a "Retry Match" outlined button if the feature is exposed. 
No Tier 3 in OPEN state — cancelling intent is a moderate, not destructive, action; 
keep as outlined secondary.*

**Finding (MATCHING state):**
```
Tier 1: [ View Proposal → ] (full-width orange pill)
[Cancel / Destructive] → hidden (cannot cancel while MATCHING per I-6 semantics)
```

**Proposal (PENDING — my turn):**
```
Tier 2: [ Pass ]    Tier 1: [ Accept ✦ ]    (1:2 weight split, side by side)
Tier 3: Cancel Proposal (red text button, full-width, bottom)
```

**Proposal (PENDING — waiting):**
```
                   "Waiting for partner to accept…"    (centered muted text)
Tier 3: Cancel Proposal (red text button, full-width, bottom)
```

**Session (PENDING):**
```
Tier 2: [ Chat ]    Tier 2: [ Cancel Session ]    (side by side, both outlined)
Tier 1: [ I'm Here! ] (ActivationWindowButtonView, full-width)
[No Tier 3 — Cancel Session in Tier 2 already covers destructive]
```
*`Cancel Session` button style change: currently `color_danger` stroke + text.
Move it to standard secondary (muted outlined) to reduce visual alarm before arrival.
Reserve danger color for Tier 3 only. The cancel dialog provides the final warning.*

**Session (ACTIVE):**
```
Tier 2: [ Chat ]    Tier 1: [ Resume Walk → ] (full-width orange pill)
Tier 3: Report an Issue (right-aligned muted text → move to Tier 3 danger text, full-width bottom)
```

---

## 4. `MatchCardHeaderView` — New Custom View Spec

### 4.1 Rationale

The status badge + countdown row appears in all 3 cards (≥ 3 occurrences), contains internal state (countdown running/stopped, badge color toggle), and requires a public API for binding. This satisfies all three conditions for a mandatory Custom View per section 8.2 of `Frontend_VI.md`.

### 4.2 File Locations

```
core/designsystem/view/
└── MatchCardHeaderView.java          ← extends ConstraintLayout

res/layout/
└── view_match_card_header.xml        ← <merge> root

res/values/
└── attrs.xml                         ← add declare-styleable MatchCardHeaderView
```

### 4.3 XML Attributes (`attrs.xml` addition)

```xml
<declare-styleable name="MatchCardHeaderView">
    <attr name="wm_statusLabel"  format="string" />
    <attr name="wm_statusStyle"  format="enum">
        <enum name="open"             value="0" />
        <enum name="matching"         value="1" />
        <enum name="proposal_pending" value="2" />
        <enum name="proposal_waiting" value="3" />
        <enum name="session_pending"  value="4" />
        <enum name="session_active"   value="5" />
    </attr>
    <attr name="wm_showCountdown" format="boolean" />
</declare-styleable>
```

### 4.4 Layout (`view_match_card_header.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android"
       xmlns:app="http://schemas.android.com/apk/res-auto"
       xmlns:tools="http://schemas.android.com/tools">

    <!-- Status badge — left-anchored -->
    <com.google.android.material.chip.Chip
        android:id="@+id/chipHeaderStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:clickable="false"
        android:focusable="false"
        android:textSize="11sp"
        android:textStyle="bold"
        app:chipCornerRadius="999dp"
        app:chipEndPadding="10dp"
        app:chipIconVisible="false"
        app:chipMinHeight="28dp"
        app:chipStartPadding="10dp"
        app:chipStrokeWidth="0dp"
        app:checkedIconVisible="false"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        tools:text="Searching…" />

    <!-- Countdown — right-anchored, GONE when wm_showCountdown=false -->
    <com.walkmate.core.designsystem.view.CountdownTimerView
        android:id="@+id/headerCountdown"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:wm_normalColor="@color/text_muted"
        app:wm_urgentColor="@color/color_danger"
        app:wm_urgentThresholdSec="60"
        tools:text="04:35"
        tools:visibility="visible" />

</merge>
```

### 4.5 Public Java API

```java
public class MatchCardHeaderView extends ConstraintLayout {

    // Matches wm_statusStyle enum values
    public static final int STYLE_OPEN             = 0;
    public static final int STYLE_MATCHING         = 1;
    public static final int STYLE_PROPOSAL_PENDING = 2;
    public static final int STYLE_PROPOSAL_WAITING = 3;
    public static final int STYLE_SESSION_PENDING  = 4;
    public static final int STYLE_SESSION_ACTIVE   = 5;

    // Bind status badge: label + visual style
    public void setStatus(String label, int statusStyle);

    // Show and start countdown (call with ISO-8601 expiresAt)
    public void startCountdown(String expiresAt);

    // Hide countdown (call when not applicable, e.g. Session card)
    public void hideCountdown();

    // Wire expired callback (delegates to internal CountdownTimerView)
    public void setOnExpiredListener(CountdownTimerView.OnExpiredListener listener);

    // Release internal timer (call from onViewRecycled / onDestroyView)
    public void cancelCountdown();
}
```

**Adapter/Fragment usage (Finding, MATCHING state):**
```java
cardHeader.setStatus("Matched 🔒", MatchCardHeaderView.STYLE_MATCHING);
cardHeader.startCountdown(intent.getExpiresAt());
cardHeader.setOnExpiredListener(() -> { if (listener != null) listener.onIntentExpired(); });
```

**Adapter/Fragment usage (Session, PENDING state):**
```java
cardHeader.setStatus("Ready to Walk", MatchCardHeaderView.STYLE_SESSION_PENDING);
cardHeader.hideCountdown();
```

### 4.6 Internal `setStatus()` color routing

```java
private void applyStatusStyle(int style) {
    int bgColor, textColor;
    switch (style) {
        case STYLE_MATCHING:
        case STYLE_SESSION_PENDING:
        case STYLE_OPEN:
            bgColor   = getColor(R.color.bg_warm_light);
            textColor = getColor(R.color.orange_end);
            break;
        case STYLE_PROPOSAL_PENDING:
            bgColor   = getColor(R.color.bg_info_light);   // add to colors.xml
            textColor = getColor(R.color.color_info);       // add to colors.xml
            break;
        case STYLE_PROPOSAL_WAITING:
            bgColor   = getColor(R.color.bg_tag_inactive);
            textColor = getColor(R.color.text_label);
            break;
        case STYLE_SESSION_ACTIVE:
            bgColor   = getColor(R.color.bg_success_light); // add to colors.xml
            textColor = getColor(R.color.color_success);    // add to colors.xml
            break;
    }
    chipHeaderStatus.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
    chipHeaderStatus.setTextColor(textColor);
}
```

*Add `bg_info_light`, `color_info`, `bg_success_light`, `color_success` to `colors.xml`.*

---

## 5. Per-Card Refactor Delta

### 5.1 `item_finding_card.xml` — Changes

| Element | Current | After |
|---|---|---|
| `txtHotspotName` | Shows raw UUID | Keep field, bind `hotspot.name` in Adapter (not UUID) |
| `chipStatus` + `imgLock` | Top-right chip + lock icon | **Remove both.** Replace row with `MatchCardHeaderView` |
| `CountdownTimerView` (standalone) | Below time text | **Remove.** Now lives inside `MatchCardHeaderView` |
| `txtTimeWindow` | Plain muted text | Wrap in warm-bg pill (add `android:background="@color/bg_warm_light"` + padding, matching Proposal style) |
| `chipDuration` + `chipAgeRange` | In same section as time | Move to Zone 2 as sibling of hotspot name. Add "TIME WINDOW" label above time pill. |
| `btnCancelIntent` stroke color | `@color/orange_end` | Change to `@color/handle_bar` (standard secondary) |
| Section divider | None | Add 1dp divider after Zone 2, before Zone 3 |

**New XML skeleton:**
```xml
<MatchCardHeaderView android:id="@+id/cardHeader" ... />

<!-- Zone 2: Identity -->
<TextView android:id="@+id/txtHotspotName" ... />
<LinearLayout> <!-- duration chip + age chip --> </LinearLayout>

<!-- divider -->
<View ... android:background="@color/handle_bar" />

<!-- Zone 3: When -->
<TextView android:text="TIME WINDOW" ... />  <!-- label -->
<TextView android:id="@+id/txtTimeWindow"
          android:background="@color/bg_warm_light" ... />

<!-- Zone 4: Tags -->
<TagChipGroup android:id="@+id/chipGroupTags" ... />

<!-- Zone 5: Actions -->
<MaterialButton android:id="@+id/btnFindMatch" ... />   <!-- Tier 1, GONE when OPEN -->
<MaterialButton android:id="@+id/btnCancelIntent" ... /> <!-- Tier 2 secondary -->
```

**`FindingAdapter` changes:**
- Bind `MatchCardHeaderView` in `bind()`.
- Call `holder.cardHeader.cancelCountdown()` in `onViewRecycled()` (replaces direct `holder.countdown.cancelCountdown()`).
- `cancelAllTimers()` iterates `holder.cardHeader.cancelCountdown()`.

---

### 5.2 `item_proposal_card.xml` — Changes

| Element | Current | After |
|---|---|---|
| `CountdownTimerView` (in avatar row) | Top-right of partner row | **Remove.** Now inside `MatchCardHeaderView` |
| Avatar row | Zone 1 (top of card) | Becomes Zone 2 (below `MatchCardHeaderView`) |
| Divider | After avatar row | Stays, now separates Zone 2 from Zone 3 |
| Action buttons | `Pass` + `Accept` side-by-side | Keep; they are already Tier 2 + Tier 1. Fix weight to `1:2`. |

**`MatchCardHeaderView` bind logic in `ProposalAdapter`:**
```java
if (proposal.isCurrentUserAccepted()) {
    cardHeader.setStatus("Waiting…", MatchCardHeaderView.STYLE_PROPOSAL_WAITING);
} else {
    cardHeader.setStatus("Decide Now", MatchCardHeaderView.STYLE_PROPOSAL_PENDING);
}
cardHeader.startCountdown(proposal.getExpiresAt());
```

The status badge now communicates the sub-state that was previously only implied by button visibility. The user immediately sees "Waiting…" vs "Decide Now" at the top of the card without needing to scan down to the button area.

---

### 5.3 `item_session_card.xml` — Changes (Largest Delta)

| Element | Current | After |
|---|---|---|
| **130dp map `FrameLayout`** | Top of card, full-width | **Remove entirely.** Replace with a ghost "View on Map →" text link in Zone 3 (add back as real feature when map is implemented) |
| Avatar row | Below map, shows UUID | Becomes Zone 2. `txtPartnerName` stays; ensure domain always populates `partnerName` |
| `txtMeetingPoint` | Raw coordinates | Bind to `hotspotName` (or abbreviated address) instead of lat/lng |
| `txtMeetingTime` | `🕐 03:00` raw text | Move to Zone 3. Add "MEETING" label above, same warm-pill style |
| `btnCancelSession` | `color_danger` outlined | Change to standard secondary (muted outlined). Destructive confirmation is in the dialog |
| `btnReportIssue` | Right-aligned muted text | Move to Tier 3 danger text button at bottom of Zone 5 |
| Zone 1 | None | Add `MatchCardHeaderView` |
| Divider | After avatar row | Stays, now separates Zone 2 from Zone 3 |

**New XML skeleton:**
```xml
<MatchCardHeaderView android:id="@+id/cardHeader" ... />

<!-- Zone 2: Identity -->
<AvatarInitialView ... />
<TextView android:id="@+id/txtPartnerName" ... />  <!-- partner name, not UUID -->
<TextView android:id="@+id/txtHotspotName" ... />  <!-- hotspot name, not coordinates -->

<!-- divider -->

<!-- Zone 3: When & Where -->
<TextView android:text="MEETING" ... />
<TextView android:id="@+id/txtMeetingTime"
          android:background="@color/bg_warm_light" ... />  <!-- warm pill -->
<!-- "View on Map →" text link (GONE until map feature is live) -->

<!-- Zone 5: Actions -->
<LinearLayout>
    <MaterialButton android:id="@+id/btnChat" ... />         <!-- Tier 2 -->
    <MaterialButton android:id="@+id/btnCancelSession" ... /> <!-- Tier 2, muted outlined -->
</LinearLayout>
<ActivationWindowButtonView android:id="@+id/activationBtn" ... />  <!-- Tier 1, PENDING only -->
<MaterialButton android:id="@+id/btnComplete" ... />                 <!-- Tier 1, ACTIVE only -->
<MaterialButton android:id="@+id/btnReportIssue" ... />              <!-- Tier 3, ACTIVE only -->
```

**`SessionAdapter` bind logic for `MatchCardHeaderView`:**
```java
if (session.getStatus() == WalkSession.Status.PENDING) {
    cardHeader.setStatus("Ready to Walk", MatchCardHeaderView.STYLE_SESSION_PENDING);
} else if (session.getStatus() == WalkSession.Status.ACTIVE) {
    cardHeader.setStatus("Walk Active", MatchCardHeaderView.STYLE_SESSION_ACTIVE);
}
cardHeader.hideCountdown();  // Session TTL (24h) not useful on card
```

**`SessionAdapter.onViewRecycled()` addition:**
```java
holder.cardHeader.cancelCountdown(); // no-op for session, but safe for future
```

---

## 6. Architecture Compliance

### 6.1 Custom View Checklist for `MatchCardHeaderView`

- [x] Pattern appears ≥ 3 times (all 3 item cards) → mandatory Custom View
- [x] Contains internal state (countdown running / badge color)
- [x] `declare-styleable` in `attrs.xml`
- [x] Layout uses `<merge>` root
- [x] Class reads attrs in `finally { a.recycle(); }`
- [x] Public API does not expose inner `chipHeaderStatus` or `headerCountdown` views
- [x] Extends `ConstraintLayout` (flat compound view: badge + countdown side by side)
- [x] Added to catalogue in `Frontend_VI.md` section 8.5

### 6.2 MVVM Constraints — No violations

- All binding stays in Adapter `bind()` methods. Fragments remain "thin."
- No new ViewModel fields needed. `MatchCardHeaderView` derives its state from the same domain objects already in `MatchesUiState`.
- `MatchCardHeaderView` does not import `Retrofit` or `Room` — it is a pure View.

### 6.3 Custom View Catalogue Entry (add to `Frontend_VI.md` §8.5)

```
| MatchCardHeaderView | core.designsystem.view | Status badge (left) + optional countdown (right) — shared header row for all Matches sub-fragment cards | wm_statusLabel, wm_statusStyle, wm_showCountdown |
```

---

## 7. Before / After Summary

### Before (3 alien card shapes)
```
Finding:   [UUID  🔒  MATCHING]    ← chip right
           10:00–15:00
           319:22                  ← countdown dangling below, no label
           [300min][18-42y/o]
           [Tags]
           [View Proposal ●●●●●●●]
           [Cancel Intent ○○○○○○○] ← orange stroke (non-standard)

Proposal:  [Avatar | Name · Age   | 03:02]  ← countdown top-right
           [         ⭐ 0·Trusted  |      ]
           ─────────────────────────────
           COMMON TIME WINDOW
           [10:00–15:00]
           COMMON INTERESTS
           [tags]
           [Pass○] [Accept ●●●●●●]
           Cancel Proposal

Session:   [██████ Map Preview ████████]   ← 130dp alien element
           [Avatar | UUID/coords         ]
           ───────────────────────────────
           🕐 03:00
           [Chat○] [Cancel Session◌◌◌◌◌]  ← red outline (alarm)
           [I'm Here! ●●●●●●●●●●●●●●●●]
```

### After (shared 5-zone box)
```
Finding:   [Matched 🔒              ↓319:22]  ← Zone 1: MatchCardHeaderView
           ─────────────────────────────────
           📍 Hồ Hoàn Kiếm                    ← Zone 2: readable hotspot name
           [300min] [18-42y/o]
           ─────────────────────────────────
           TIME WINDOW                         ← Zone 3: labeled warm pill
           [10:00 – 15:00]
           ─────────────────────────────────
           [Tags]                              ← Zone 4
           [View Proposal ●●●●●●●●●●●●●●●]    ← Zone 5: Tier 1

Proposal:  [Decide Now             ↓03:02]    ← Zone 1: MatchCardHeaderView
           ─────────────────────────────────
           [Avatar] Nguyen Bao Duy · 0 tuổi   ← Zone 2: same as before
                    ⭐ 0 · Trusted
           ─────────────────────────────────
           COMMON TIME WINDOW                  ← Zone 3: same as before
           [10:00 – 15:00]
           COMMON INTERESTS
           [Tags]                              ← Zone 4
           [Pass○]  [Accept ✦●●●●●]           ← Zone 5: Tier 2 + Tier 1
           Cancel Proposal                     ← Zone 5: Tier 3

Session:   [Ready to Walk                  ]  ← Zone 1: MatchCardHeaderView (no countdown)
           ─────────────────────────────────
           [Avatar] Nguyen Bao Duy            ← Zone 2: partner name (not UUID)
                    📍 Hồ Hoàn Kiếm           ←         hotspot name (not coords)
           ─────────────────────────────────
           MEETING                            ← Zone 3: labeled warm pill
           [🕐 17:30]
           ─────────────────────────────────
           [Chat○]  [Cancel Session○]         ← Zone 5: Tier 2 + Tier 2 (muted, not red)
           [I'm Here! ●●●●●●●●●●●●●●●●●]     ← Zone 5: Tier 1
```

---

## 8. Implementation Checklist

**Phase A — New Custom View:**
- [ ] Create `MatchCardHeaderView.java` in `core/designsystem/view/`
- [ ] Create `res/layout/view_match_card_header.xml` (`<merge>` root)
- [ ] Add `declare-styleable MatchCardHeaderView` to `res/values/attrs.xml`
- [ ] Add color tokens `bg_info_light`, `color_info`, `bg_success_light`, `color_success` to `colors.xml`
- [ ] Update catalogue table in `Frontend_VI.md` §8.5

**Phase B — Finding card:**
- [ ] Replace `chipStatus` + `imgLock` row with `MatchCardHeaderView`
- [ ] Remove standalone `CountdownTimerView`, wire through `cardHeader`
- [ ] Bind `hotspotName` (not `hotspotId` UUID) in `FindingAdapter`
- [ ] Wrap `txtTimeWindow` in warm-bg pill style + "TIME WINDOW" label
- [ ] Add Zone 2→3 divider
- [ ] Fix `btnCancelIntent` stroke to `@color/handle_bar`
- [ ] Update `cancelAllTimers()` to call `cardHeader.cancelCountdown()`

**Phase C — Proposal card:**
- [ ] Remove standalone `CountdownTimerView` from avatar row, wire through `MatchCardHeaderView`
- [ ] Insert `MatchCardHeaderView` as first child (above avatar row)
- [ ] Bind `STYLE_PROPOSAL_PENDING` vs `STYLE_PROPOSAL_WAITING` based on `isCurrentUserAccepted()`

**Phase D — Session card:**
- [ ] Remove 130dp map `FrameLayout`
- [ ] Insert `MatchCardHeaderView` as first child
- [ ] Bind `STYLE_SESSION_PENDING` or `STYLE_SESSION_ACTIVE`
- [ ] Bind `txtPartnerName` correctly (verify `session.getPartnerName()` is non-null in all states)
- [ ] Add `txtHotspotName` bound to hotspot name (not coordinates); keep `txtMeetingPoint` field for lat/lng as internal reference if needed by Tracking screen
- [ ] Add "MEETING" label + warm-pill style to `txtMeetingTime`
- [ ] Change `btnCancelSession` to muted outlined style (remove red stroke/text)
- [ ] Move `btnReportIssue` to Tier 3 full-width danger text button at bottom
- [ ] Add Zone 2→3 divider (already present, confirm positioning after map removal)
