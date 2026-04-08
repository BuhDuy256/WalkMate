# Phase 5 Report — Repository Interfaces
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 2.7 (session complete/history/route/report), 2.8 (intent description param), 2.9 (periodic sync)

---

## WalkSessionRepository — New Methods Added

```java
void completeSession(String sessionId, DomainCallback<WalkSession> callback);
void getSessionHistory(DomainCallback<List<SessionSummary>> callback);
void getSessionRoute(String sessionId, DomainCallback<SessionRoute> callback);
void reportSession(String sessionId, String reportedUserId,
                   String reason, String evidenceUrl,
                   DomainCallback<Void> callback);
```

All four signatures follow the existing convention: `void`, `DomainCallback` last, no return value.

---

## WalkIntentRepository — Updated `createIntent()` Signature

`String description` inserted after `invitedFriendId` and before the `DomainCallback` parameter:

```java
void createIntent(String hotspotId, String date, float timeStart, float timeEnd,
                  int ageMin, int ageMax, List<String> tags,
                  boolean isPrivate, String invitedFriendId,
                  String description,
                  DomainCallback<WalkIntent> callback);
```

**Full parameter list for Phase 6 reference:**
| # | Name | Type |
|---|---|---|
| 1 | hotspotId | String |
| 2 | date | String |
| 3 | timeStart | float |
| 4 | timeEnd | float |
| 5 | ageMin | int |
| 6 | ageMax | int |
| 7 | tags | List\<String\> |
| 8 | isPrivate | boolean |
| 9 | invitedFriendId | String |
| 10 | description | String (**NEW**) |
| 11 | callback | DomainCallback\<WalkIntent\> |

---

## TrackingRepository — New Method Added

```java
// Triggered by the 30-second periodic scheduler. Syncs all unsynced points
// regardless of batch size threshold.
void triggerPeriodicSync(String sessionId);
```

No `DomainCallback` — fire-and-forget; result is observed via the existing `LiveData` stream.

---

## WalkProposalRepository — `acceptProposal()` Callback Type Changed

| | Before | After |
|---|---|---|
| Signature | `void acceptProposal(String proposalId, DomainCallback<WalkSession> callback)` | `void acceptProposal(String proposalId, DomainCallback<WalkProposal> callback)` |
| Unused import | `import com.walkmate.domain.walksession.WalkSession;` | **Removed** |

**Rationale:** The accept endpoint returns the updated proposal (status → ACCEPTED), not a session. The session is only created when both parties accept; it arrives via the existing session-fetch flow.

---

## `acceptProposal` Call Sites to Update in Phase 6

These call sites pass `DomainCallback<WalkSession>` and must be updated to `DomainCallback<WalkProposal>`:

| File | Location |
|---|---|
| `data/repository/WalkProposalRepositoryImpl.java` | Line 77 — `public void acceptProposal(String proposalId, DomainCallback<WalkSession> callback)` |
| `ui/matches/MatchesViewModel.java` | Line 261 — `proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkSession>() {` |

**Also note:** `MatchesViewModel.acceptProposal()` (line 260) processes the callback result as a `WalkSession` — that logic will need to be updated to handle `WalkProposal` instead.
