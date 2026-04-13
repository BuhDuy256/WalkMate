# WalkMate — Backend Use Cases Index

**Document Purpose:** Master index for all backend API use cases, organized by functional domain. Each domain file is the single source of truth for its area. Cross-referenced against `invariants.md` and `state-transitions.md`.

**Last Updated:** 2026-04-13
**Backend Branch Analyzed:** `implement/realtime`

---

## Domain Files

| Domain | File | Use Cases |
|--------|------|-----------|
| Auth & Profile | [auth_profile_use_cases.md](auth_profile_use_cases.md) | UC-01 to UC-13 |
| Discovery | [discovery_use_cases.md](discovery_use_cases.md) | UC-14 |
| Walk Intent | [intent_use_cases.md](intent_use_cases.md) | UC-15 to UC-18 |
| Proposal Negotiation | [proposal_use_cases.md](proposal_use_cases.md) | UC-19 to UC-22 |
| Session Lifecycle | [session_use_cases.md](session_use_cases.md) | UC-23 to UC-27 |
| GPS Tracking | [tracking_use_cases.md](tracking_use_cases.md) | UC-28 |
| Post-Session | [post_session_use_cases.md](post_session_use_cases.md) | UC-29 to UC-32 |
| Social | [social_use_cases.md](social_use_cases.md) | UC-33 to UC-38 |
| Notifications | [notifications_use_cases.md](notifications_use_cases.md) | UC-39 to UC-40 |
| Gamification | [gamification_use_cases.md](gamification_use_cases.md) | UC-41 to UC-43 |
| Shared Appendices | [appendix.md](appendix.md) | Global error handling, Invariant↔UI map |

---

## Full Use Case Table

| UC# | Domain | Use Case | File |
|-----|--------|----------|------|
| **AUTH & PROFILE** | | | |
| UC-01 | Auth | Register Account | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-01--register-account) |
| UC-02 | Auth | Login | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-02--login) |
| UC-03 | Profile | View My Profile | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-03--view-my-profile) |
| UC-04 | Profile | Edit My Profile | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-04--edit-my-profile) |
| UC-05 | Profile | Upload Avatar | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-05--upload-avatar) |
| UC-06 | Device | Register FCM Token | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-06--register-fcm-token) |
| UC-07 | Auth | Login with Google (OAuth) | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-07--login-with-google-oauth) |
| UC-08 | Auth | Phone Sign-In — Send OTP | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-08--phone-sign-in--send-otp) |
| UC-09 | Auth | Phone Sign-In — Verify OTP | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-09--phone-sign-in--verify-otp) |
| UC-10 | Auth | Logout (This Device) | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-10--logout-this-device) |
| UC-11 | Auth | Logout All Devices | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-11--logout-all-devices) |
| UC-12 | Auth | Silent Token Refresh | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-12--silent-token-refresh) |
| UC-13 | Profile | Set Profile Visibility | [auth_profile_use_cases.md](auth_profile_use_cases.md#uc-13--set-profile-visibility) |
| **DISCOVERY** | | | |
| UC-14 | Hotspots | Browse Hotspot Map | [discovery_use_cases.md](discovery_use_cases.md#uc-14--browse-hotspot-map) |
| **WALK INTENT** | | | |
| UC-15 | Intent | Create Walk Intent | [intent_use_cases.md](intent_use_cases.md#uc-15--create-walk-intent) |
| UC-16 | Intent | View My Active Intents | [intent_use_cases.md](intent_use_cases.md#uc-16--view-my-active-intents) |
| UC-17 | Intent | Cancel Walk Intent | [intent_use_cases.md](intent_use_cases.md#uc-17--cancel-walk-intent) |
| UC-18 | Intent | Trigger Match (Internal API) | [intent_use_cases.md](intent_use_cases.md#uc-18--trigger-match) |
| **PROPOSAL NEGOTIATION** | | | |
| UC-19 | Proposal | View Incoming Proposals | [proposal_use_cases.md](proposal_use_cases.md#uc-19--view-incoming-proposals) |
| UC-20 | Proposal | Accept a Proposal | [proposal_use_cases.md](proposal_use_cases.md#uc-20--accept-a-proposal) |
| UC-21 | Proposal | Pass (Reject) a Proposal | [proposal_use_cases.md](proposal_use_cases.md#uc-21--pass-reject-a-proposal) |
| UC-22 | Proposal | Cancel a Proposal (Withdraw Intent) | [proposal_use_cases.md](proposal_use_cases.md#uc-22--cancel-a-proposal-withdraw-intent) |
| **SESSION LIFECYCLE** | | | |
| UC-23 | Session | View Active Sessions | [session_use_cases.md](session_use_cases.md#uc-23--view-active-sessions) |
| UC-24 | Session | Activate Session (Arrive at Hotspot) | [session_use_cases.md](session_use_cases.md#uc-24--activate-session-arrive-at-hotspot) |
| UC-25 | Session | Cancel a Pending Session | [session_use_cases.md](session_use_cases.md#uc-25--cancel-a-pending-session) |
| UC-26 | Session | Complete Walk Session (User-initiated) | [session_use_cases.md](session_use_cases.md#uc-26--complete-walk-session-user-initiated) |
| UC-27 | Session | Abort Active Session (Emergency) | [session_use_cases.md](session_use_cases.md#uc-27--abort-active-session-emergency) |
| **GPS TRACKING** | | | |
| UC-28 | Tracking | Background GPS Route Sync | [tracking_use_cases.md](tracking_use_cases.md#uc-28--background-gps-route-sync) |
| **POST-SESSION** | | | |
| UC-29 | History | View Session History | [post_session_use_cases.md](post_session_use_cases.md#uc-29--view-session-history) |
| UC-30 | History | View Session Route Replay | [post_session_use_cases.md](post_session_use_cases.md#uc-30--view-session-route-replay) |
| UC-31 | Review | Submit a Review | [post_session_use_cases.md](post_session_use_cases.md#uc-31--submit-a-review) |
| UC-32 | Report | Submit an Incident Report | [post_session_use_cases.md](post_session_use_cases.md#uc-32--submit-an-incident-report) |
| **SOCIAL** | | | |
| UC-33 | Social | View a Public User Profile | [social_use_cases.md](social_use_cases.md#uc-33--view-a-public-user-profile) |
| UC-34 | Social | Send a Friend Request | [social_use_cases.md](social_use_cases.md#uc-34--send-a-friend-request) |
| UC-35 | Social | Respond to a Friend Request (Accept/Decline) | [social_use_cases.md](social_use_cases.md#uc-35--respond-to-a-friend-request-acceptdecline) |
| UC-36 | Social | View Friends and Friend Requests | [social_use_cases.md](social_use_cases.md#uc-36--view-friends-and-friend-requests) |
| UC-37 | Social | Block a User | [social_use_cases.md](social_use_cases.md#uc-37--block-a-user) |
| UC-38 | Social | Unblock a User | [social_use_cases.md](social_use_cases.md#uc-38--unblock-a-user) |
| **NOTIFICATIONS** | | | |
| UC-39 | Notifications | View Notification Feed | [notifications_use_cases.md](notifications_use_cases.md#uc-39--view-notification-feed) |
| UC-40 | Notifications | Mark Notification as Read | [notifications_use_cases.md](notifications_use_cases.md#uc-40--mark-notification-as-read) |
| **GAMIFICATION** | | | |
| UC-41 | Gamification | View User Badges | [gamification_use_cases.md](gamification_use_cases.md#uc-41--view-user-badges) |
| UC-42 | Gamification | View User Stats | [gamification_use_cases.md](gamification_use_cases.md#uc-42--view-user-stats) |
| UC-43 | Gamification | View Leaderboard | [gamification_use_cases.md](gamification_use_cases.md#uc-43--view-leaderboard) |

---

## Global References

- [Appendix A — Global Error Handling](appendix.md#appendix-a--global-error-handling)
- [Appendix B — Key Invariant ↔ UI Decision Map](appendix.md#appendix-b--key-invariant--ui-decision-map)
