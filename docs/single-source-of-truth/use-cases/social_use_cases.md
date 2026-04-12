# WalkMate — Social Use Cases

> Part of: [Use Cases Index](README.md)

**Domain:** Social Graph — Profiles, Following, and Blocking
**Last Updated:** 2026-04-12

---

## Table of Contents

| UC# | Use Case | API Endpoint |
|-----|----------|--------------|
| UC-26 | [View a Public User Profile](#uc-26--view-a-public-user-profile) | `GET /api/v1/users/{userId}` |
| UC-27 | [Follow a User](#uc-27--follow-a-user) | `POST /api/v1/users/{userId}/follow` |
| UC-28 | [Unfollow a User](#uc-28--unfollow-a-user) | `DELETE /api/v1/users/{userId}/follow` |
| UC-29 | [View Followers / Following Lists](#uc-29--view-followers--following-lists) | `GET /api/v1/users/{userId}/followers` / `following` |
| UC-30 | [Block a User](#uc-30--block-a-user) | `POST /api/v1/users/{userId}/block` |
| UC-31 | [Unblock a User](#uc-31--unblock-a-user) | `DELETE /api/v1/users/{userId}/block` |

---

### UC-26 — View a Public User Profile

**Use Case Name:** View a Public User Profile

**Initial assumption:** User taps on another user's name/avatar anywhere in the app (from session history, proposal, followers list, etc.). Authentication not required to **view** the profile; authentication is required to **act** (Follow/Block).

**Normal:**
1. UI calls `GET /api/v1/users/{userId}`.
2. Backend returns `200 OK` with public profile data.
3. UI calls `GET /api/v1/users/{userId}/badges` and `GET /api/v1/users/{userId}/stats` in parallel.
4. UI calls `GET /api/v1/users/{userId}/reviews` to show review feed.
5. UI renders full public profile page: avatar, name, bio, tags, trust score, stats, badges, reviews.
6. If viewing another user's profile (not self): show "Follow" / "Unfollow" toggle and "Block" option.
7. **Unauthenticated guard:** If the user is not logged in and taps "Follow" or "Block", do **not** call the API. Instead, navigate to the Login screen and show a prompt: "Log in to follow or block users." After successful login, return the user to this profile screen.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| User not found | `USER_NOT_FOUND` | Show toast: "User not found." Navigate back. |

**Other activities:** None.

**System state on completion:** Read-only profile view. Action buttons for follow/block are enabled.

---

### UC-27 — Follow a User

**Use Case Name:** Follow a User

**Initial assumption:** User is **authenticated**. Viewing another user's public profile (UC-26). Not currently following them. (If user is unauthenticated and taps "Follow", redirect to Login — see UC-26 step 7.)

**Normal:**
1. User taps "Follow".
2. UI optimistically updates the button to "Following".
3. UI calls `POST /api/v1/users/{userId}/follow`.
4. Backend returns `200 OK`. Follow relationship is created.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Revert button. Show toast: "User not found." |
| Trying to follow self | `FOLLOW_SELF_FOLLOW_FORBIDDEN` | Hide "Follow" button for the user's own profile. |
| Already following | `FOLLOW_ALREADY_FOLLOWING` | Button should already show "Following" — this is a double-tap race. Ignore silently. |

**Other activities:** None.

**System state on completion:** Follow relationship exists. User appears in target's followers list (`GET /api/v1/users/{userId}/followers`).

---

### UC-28 — Unfollow a User

**Use Case Name:** Unfollow a User

**Initial assumption:** User is authenticated. Currently following the target user.

**Normal:**
1. User taps "Following" (toggle to unfollow).
2. UI shows confirmation: "Unfollow this user?"
3. UI optimistically updates button to "Follow".
4. UI calls `DELETE /api/v1/users/{userId}/follow`.
5. Backend returns `200 OK`. Follow relationship is removed.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Revert button state. Show toast: "Could not unfollow. Try again." |

**Other activities:** None.

**System state on completion:** Follow relationship removed. User no longer appears in target's followers list.

---

### UC-29 — View Followers / Following Lists

**Use Case Name:** View Followers / Following Lists

**Initial assumption:** User is on a public profile page (own or another user's).

**Normal:**
1. User taps "Followers" tab → UI calls `GET /api/v1/users/{userId}/followers`.
2. User taps "Following" tab → UI calls `GET /api/v1/users/{userId}/following`.
3. Each response returns a list of `{ userId, fullName, avatarUrl }`.
4. UI renders the list; each item is tappable and navigates to UC-26.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show empty state with retry option. |

**Other activities:** None.

**System state on completion:** User sees the social graph. Read-only.

---

### UC-30 — Block a User

**Use Case Name:** Block a User

**Initial assumption:** User is **authenticated**. Viewing another user's public profile. Has a "Block" option available (via overflow menu). (If user is unauthenticated and taps "Block", redirect to Login — see UC-26 step 7.)

**Normal:**
1. User selects "Block User" from the overflow menu.
2. UI shows a strong confirmation dialog: "Block [name]? They won't be able to see your activity and you won't be matched together."
3. User confirms.
4. UI calls `POST /api/v1/users/{userId}/block`.
5. Backend returns `200 OK`. Block relationship is created. Any existing follow relationships in both directions are silently removed.
6. UI navigates back to the previous screen and removes the blocked user from visible lists.

**What can go wrong:**

| Condition | Error Code | UI Reaction |
|-----------|-----------|-------------|
| Target user not found | `SOCIAL_USER_NOT_FOUND` | Show toast: "User not found." |
| Trying to block self | `BLOCK_SELF_BLOCK_FORBIDDEN` | Hide "Block" on own profile. |
| Already blocked | `BLOCK_ALREADY_BLOCKED` | Show "Blocked" state in UI already. Ignore. |

**Other activities:** None.

**System state on completion:** Block relationship exists. Follow relationships are torn down. Matching engine will not pair blocked users.

---

### UC-31 — Unblock a User

**Use Case Name:** Unblock a User

**Initial assumption:** User is authenticated. The blocked user appears in a "Blocked Users" settings list.

**Normal:**
1. User taps "Unblock" next to the blocked user.
2. UI calls `DELETE /api/v1/users/{userId}/block`.
3. Backend returns `200 OK`. Block relationship is removed.
4. UI removes the user from the "Blocked Users" list.

**What can go wrong:**

| Condition | UI Reaction |
|-----------|------------|
| Network failure | Show toast: "Could not unblock. Try again." |

**Other activities:** None.

**System state on completion:** Block relationship removed. Users may be matched again.
