# Phase 7 — Optimization Decisions

## 1. Unified `ProposalActionListener` replaces two separate interfaces

**Decision:** Collapsed `OnPassClickListener` + `OnAcceptClickListener` into a single `ProposalActionListener` with three methods (`onPass`, `onAccept`, `onCancel`).

**Trade-off:** Breaking change for `ProposalFragment` (which used both separate setters), but the adapter now has a single attachment point instead of two. Any future action (e.g., "Report") is added to one interface, not a fourth setter.

---

## 2. Cancel button placed below Pass/Accept as a full-width text button

**Decision:** Rather than cramming three buttons into one horizontal row (which becomes unusable at 360dp width), Cancel is rendered as a full-width `TextButton` below the Pass/Accept row.

**Why:** Pass and Accept are primary peer actions of equal visual weight. Cancel is a destructive secondary action — lower visual priority matches lower tap frequency. The `color_danger` (`#E53935`) text colour signals severity without an aggressive filled background.

---

## 3. `cancelProposal` triggers `loadAll()` instead of optimistic remove

**Decision:** On successful cancellation, `MatchesViewModel.cancelProposal()` calls `loadAll()` rather than doing an optimistic list splice like `passProposal()`.

**Why:** Per the backend spec, `DELETE /api/v1/proposals/{id}` also closes the parent WalkIntent. That intent lives in the Finding sub-tab, which is a completely different data set. A partial optimistic update would leave a stale intent visible in Finding. A full `loadAll()` keeps all three sub-tabs consistent at the cost of one extra network round-trip — acceptable for a low-frequency destructive action.

---

## 4. `cancelProposal` added to the repository layer fully (interface → impl → API)

**Decision:** Even though the backend endpoint `DELETE /api/v1/proposals/{id}` is not yet deployed, the full chain (domain interface → repository impl → Retrofit service) was wired so that the frontend compiles and the UI is ready. The endpoint will return HTTP 404 until the backend team ships it, which surfaces a meaningful error toast rather than a crash.

**Risk note:** Coordinate with the backend team before releasing this to users. The plan explicitly flags this requirement.

---

## 5. Vietnamese comments translated in-place, not extracted

**Decision:** The six Vietnamese code comments in `ExploreFragment.java` were translated to English directly at their original locations rather than being moved or restructured.

**Why:** The comments describe non-obvious BottomSheetBehavior mechanics. Keeping them close to the code they annotate is more maintainable than moving them to a separate doc. The translations preserve the original intent precisely.
