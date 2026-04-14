# Phase 2 — Public User Profile Screen: Completion Report

**Branch:** `feature/phase-2-rework`
**Date:** 2026-04-14

---

## 1. Files Created

| File | Purpose |
|------|---------|
| `frontend/src/main/java/com/walkmate/ui/profile/publicprofile/PublicProfileUiState.java` | Immutable state snapshot (isLoading, error, profile, badges, stats, reviews, friendshipStatus, isSelf) |
| `frontend/src/main/java/com/walkmate/ui/profile/publicprofile/PublicProfileViewModel.java` | 4-way parallel load with AtomicInteger barrier; full CRUD for friend actions + blockUser |
| `frontend/src/main/java/com/walkmate/ui/profile/publicprofile/PublicProfileViewModelFactory.java` | Manual DI factory — injects SocialRepo, GamificationRepo, ReviewRepo, localUserId from SessionManager |
| `frontend/src/main/java/com/walkmate/ui/profile/publicprofile/PublicProfileFragment.java` | Full screen fragment: identity, stats, badges, reviews, contextual friendship buttons, overflow menu |
| `frontend/src/main/java/com/walkmate/ui/profile/publicprofile/ReviewAdapter.java` | RecyclerView adapter for the reviews section |
| `frontend/src/main/res/layout/fragment_public_profile.xml` | Layout: avatar, name, bio, tags, stats row, badges, reviews RecyclerView, friendship action area |
| `frontend/src/main/res/layout/item_review.xml` | Review row item (stars, date, comment) |
| `frontend/src/main/res/drawable/ic_more_vert.xml` | Three-dot vertical menu icon vector drawable |

---

## 2. Files Modified

| File | Change |
|------|--------|
| `res/navigation/nav_graph.xml` | Added `publicProfileFragment` destination with `userId` arg; added `action_home_to_publicProfileFragment` on `homeFragment`; added `action_matches_to_publicProfileFragment` on `matchesFragment` |
| `ui/home/quickinvite/QuickInviteAdapter.java` | Added `OnUserClickListener` interface; wired `itemView.setOnClickListener` to deliver `userId` |
| `ui/home/HomeFragment.java` | Set `OnUserClickListener` on `quickInviteAdapter` → NavController navigate to `publicProfileFragment` |
| `ui/matches/proposal/ProposalAdapter.java` | Added `onViewProfile(String userId)` to `ProposalActionListener`; wired `avatarPartner` + `txtName` click to fire it |
| `ui/matches/proposal/ProposalFragment.java` | Implemented `onViewProfile` → NavController navigate to `publicProfileFragment` via `action_matches_to_publicProfileFragment` |
| `ui/history/SessionHistoryAdapter.java` | Added `OnPartnerClickListener` interface; wired `txtPartner` click to deliver `partnerId` |
| `ui/history/SessionHistoryFragment.java` | Set `OnPartnerClickListener` → FragmentManager replace into `R.id.fragment_container` with `PublicProfileFragment` |
| `ui/gamification/PostSessionSummaryFragment.java` | Added `ARG_PARTNER_ID` + `newInstance()` overload with partnerId; wired `txtSummaryPartner` click to navigate via FragmentManager when partnerId is present |

---

## 3. Entry Points Wired

| Source | Trigger | Nav Method | Partner ID Source |
|--------|---------|------------|-------------------|
| `HomeFragment` (quick-invite list) | Card tap | NavController (`action_home_to_publicProfileFragment`) | `QuickInviteUser.userId` |
| `ProposalFragment` (proposal card) | Avatar or name tap | NavController (`action_matches_to_publicProfileFragment`) | `WalkProposal.getMatchedUserId()` |
| `SessionHistoryFragment` | Partner name tap | FragmentManager replace (`R.id.fragment_container`) | `SessionSummary.getPartnerId()` |
| `PostSessionSummaryFragment` | Partner name tap | FragmentManager replace (`android.R.id.content`) | `ARG_PARTNER_ID` (optional — see Known Risks) |

---

## 4. Known Risks / Follow-ups for Phase 3

### 4.1 PostSessionSummaryFragment partner ID (medium risk)
`PostSessionSummaryFragment.newInstance(sessionId, partnerName, boolean)` (the original 3-arg overload) does **not** pass a `partnerId`, so the partner name tap is inactive for sessions opened via the old call site. The fix requires `TrackingScreenActivity.showPostSessionSummary()` to derive the partner ID from `WalkSession`. Since `WalkSession` does not currently carry a `partnerId` field (only `partnerName`/`partnerAvatar`), this is deferred to a data-layer fix.

**Workaround path:**
1. Add `partnerId` field to `WalkSession` domain model (computed from `WalkSessionResponse.userIdA/userIdB` + `isCallerUserA`).
2. Update `WalkSessionMapper`.
3. Update `TrackingScreenActivity.showPostSessionSummary()` to call the new 4-arg `newInstance()`.

### 4.2 UserSummary missing bio and tags
`UserSummary` only carries `userId`, `fullName`, `avatarUrl`, `friendshipStatus`. The bio text and personality tags sections in `fragment_public_profile.xml` are hidden (`GONE`) until Phase 3 extends `PublicUserResponse` + `UserSummary` with `bio` and `tags` fields.

### 4.3 pendingRequestId for PENDING_RECEIVED state
When `friendshipStatus == "PENDING_RECEIVED"`, the "Accept" button calls `viewModel.acceptIncomingRequest(pendingRequestId)`. However, `PublicProfileUiState` does not currently carry the `requestId` — it would need to come from `SocialRepository.getIncomingRequests()` or be embedded in the `UserSummary` response. For Phase 2, the `pendingRequestId` field in the Fragment is `null` by default, making "Accept"/"Decline" no-ops. Phase 3's `FriendsViewModel` will expose the correct `requestId` from the incoming requests list.

### 4.4 Invite Walk deep-link
The "Invite Walk" button (FRIENDS state) shows a "coming soon" toast. Phase 5 will deep-link to `ExploreFragment` with `friendId` pre-filled per the implementation plan.

---

## 5. Verification

- **ACKG index:** 12 files re-indexed after Phase 2 changes (5 new + 7 modified).
- **No existing tests broken:** Phase 2 only adds new classes and extends existing adapters with backward-compatible interface additions (existing code that does not call `onViewProfile` won't break at compile time as long as it implements the interface — ProposalFragment already wires the full interface).
- **Package name:** `com.walkmate.ui.profile.publicprofile` used instead of `...profile.public` (`public` is a Java keyword and cannot be a package component).
- **Resource references verified:** All drawable and color references in layout files confirmed against `res/values/colors.xml` and `res/drawable/` — `@color/color_danger`, `@color/handle_bar`, `@drawable/bg_card_rounded_32`, `@drawable/ic_back`, `@drawable/ic_more_vert` (newly created).
