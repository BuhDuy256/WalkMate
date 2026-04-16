# Phase 7 Summary — Proposal Cancel + i18n Fixes

## Implementation Checklist

- [x] **7a** — Added `btnCancelProposal` to `item_proposal_card.xml` (full-width TextButton, `color_danger` text)
- [x] **7a** — Replaced two separate adapter listener interfaces with unified `ProposalActionListener` (`onPass`, `onAccept`, `onCancel`)
- [x] **7a** — Wired `btnCancelProposal` click → `actionListener.onCancel(proposalId)` in `ProposalAdapter.ViewHolder`
- [x] **7b** — Added `cancelProposal(String proposalId)` to `MatchesViewModel`; on success calls `loadAll()` to refresh all three sub-tabs
- [x] **7b** — Added `cancelProposal` to `WalkProposalRepository` interface (domain layer)
- [x] **7b** — Implemented `cancelProposal` in `WalkProposalRepositoryImpl` (data layer)
- [x] **7b** — Added `@DELETE("api/v1/proposals/{proposalId}")` to `ProposalApiService`
- [x] **7c** — Fixed 10 Vietnamese strings in `res/values/strings.xml` (gender, tags, quick-invite header)
- [x] **7d** — Translated 6 Vietnamese comments in `ExploreFragment.java` to English (lines 314, 322, 323, 329, 628, 631)

---

## Files Modified

| File | Change |
|---|---|
| `frontend/src/main/res/layout/item_proposal_card.xml` | Added `btnCancelProposal` TextButton |
| `frontend/src/main/res/values/strings.xml` | Fixed 10 Vietnamese strings + added `btn_cancel_proposal` |
| `frontend/src/main/java/.../proposal/ProposalAdapter.java` | Unified `ProposalActionListener`; added cancel button binding |
| `frontend/src/main/java/.../proposal/ProposalFragment.java` | Updated to use `setProposalActionListener`; wired `onCancel` |
| `frontend/src/main/java/.../domain/walkproposal/WalkProposalRepository.java` | Added `cancelProposal` method signature |
| `frontend/src/main/java/.../data/repository/WalkProposalRepositoryImpl.java` | Implemented `cancelProposal` via Retrofit DELETE |
| `frontend/src/main/java/.../remote/api/ProposalApiService.java` | Added `@DELETE` endpoint |
| `frontend/src/main/java/.../matches/MatchesViewModel.java` | Added `cancelProposal()` method |
| `frontend/src/main/java/.../explore/ExploreFragment.java` | Replaced 6 Vietnamese comments with English |

---

## States / Contracts the Next Phase Must Know

### API Contract (backend-dependent)
- `DELETE /api/v1/proposals/{proposalId}` must return `ApiResponse<Void>` with `success: true`
- On success the backend removes the proposal **and** closes the parent WalkIntent
- **Not yet deployed** — coordinate with backend before releasing

### ViewModel contract
- `MatchesViewModel.cancelProposal(String proposalId)` — public method, safe to call from any child fragment
- On success → triggers full `loadAll()` which refreshes Finding, Proposal, and Session sub-tabs simultaneously
- On error → posts error string to `uiState.error`; cleared by `consumeError()`

### Adapter interface change (breaking for Phase 8+ if referencing old interfaces)
- Old: `OnPassClickListener`, `OnAcceptClickListener` — **removed**
- New: `ProposalAdapter.ProposalActionListener` — single interface with `onPass`, `onAccept`, `onCancel`
- Set via: `adapter.setProposalActionListener(listener)`

### i18n
- `gender_male`, `gender_female`, `gender_any`, all six `tag_*` strings, and `home_quick_invite_header` are now English
- Default locale (`res/values/strings.xml`) is the only locale — no `values-vi` override exists yet
