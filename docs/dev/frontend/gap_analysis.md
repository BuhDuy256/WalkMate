 ---
  Gap Analysis Report — Frontend vs. Backend Use Cases (UC-01 → UC-43)

  Date: 2026-04-13 | Backend branch: implement/realtime | Frontend branch: merge/oauth

  ---
  Methodology

  The analysis cross-referenced every UC against three evidence layers: (1) ACKG symbol search for UI/ViewModel/Repository/DTO classes, (2) method-level outlines of key
  ViewModels and Repository interfaces, and (3) the AppEventBus + MainActivity FCM dispatch table. Gaps are classified as Critical (feature does not exist), Major
  (feature exists but is architecturally wrong or directly violates a use-case rule), and Medium/Minor (behavior partially implemented but incomplete).

  ---
  Section 1 — Critical Gaps (Features Fully Missing)

  GAP-1 · UC-33: Public User Profile Screen

  Expected: A PublicProfileFragment (or equivalent) reachable by tapping any user's name/avatar. Renders their avatarUrl, bio, tags, trustScore, stats, badges, reviews.
  Conditionally shows friendship action buttons based on relationship state (Add Friend / Request Sent / Accept + Decline / Invite Walk + Remove Friend). Unauthenticated
  guard redirects to login for any mutating action.

  Actual: No such screen exists. ProfileFragment renders the current user's own profile only. There is no entry point, navigation route, Fragment class, or ViewModel
  scoped to viewing another user.

  Blast radius: UC-34, UC-35, UC-36, UC-37 all require this screen as their primary entry surface. Session History partner name taps (UC-29) and Leaderboard row taps
  (UC-43) also deep-link here.

  ---
  GAP-2 · UC-34/UC-35: Friend Request Send + Respond

  Expected: SocialRepository exposes sendFriendRequest(userId), acceptFriendRequest(requestId), and declineFriendRequest(requestId). UI surfaces these in the Public
  Profile screen (GAP-1) and the incoming requests list (GAP-3).

  Actual: The frontend SocialRepository interface (domain/social/SocialRepository.java) contains only: follow, unfollow, getFollowers, getFollowing, getFriends, block,
  unblock. Friend request verbs (sendFriendRequest, acceptFriendRequest, declineFriendRequest) do not exist anywhere in the frontend domain, data, or UI layers.
  SocialRepositoryImpl confirms this — only getFriends, follow, unfollow, getFollowers, getFollowing, block, unblock are implemented.

  ---
  GAP-3 · UC-36: Friends & Friend Requests Screen

  Expected: A tabbed screen (Friends | Incoming Requests | Sent Requests) calling GET /api/v1/friends, GET /api/v1/friends/requests/incoming, and GET
  /api/v1/friends/requests/outgoing. Friends list items expose quick actions: Invite Walk (deep-link to UC-15 with is_private=true), Remove Friend, View Profile.

  Actual: No FriendsFragment, no FriendsViewModel, no corresponding API service methods for incoming/outgoing requests. Only SocialRepository.getFriends() exists (called
  by HomeViewModel for the quick-invite candidate list on the Home Dashboard). The multi-tab social screen with request management is entirely absent.

  ---
  GAP-4 · UC-38: Blocked Users Settings Screen

  Expected: A settings-accessible list of blocked users, each with an Unblock action calling DELETE /api/v1/users/{userId}/block.

  Actual: SocialRepository.unblock() and SocialRepositoryImpl.unblock() exist at the data layer, but there is no BlockedUsersFragment, no ViewModel, and no navigation
  entry point exposing this screen. The infrastructure code has been built; the UI container and access path are completely missing.

  ---
  GAP-5 · UC-43: Standalone Leaderboard Screen

  Expected: A dedicated Leaderboard screen (accessible from the bottom nav or a Profile sub-navigation) calling GET /api/v1/leaderboard, rendering the top-50 ranked list,
   highlighting the authenticated user's row, and making each row tappable to UC-33 (public profile).

  Actual: GamificationRepository.getLeaderboard(), LeaderboardEntry domain model, LeaderboardEntryResponse DTO, and GamificationRepositoryImpl.getLeaderboard() all exist.
   However, the leaderboard is only rendered inside PostSessionSummaryFragment as a post-walk context widget. No standalone screen, no navigation route, no persistent
  access point.

  ---
  Section 2 — Major Gaps (Architectural Violations or Banned Behaviors)

  GAP-6 · UC-18 Violation: triggerMatch Exposed as a User Action

  Rule (UC-18, explicit): POST /api/v1/intents/{intentId}/match is an internal API. "Android app must not expose this endpoint as a user-triggerable button."

  Actual: FindingFragment has an onFindMatchClicked() method (line 53). MatchesViewModel.triggerMatch() (line 235, docstring: "gap 3.6") explicitly implements this flow.
  The "Find Match" button exists in the UI and is wired up. This is a direct protocol violation and must be removed.

  ---
  GAP-7 · Frontend SocialRepository — Stale Follow/Follower Model

  Rule: The backend social model is friend-request based (Friendship entity, PENDING/ACCEPTED/DECLINED states). There is no follow/follower concept in the backend.

  Actual: The frontend SocialRepository interface and SocialRepositoryImpl still expose follow(), unfollow(), getFollowers(), and getFollowing(). These map to
  non-existent backend endpoints. Any call to these methods will return 404. The entire social domain layer needs to be rebuilt around the friend-request model
  (sendFriendRequest, acceptFriendRequest, declineFriendRequest, getIncomingRequests, getOutgoingRequests, removeFriend).

  ---
  GAP-8 · UC-15 Private Intent Flow — No UI

  Rule: Create Intent form must include a Private Walk toggle. When enabled, a friend picker UI is shown (sourced from the Friends list via UC-36). invited_friend_id must
   be set before submission. Client-side validation: friendship must be ACCEPTED.

  Actual: The CreateWalkIntentRequest DTO correctly contains isPrivate and getInvitedFriendId() fields. However, ExploreFragment / the Create Intent bottom-sheet UI has
  no privacy toggle, no friend picker component, and no client-side friendship validation. Additionally, the friends list required as a data source (GAP-3) does not
  exist. The DTO is ready; the entire UI surface is absent.

  ---
  GAP-9 · FCM Event Handling — Incomplete Dispatch Table

  Rule: AppEventBus and MainActivity.observeAppEventBus() must route all backend-pushed notification types to the correct destination screen.

  Actual: observeAppEventBus() (MainActivity:127) only handles MATCH_FOUND — it navigates to the Matches tab and scrolls to the Proposal sub-tab. The following FCM types
  defined in UC-19/UC-23/UC-36/UC-39 are unhandled:

  ┌─────────────────────────┬────────────────────────────────────────┬───────────────┐
  │        FCM Type         │          Required Navigation           │ Current State │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ PROPOSAL_RECEIVED       │ Proposal tab → open detail             │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ INVITE_SENT             │ Proposal tab → open detail             │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ PROPOSAL_ACCEPTED       │ Proposal tab, show status              │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ SESSION_CONFIRMED       │ Session Detail (UC-23) with session_id │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ SESSION_ACTIVE          │ Active Session screen (UC-23)          │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ FRIEND_REQUEST_RECEIVED │ Social/Incoming Requests (UC-36)       │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ FRIEND_REQUEST_ACCEPTED │ Friends list (UC-36)                   │ ❌ Missing    │
  ├─────────────────────────┼────────────────────────────────────────┼───────────────┤
  │ FRIEND_REQUEST_DECLINED │ Sent Requests (UC-36)                  │ ❌ Missing    │
  └─────────────────────────┴────────────────────────────────────────┴───────────────┘

  ---
  Section 3 — Medium Gaps (Partial Implementations with Wrong Behavior)

  GAP-10 · UC-14: Auth Guard on "Create Intent" CTA

  Rule: If user is unauthenticated and taps a hotspot's "Create Intent" CTA, do not call any API. Navigate to Login. After login, return to Create Intent with the
  selected hotspot pre-filled.

  Actual: ExploreViewModel.selectHotspot() transitions directly to SETUP state. No auth check gate exists before transitioning. After login from AuthActivity, there is no
   saved hotspot state to restore. The return-to-intent-with-prefill flow is not implemented.

  ---
  GAP-11 · UC-15 / UC-19: Private Invite Proposal — Sender Pre-Accepted State

  Rule (UC-20): When a private invite is created (UC-15, is_private=true), the backend auto-accepts the sender side. When the sender opens the proposal, they must
  immediately see the "waiting for partner" state (Accept button disabled) without needing to tap Accept.

  Actual: ProposalFragment / MatchesViewModel.acceptProposal() handles Case A (partial acceptance) only after a tap. There is no logic to detect the "sender already
  accepted" condition from the proposal payload and render the waiting state without interaction.

  ---
  GAP-12 · UC-21: Pass Dialog Must Differentiate Public vs. Private Invite

  Rule: Confirmation dialog text differs:
  - Public proposal: "Pass on this match? Your intent will stay active and we'll keep looking for other partners."
  - Private invite: "Decline this private invite? This invite will be closed and you will not be added to the public wait list."

  Post-pass navigation also differs: public → Intent tab (OPEN); private invite → Proposal/Social context without surfacing the receiver in the OPEN wait list.

  Actual: MatchesViewModel.passProposal() (line 270) does an optimistic remove without checking proposal type. No dialog differentiation logic exists.

  ---
  GAP-13 · UC-20/UC-23: Chat Button Missing on Session Detail

  Rule (UC-20 Required Navigation, UC-23): After a proposal is confirmed, the Session Detail screen must render a Chat button (speech-bubble icon) that opens the
  WebSocket/Chat UI scoped to session_id. The button must remain enabled until the session reaches a terminal state (invariant S-7).

  Actual: SessionFragment has onArriveClicked(), onAbortClicked(), onCompleteClicked() — no Chat button method. ChatFragment and ChatViewModel exist as separate classes,
  but there is no wiring from SessionFragment into ChatFragment with a session_id argument. The Chat UI exists in isolation.

  ---
  GAP-14 · UC-24: Activation Window Enforcement (Invariant S-3)

  Rule: "I'm Here!" button must be disabled outside [scheduledStart − 10 min, scheduledStart + 15 min]. A countdown shows time-until-window-opens before the window. On
  SESSION_ACTIVATION_WINDOW_CLOSED error: show toast, poll GET /api/v1/sessions/active once after 5 seconds, navigate to History when session disappears.

  Actual: SessionFragment.onArriveClicked() calls activateSession() without any client-side window check. There is no countdown widget for window state. The
  SESSION_ACTIVATION_WINDOW_CLOSED error case (the specific 5-second-then-poll-then-navigate behavior) is not implemented in MatchesViewModel.activateSession().

  ---
  GAP-15 · UC-32: Report Available from ACTIVE Session (Not Just Post-Abort)

  Rule: Incident report can be submitted from session statuses: ACTIVE, NO_SHOW, COMPLETED, ABORTED. Reporting windows: 72h for COMPLETED, 24h for ABORTED/NO_SHOW.

  Actual: ReportIncidentFragment docstring explicitly states: "Shown from PostSessionSummaryFragment when the session was aborted." The Fragment is only wired for the
  post-abort path. No "Report an Issue" action is available from a live ACTIVE session in SessionFragment, nor from SessionHistoryFragment for COMPLETED/NO_SHOW entries.

  ---
  GAP-16 · UC-39: Notification Deep-Link Navigation Incomplete

  Rule: Each notification type has a specific navigation target (see UC-39 table). Tapping routes to the correct destination with appropriate arguments.

  Actual: NotificationFragment renders the list and calls mark-as-read on tap, but there is no notification.type dispatch table mapping to NavController destinations. All
   8 new types listed in GAP-9 are also absent from the notification tap handler, not just the FCM foreground handler.

  ---
  Section 4 — Minor Gaps (UI Enforcement Details)

  GAP-17 · UC-16/UC-19: Expiry Countdown Timers

  Rule: Each OPEN intent card (UC-16) must show a live countdown to expires_at. Each PENDING proposal card (UC-19) must show a live 5-minute countdown (invariant P-4).
  When the timer hits 0, the list must auto-refresh.

  Actual: No countdown timer logic is visible in FindingFragment.renderState() or ProposalFragment. The MatchesViewModel has loadAll() but no scheduled refresh on expiry.

  ---
  GAP-18 · UC-26 / Invariant S-5: "Complete Walk" 5-Minute Minimum Countdown

  Rule: "Complete Walk" button must be disabled until 5 minutes have elapsed since started_at. A countdown timer must show time remaining until the button becomes
  enabled.

  Actual: TrackingViewModel owns a walk timer but no evidence of an S-5 enforcement flag that disables the complete button with a countdown.

  ---
  GAP-19 · UC-20: Celebration Animation on Double-Accept (Case B)

  Rule: On status: "CONFIRMED" response from POST /api/v1/proposals/{proposalId}/accept, show a celebration animation (e.g., confetti overlay) before navigating to
  Session Detail.

  Actual: MatchesViewModel.acceptProposal() scrolls to the Session tab on Case B but no animation trigger exists.

  ---
  GAP-20 · UC-14: Hotspot Pin Visual Weight

  Rule: Each pin's visual weight (size or color) must reflect openIntentCount — more intents = more prominent pin.

  Actual: ExploreViewModel.loadHotspots() fetches hotspot data including openIntentCount. Whether ExploreFragment's Google Maps marker rendering uses openIntentCount to
  scale pin visuals is not verifiable from symbol analysis alone — this requires visual inspection of fragment_explore.xml / marker rendering code.

  ---
  GAP-21 · Appendix A: Global Error Handling — HTTP Status vs. error.code

  Rule: @GlobalExceptionHandler maps all DomainExceptions to HTTP 400. UI must never use HTTP status alone to distinguish domain errors — always read error.code from the
  response body.

  Risk: If the current ApiResponse<T> parsing in data/repository/* switches on HTTP status code for domain errors (e.g., 404 = not found, 400 = bad request), every domain
   error will be misclassified. Per-UC error tables require error.code-level granularity (e.g., INTENT_NOT_OPEN vs. INTENT_NOT_OWNER are both 400).

  ---
  Section 5 — Consolidated Gap Inventory

  ┌────────┬────────────────┬─────────────┬─────────────────────────────────────────────────────────────────────────────────────────┐
  │   ID   │       UC       │  Severity   │                                         Summary                                         │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-1  │ UC-33          │ 🔴 Critical │ No Public User Profile screen exists                                                    │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-2  │ UC-34/35       │ 🔴 Critical │ Friend request domain layer entirely absent from frontend                               │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-3  │ UC-36          │ 🔴 Critical │ Friends & Requests tabbed screen entirely absent                                        │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-4  │ UC-38          │ 🔴 Critical │ Blocked Users screen absent (repo exists, no UI)                                        │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-5  │ UC-43          │ 🔴 Critical │ Leaderboard only in PostSession widget; no standalone screen                            │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-6  │ UC-18          │ 🟠 Major    │ triggerMatch user button violates explicit UC-18 prohibition                            │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-7  │ UC-34–38       │ 🟠 Major    │ Frontend SocialRepository uses obsolete follow/follower model                           │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-8  │ UC-15          │ 🟠 Major    │ Private intent toggle + friend-picker UI absent on Create Intent form                   │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-9  │ UC-19/23/36/39 │ 🟠 Major    │ AppEventBus handles only MATCH_FOUND; 8 FCM types unrouted                              │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-10 │ UC-14          │ 🟡 Medium   │ No auth gate on "Create Intent" CTA; no post-login hotspot restoration                  │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-11 │ UC-15/20       │ 🟡 Medium   │ Sender auto-accepted state not rendered on private invite proposal                      │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-12 │ UC-21          │ 🟡 Medium   │ Pass dialog text and post-pass navigation not differentiated by proposal type           │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-13 │ UC-20/23       │ 🟡 Medium   │ Chat button missing on Session Detail (invariant S-7 enforcement absent)                │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-14 │ UC-24          │ 🟡 Medium   │ Activation window button enforcement + SESSION_ACTIVATION_WINDOW_CLOSED handling absent │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-15 │ UC-32          │ 🟡 Medium   │ Incident report only wired to post-abort; must support ACTIVE, COMPLETED, NO_SHOW       │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-16 │ UC-39          │ 🟡 Medium   │ Notification tap dispatch table missing for all 8 new notification types                │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-17 │ UC-16/19       │ 🟡 Medium   │ Intent/Proposal expiry countdown timers not implemented                                 │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-18 │ UC-26          │ 🟡 Medium   │ "Complete Walk" 5-minute minimum countdown not enforced in TrackingScreenActivity       │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-19 │ UC-20          │ 🟢 Minor    │ No celebration animation on proposal double-accept (Case B)                             │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-20 │ UC-14          │ 🟢 Minor    │ Hotspot pin visual weight (openIntentCount) rendering unverified                        │
  ├────────┼────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────┤
  │ GAP-21 │ Appendix A     │ 🟢 Minor    │ Error handling may conflate HTTP status with error.code for domain errors               │
  └────────┴────────────────┴─────────────┴─────────────────────────────────────────────────────────────────────────────────────────┘

  ---
  End of Gap Analysis. Ready for implementation planning upon your approval.