# WalkMate — Backend Use Cases Index

**Document Purpose:** Master index for all backend API use cases, organized by functional domain. Each domain file is the single source of truth for its area. Cross-referenced against `invariants.md` and `state-transitions.md`.

**Last Updated:** 2026-04-12
**Backend Branch Analyzed:** `implement/realtime`

---

## Domain Files

| Domain | File | Use Cases |
|--------|------|-----------|
| Auth & Profile | [auth_profile_use_cases.md](auth_profile_use_cases.md) | UC-01 to UC-06 |
| Discovery | [discovery_use_cases.md](discovery_use_cases.md) | UC-07 |
| Walk Intent | [intent_use_cases.md](intent_use_cases.md) | UC-08 to UC-11 |
| Proposal Negotiation | [proposal_use_cases.md](proposal_use_cases.md) | UC-12 to UC-15 |
| Session Lifecycle | [session_use_cases.md](session_use_cases.md) | UC-16 to UC-20 |
| GPS Tracking | [tracking_use_cases.md](tracking_use_cases.md) | UC-21 |
| Post-Session | [post_session_use_cases.md](post_session_use_cases.md) | UC-22 to UC-25 |
| Social | [social_use_cases.md](social_use_cases.md) | UC-26 to UC-31 |
| Notifications | [notifications_use_cases.md](notifications_use_cases.md) | UC-32 to UC-33 |
| Gamification | [gamification_use_cases.md](gamification_use_cases.md) | UC-34 to UC-36 |
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
| **DISCOVERY** | | | |
| UC-07 | Hotspots | Browse Hotspot Map | [discovery_use_cases.md](discovery_use_cases.md#uc-07--browse-hotspot-map) |
| **WALK INTENT** | | | |
| UC-08 | Intent | Create Walk Intent | [intent_use_cases.md](intent_use_cases.md#uc-08--create-walk-intent) |
| UC-09 | Intent | View My Active Intents | [intent_use_cases.md](intent_use_cases.md#uc-09--view-my-active-intents) |
| UC-10 | Intent | Cancel Walk Intent | [intent_use_cases.md](intent_use_cases.md#uc-10--cancel-walk-intent) |
| UC-11 | Intent | Trigger Match | [intent_use_cases.md](intent_use_cases.md#uc-11--trigger-match) |
| **PROPOSAL NEGOTIATION** | | | |
| UC-12 | Proposal | View Incoming Proposals | [proposal_use_cases.md](proposal_use_cases.md#uc-12--view-incoming-proposals) |
| UC-13 | Proposal | Accept a Proposal | [proposal_use_cases.md](proposal_use_cases.md#uc-13--accept-a-proposal) |
| UC-14 | Proposal | Pass (Reject) a Proposal | [proposal_use_cases.md](proposal_use_cases.md#uc-14--pass-reject-a-proposal) |
| UC-15 | Proposal | Cancel a Proposal (Withdraw Intent) | [proposal_use_cases.md](proposal_use_cases.md#uc-15--cancel-a-proposal-withdraw-intent) |
| **SESSION LIFECYCLE** | | | |
| UC-16 | Session | View Active Sessions | [session_use_cases.md](session_use_cases.md#uc-16--view-active-sessions) |
| UC-17 | Session | Activate Session (Arrive at Hotspot) | [session_use_cases.md](session_use_cases.md#uc-17--activate-session-arrive-at-hotspot) |
| UC-18 | Session | Cancel a Pending Session | [session_use_cases.md](session_use_cases.md#uc-18--cancel-a-pending-session) |
| UC-19 | Session | Complete Walk Session (User-initiated) | [session_use_cases.md](session_use_cases.md#uc-19--complete-walk-session-user-initiated) |
| UC-20 | Session | Abort Active Session (Emergency) | [session_use_cases.md](session_use_cases.md#uc-20--abort-active-session-emergency) |
| **GPS TRACKING** | | | |
| UC-21 | Tracking | Background GPS Route Sync | [tracking_use_cases.md](tracking_use_cases.md#uc-21--background-gps-route-sync) |
| **POST-SESSION** | | | |
| UC-22 | History | View Session History | [post_session_use_cases.md](post_session_use_cases.md#uc-22--view-session-history) |
| UC-23 | History | View Session Route Replay | [post_session_use_cases.md](post_session_use_cases.md#uc-23--view-session-route-replay) |
| UC-24 | Review | Submit a Review | [post_session_use_cases.md](post_session_use_cases.md#uc-24--submit-a-review) |
| UC-25 | Report | Submit an Incident Report | [post_session_use_cases.md](post_session_use_cases.md#uc-25--submit-an-incident-report) |
| **SOCIAL** | | | |
| UC-26 | Social | View a Public User Profile | [social_use_cases.md](social_use_cases.md#uc-26--view-a-public-user-profile) |
| UC-27 | Social | Follow a User | [social_use_cases.md](social_use_cases.md#uc-27--follow-a-user) |
| UC-28 | Social | Unfollow a User | [social_use_cases.md](social_use_cases.md#uc-28--unfollow-a-user) |
| UC-29 | Social | View Followers / Following Lists | [social_use_cases.md](social_use_cases.md#uc-29--view-followers--following-lists) |
| UC-30 | Social | Block a User | [social_use_cases.md](social_use_cases.md#uc-30--block-a-user) |
| UC-31 | Social | Unblock a User | [social_use_cases.md](social_use_cases.md#uc-31--unblock-a-user) |
| **NOTIFICATIONS** | | | |
| UC-32 | Notifications | View Notification Feed | [notifications_use_cases.md](notifications_use_cases.md#uc-32--view-notification-feed) |
| UC-33 | Notifications | Mark Notification as Read | [notifications_use_cases.md](notifications_use_cases.md#uc-33--mark-notification-as-read) |
| **GAMIFICATION** | | | |
| UC-34 | Gamification | View User Badges | [gamification_use_cases.md](gamification_use_cases.md#uc-34--view-user-badges) |
| UC-35 | Gamification | View User Stats | [gamification_use_cases.md](gamification_use_cases.md#uc-35--view-user-stats) |
| UC-36 | Gamification | View Leaderboard | [gamification_use_cases.md](gamification_use_cases.md#uc-36--view-leaderboard) |

---

## Global References

- [Appendix A — Global Error Handling](appendix.md#appendix-a--global-error-handling)
- [Appendix B — Key Invariant ↔ UI Decision Map](appendix.md#appendix-b--key-invariant--ui-decision-map)
