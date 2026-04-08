# Phase 8 Report — Proposal Negotiation Feature
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 4.1 (proposal fields in UI), 4.2 (Case A vs Case B), 4.3 (premature session navigation), 4.4 (countdown on proposals), 4.5 (partner profile enrichment)

---

## Layout Changes — `item_proposal_card.xml`

| Addition | Position | ID |
|---|---|---|
| `CountdownTimerView` | Top-right of partner info row | `@+id/countdownTimer` |
| `TextView` (waiting overlay) | Between accept/pass buttons and cancel button | `@+id/txtWaitingOverlay` |

- `CountdownTimerView` uses `wm_urgentThresholdSec="60"` (1 min urgent threshold) since proposal TTL is 5 minutes total.
- Waiting overlay `visibility="gone"` by default, `visibility="visible"` in Case A.

String added to `strings.xml`:
- `proposal_waiting_partner` — "Waiting for partner to accept…"

---

## ProposalAdapter Changes

### New method on `ProposalActionListener`
```java
void onProposalExpired();
```
Wired in `ProposalFragment`: `onProposalExpired` → `matchesViewModel.loadAll()`.

### ViewHolder — new fields
- `CountdownTimerView countdown`
- `TextView txtWaitingOverlay`

### `bind()` — Case A / Case B waiting overlay
```java
countdown.startCountdown(proposal.getExpiresAt());
countdown.setOnExpiredListener(() -> actionListener.onProposalExpired());

if (proposal.isAcceptedByMe() && proposal.getStatus() == WalkProposal.Status.PENDING) {
    // Case A: show waiting, hide Accept + Pass
    txtWaitingOverlay.setVisibility(VISIBLE);
    btnAccept.setVisibility(GONE);
    btnPass.setVisibility(GONE);
} else {
    txtWaitingOverlay.setVisibility(GONE);
    btnAccept.setVisibility(VISIBLE);
    btnPass.setVisibility(VISIBLE);
    // ... wire click listeners
}
btnCancelProposal always wired (user can cancel from either state)
```

### `onViewRecycled()` — countdown cancel
```java
@Override
public void onViewRecycled(@NonNull ViewHolder holder) {
    super.onViewRecycled(holder);
    holder.countdown.cancelCountdown();
}
```

---

## WalkProposal Domain Model

Added `withMatchedUserName(String enrichedName)` copy method:
```java
public WalkProposal withMatchedUserName(String enrichedName) {
    return new WalkProposal(proposalId, intentId, matchedUserId, enrichedName, ...);
}
```
Keeps the model immutable; enrichment in ViewModel creates new instances.

---

## MatchesViewModel Changes

### `acceptProposal()` — Case A vs Case B (gaps 4.2, 4.3)
```java
if (result.isConfirmed()) {
    // Case B: session_id non-null — reload + navigate to Session tab
    loadAll(() -> scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION));
} else {
    // Case A: I accepted but partner has not — update in-place, show waiting overlay
    updateProposalInPlace(result);
}
```
`isConfirmed()` = `Status.CONFIRMED && sessionId != null` — spec-correct guard.

### `updateProposalInPlace(WalkProposal updated)` (gap 4.3)
Replaces exactly one proposal entry in `uiState` without a full reload. Eliminates the premature session-tab navigation on partial acceptance.

### Partner name enrichment (gap 4.5)
New fields:
```java
private final UserProfileRepository userProfileRepository;
private final Map<String, UserProfile> profileCache = new HashMap<>();
```

`enrichProposalPartnerNames(List<WalkProposal>)` — called after `uiState.postValue(...)` in both `loadAll()` and `reloadIntentsAndProposals()`:
- Cache hit: calls `rebuildUiStateWithEnrichedProposals()` immediately.
- Cache miss: fires `userProfileRepository.getProfile(uid, ...)`, caches the result, then calls `rebuildUiStateWithEnrichedProposals()`.
- Errors fail silently — adapter falls back to `matchedUserId` placeholder.

`rebuildUiStateWithEnrichedProposals()` — reads current `uiState`, replaces each cached proposal via `withMatchedUserName()`, re-posts.

### New MatchesViewModel constructor signature
```java
public MatchesViewModel(
    WalkIntentRepository intentRepository,
    WalkProposalRepository proposalRepository,
    WalkSessionRepository sessionRepository,
    UserProfileRepository userProfileRepository)
```

---

## MatchesViewModelFactory Changes

Added `UserProfileRepositoryImpl` as the fourth constructor argument:
```java
return (T) new MatchesViewModel(
    new WalkIntentRepositoryImpl(application),
    new WalkProposalRepositoryImpl(application),
    new WalkSessionRepositoryImpl(application),
    new UserProfileRepositoryImpl(application));
```

---

## Gap Closure Summary

| Gap | Description | Status |
|---|---|---|
| 4.1 | `WalkProposal` domain fields (`expiresAt`, `myAcceptanceStatus`, `meetingLat/Lng`) wired to UI | ✅ Closed |
| 4.2 | `acceptProposal()` distinguishes Case A (waiting) vs Case B (confirmed) | ✅ Closed |
| 4.3 | Session navigation guarded — only fires when `sessionId` is non-null | ✅ Closed |
| 4.4 | 5-minute countdown timer on proposal cards | ✅ Closed |
| 4.5 | Partner profile enriched via `GET /api/v1/users/{uid}` with in-memory cache | ✅ Closed |
