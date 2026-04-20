# Profile Feature — Missing Backend API Flows

This document lists every Profile screen menu action and data field where the app currently
does **not** call a backend API to fetch or display real data. Each entry explains the current
behavior, the expected behavior, and what backend endpoint would be needed.

---

## Part A — Menu / Navigation Items With No API Call

These are the clickable rows visible on the Profile screen. They either navigate to a stub
destination or their handler is an empty comment in `ProfileViewModel`.

### A1. My Badges (`menuMyBadges`)

| Attribute | Detail |
|---|---|
| Click handler | `ProfileViewModel.onMyBadgesClicked()` |
| Current code | `/* Phase D: emit navigation signal */` — empty stub, no navigation fires |
| What the user sees | Tap does nothing visible |
| Expected behavior | Navigate to a dedicated **"All My Badges"** screen that lists every badge the user has earned with its unlock criteria |
| Missing | Navigation event + `navigateToMyBadgesEvent` LiveData in ViewModel; a `MyBadgesFragment` + `MyBadgesViewModel` sub-feature; an API call to `GamificationRepository.getBadges(userId)` (the endpoint exists — `ProfileViewModel` already calls it to preview 3 badges, but a full paginated view is not built) |

### A2. Settings (`menuSettings`)

| Attribute | Detail |
|---|---|
| Click handler | `ProfileViewModel.onSettingsClicked()` |
| Current code | `/* Phase D: emit navigation signal */` — empty stub, no navigation fires |
| What the user sees | Tap does nothing visible |
| Expected behavior | Navigate to a **Settings** screen covering at minimum: notification preferences, privacy settings (visibility mode is currently only a toggle on the profile screen), account actions (change password, delete account) |
| Missing | Navigation event; a `SettingsFragment` sub-feature; backend API calls for preference persistence (no endpoint defined yet) |

---

## Part B — Sub-Tabs / Destinations That Navigate Away But Have No API Integration

These menu items do navigate to other screens, but those destination screens do not currently
fetch data from the backend.

### B1. Walk History (`menuWalkHistory`) → `SessionHistoryFragment`

| Attribute | Detail |
|---|---|
| Navigation | `R.id.action_profile_to_sessionHistoryFragment` — navigation fires correctly |
| Gap | Unknown — needs a separate audit of `SessionHistoryFragment` to confirm whether it fetches walk session data from the backend. The `ProfileViewModel` correctly treats this as a pure navigation event and does not own any history-fetching logic (per the audit: *"Any complex fetching for history is properly delegated to their respective isolated fragments"*). |
| Action | Audit `SessionHistoryFragment` to confirm it has a working `SessionHistoryViewModel` + `WalkSessionRepository` call. |

### B2. Leaderboard (`menuLeaderboard`) → `leaderboardFragment`

| Attribute | Detail |
|---|---|
| Navigation | `R.id.action_profile_to_leaderboardFragment` — navigation fires correctly |
| Gap | Unknown — needs a separate audit of `LeaderboardFragment` to confirm it fetches ranked data from the backend |
| Action | Audit `LeaderboardFragment` |

### B3. Friends (`menuFriends`) → `friendsFragment`

| Attribute | Detail |
|---|---|
| Navigation | `R.id.action_profile_to_friendsFragment` — navigation fires correctly |
| Gap | Unknown — needs a separate audit of `FriendsFragment` |
| Action | Audit `FriendsFragment` |

### B4. Blocked Users (`menuBlockedUsers`) → `blockedUsersFragment`

| Attribute | Detail |
|---|---|
| Navigation | `R.id.action_profile_to_blockedUsersFragment` — navigation fires correctly |
| Gap | Unknown — needs a separate audit of `BlockedUsersFragment` |
| Action | Audit `BlockedUsersFragment` |

---

## Summary Table

| # | Location | Gap Type | Priority |
|---|---|---|---|
| A1 | My Badges menu | Stub — no navigation, no API | High (user-facing dead tap) |
| A2 | Settings menu | Stub — no navigation, no API | High (user-facing dead tap) |
| B1–B4 | Walk History / Leaderboard / Friends / Blocked | Navigation works; destination API coverage unknown | Needs per-fragment audit |
