# Execution Playbook: Main Flow v2

**Purpose:** This document is a context-switching guide. Each phase maps directly to the phases defined in `implementation_plan.md`. When you start a new chat session to implement a phase, copy the prompt in that phase's section verbatim and attach the listed input files.

**Ground rules for every session:**
- The AI role is: *Expert Spring Boot Backend Engineer (Java). Backend only — no Android/frontend code.*
- Grapuco MCP verification is mandatory before writing any code (detailed in each prompt).
- The AI must mark each checklist item in `implementation_plan.md` as it completes it.
- Each session ends by producing the Output artifacts listed for that phase.

---

## Phase 0 · Fix the State Machine

### Inputs

Attach all of the following at the start of the session:

| File | Purpose |
|---|---|
| `@docs/dev/main-flow-v2/implementation_plan.md` | Steps 0.1–0.6 with exact code changes and checklists |
| `@docs/dev/main-flow-v2/gap_analysis.md` | GAP-1, GAP-2, GAP-3, GAP-4, GAP-7 detail |
| `@docs/single-source-of-truth/lifecycle/state-transitions.md` | Authoritative state machine (source of truth) |
| `@docs/single-source-of-truth/lifecycle/invariants.md` | I-4, P-1, P-2, X-5 invariant rules |

---

### The Prompt

```
Role: You are an Expert Spring Boot Backend Engineer (Java). This task is STRICTLY backend — do NOT generate any Android/frontend code.

Context:
WalkMate is a Spring Boot 3.5 application using Supabase (PostgreSQL via JDBC) as its primary database. The codebase follows a layered architecture: domain → application → infrastructure → presentation.

I have provided four context documents (attached). Read all of them before doing anything else.

Task: Implement Phase 0 from implementation_plan.md — all steps 0.1 through 0.6 in order.

Step 1 — Grapuco MCP Verification (mandatory before writing any code):
Connect to the Grapuco MCP Server and run the following verifications:
1. Search for "IntentStatus" — confirm the current enum values in the Java file.
2. Search for "WalkIntent" — read the domain entity and confirm which methods exist (lock, unlock, consume, cancel).
3. Search for "MatchProposal" — confirm the version field is absent.
4. Search for "MatchingCommandService" — read the acceptProposal() method to confirm it checks isOpen() instead of MATCHING.
5. Search for "MatchProposalJdbcRepository" — confirm the UPDATE query has no version guard.

Report what you find for each item. Only proceed to implementation after this verification.

Step 2 — Implementation:
Implement steps 0.1 through 0.6 exactly as specified in implementation_plan.md. Follow this order strictly:
0.1 → 0.2 → 0.3 → 0.4 → 0.5 → 0.6
Each step depends on the previous. Do not skip ahead.

Constraints:
- Touch only the files listed in the "Files Touched Summary" table for Phase 0.
- Do not refactor, rename, or clean up anything outside the scope of each step.
- After completing each step, mark its checklist items in implementation_plan.md.

Step 3 — Output:
When all steps are done, produce the file docs/dev/main-flow-v2/phase_outputs/phase0_report.md as specified by the playbook.
```

---

### Outputs

The AI must produce the following file before the session ends:

**`docs/dev/main-flow-v2/phase_outputs/phase0_report.md`**

This file is the Input for Phase 1. It must contain:

```markdown
# Phase 0 Completion Report

**Date:** <ISO date>
**Branch:** <git branch name>

## Files Modified
<!-- List every file path that was changed, one per line. -->

## Verification Results (Grapuco)
<!-- Paste the Grapuco findings for each search performed before coding. -->

## Deviations from Plan
<!-- Any step where the actual implementation differed from implementation_plan.md.
     If none: write "None." -->

## New Domain State
<!-- Short summary of the state machine post-Phase-0.
     Example:
     - IntentStatus enum: OPEN, MATCHING, CONSUMED, CANCELLED, EXPIRED
     - WalkIntent.lock() / unlock() / consume() / expire(): implemented
     - MatchProposal.version: field present, OCC guard in JDBC UPDATE
     - acceptProposal() checks MATCHING (not OPEN) under pessimistic lock -->

## Open Issues / Blockers
<!-- Anything that blocked progress or needs review before Phase 1 starts.
     If none: write "None." -->
```

---

## Phase 1 · Restore Invariant Correctness

### Inputs

Attach all of the following at the start of the session:

| File | Purpose |
|---|---|
| `@docs/dev/main-flow-v2/implementation_plan.md` | Steps 1.1–1.5 with exact code changes and checklists |
| `@docs/dev/main-flow-v2/gap_analysis.md` | GAP-5, GAP-6, GAP-9, GAP-10, GAP-12 detail |
| `@docs/single-source-of-truth/lifecycle/state-transitions.md` | Authoritative state machine |
| `@docs/single-source-of-truth/lifecycle/invariants.md` | S-3, S-5, I-1, I-7 invariant rules |
| `@docs/dev/main-flow-v2/phase_outputs/phase0_report.md` | What Phase 0 actually changed (do not assume the plan was followed perfectly) |

---

### The Prompt

```
Role: You are an Expert Spring Boot Backend Engineer (Java). This task is STRICTLY backend — do NOT generate any Android/frontend code.

Context:
WalkMate is a Spring Boot 3.5 application using Supabase (PostgreSQL via JDBC). Phase 0 has already been completed — the state machine is fixed. The Phase 0 report (attached) documents exactly what was changed. Read it carefully; do not assume the plan was followed perfectly.

I have provided five context documents (attached). Read all of them before doing anything else.

Task: Implement Phase 1 from implementation_plan.md — steps 1.1 through 1.5.

Step 1 — Grapuco MCP Verification (mandatory before writing any code):
Connect to the Grapuco MCP Server and run the following verifications:
1. Search for "WalkSession" — confirm ACTIVATION_WINDOW_BEFORE and ACTIVATION_WINDOW_AFTER constants are still 15 min / 30 min (i.e., not yet fixed).
2. Search for "MatchingCommandService" — confirm PROPOSAL_TTL_MINUTES is still 30.
3. Search for "SessionController" — confirm no /complete endpoint exists yet.
4. Search for "WalkIntentJdbcRepository" — read hasOverlappingActiveIntent() and confirm it uses OPEN+CONSUMED (not OPEN+MATCHING).
5. Search for "CreateWalkIntentCommand" — confirm is_private, invitedFriendId, description fields are absent.

Cross-check findings against phase0_report.md. Report any discrepancies. Only proceed after this verification.

Step 2 — Implementation:
Steps 1.1, 1.2, and 1.3 are independent — implement them in any order (or note if one must block another given findings).
Steps 1.4 and 1.5 are independent of each other but both depend on Phase 0 being complete.
Follow the exact specifications in implementation_plan.md for each step.

For Step 1.4 (is_private / invitedFriendId / description), this is a cross-cutting change across six layers. Implement them in this order to avoid compilation breaks:
1.4a (domain) → 1.4b (command) → 1.4c (request DTO) → 1.4d (controller) → 1.4e (service) → 1.4f (JDBC) → 1.4g (SQL filter) → 1.4h (response DTO).

Constraints:
- Touch only the files listed in the "Files Touched Summary" table for Phase 1.
- Do not add error handling, logging, or comments beyond what already exists in the surrounding code style.
- After completing each step, mark its checklist items in implementation_plan.md.

Step 3 — Output:
When all steps are done, produce docs/dev/main-flow-v2/phase_outputs/phase1_report.md as specified by the playbook.
```

---

### Outputs

**`docs/dev/main-flow-v2/phase_outputs/phase1_report.md`**

This file is the Input for Phase 2. Same structure as the Phase 0 report:

```markdown
# Phase 1 Completion Report

**Date:** <ISO date>
**Branch:** <git branch name>

## Files Modified

## Verification Results (Grapuco)

## Deviations from Plan

## New Invariant State
<!-- Short summary of invariants now enforced.
     Example:
     - Proposal TTL: 5 minutes
     - Activation window: [start - 10 min, start + 15 min]
     - POST /sessions/{id}/complete: exposed
     - WalkIntent: is_private / invitedFriendId / description present in all layers
     - Overlap check: OPEN + MATCHING (CONSUMED removed) -->

## Open Issues / Blockers
```

---

## Phase 2 · Implement Missing Features

> **Session split advisory:** Phase 2 contains six independent work streams (2.1–2.6). Steps 2.1–2.3 (MongoDB + Chat) and steps 2.4–2.6 (Exclude List + Scheduler + FCM) are independent of each other. If the full phase is too large for one context window, run it as two sessions: **Phase 2A** (steps 2.1, 2.2, 2.3) then **Phase 2B** (steps 2.4, 2.5, 2.6), each producing their own report. The Phase 3 prompt accepts both reports as input.

---

### Phase 2A · MongoDB Atlas + Chat Room Lifecycle

#### Inputs

| File | Purpose |
|---|---|
| `@docs/dev/main-flow-v2/implementation_plan.md` | Steps 2.1–2.3 with full code and checklists |
| `@docs/dev/main-flow-v2/gap_analysis.md` | GAP-8, GAP-17 detail and architectural constraints |
| `@docs/single-source-of-truth/lifecycle/invariants.md` | P-3, S-7 invariant rules |
| `@docs/dev/main-flow-v2/phase_outputs/phase1_report.md` | Confirmed post-Phase-1 state |

---

#### The Prompt

```
Role: You are an Expert Spring Boot Backend Engineer (Java). This task is STRICTLY backend — do NOT generate any Android/frontend code.

Context:
WalkMate is a Spring Boot 3.5 application. Phases 0 and 1 are complete. We are now wiring MongoDB Atlas as a secondary store for the Chat layer. The Phase 1 report (attached) documents the current codebase state. Read all attached documents before doing anything else.

Architectural contract (non-negotiable — agreed in a prior architecture session):
- MongoDB is a derived, eventually-consistent store. PostgreSQL (Supabase) is the Source of Truth.
- MongoDB writes must NEVER be placed inside a Spring @Transactional boundary.
- Use TransactionSynchronizationManager.registerSynchronization() afterCommit() hooks to dispatch MongoDB writes only after the PostgreSQL transaction commits durably.
- Do NOT register a MongoTransactionManager bean — Spring @Transactional must continue to manage only the DataSourceTransactionManager.
- All MongoDB-specific types (@Document, MongoTemplate) are confined to the infrastructure layer only. The application layer interacts only via the ChatRoomRepository port.
- MongoChatRoomRepository.initRoom() uses upsert semantics (setOnInsert) to be idempotent and safe for retries.
- ChatRoom.status ("OPEN"/"CLOSED") is a convenience field — it is NOT the S-7 write-gate. The application enforces S-7 by checking WalkSession.status in PostgreSQL before allowing chat writes.

Task: Implement steps 2.1, 2.2, and 2.3 from implementation_plan.md.

Step 1 — Grapuco MCP Verification (mandatory before writing any code):
1. Search for "build.gradle" or inspect the dependency list — confirm spring-boot-starter-data-mongodb is NOT yet present.
2. Search for "application.properties" — confirm MONGODB_URI is NOT yet present.
3. Search for "ChatRoomRepository" — confirm it does NOT exist anywhere in the codebase.
4. Search for "MatchingCommandService" — read the acceptProposal() method and locate the exact line after walkSessionRepository.save(session) where the afterCommit hook will be inserted.
5. Search for "SessionCommandService" — locate completeSession(), cancelSession(), and abortSession() to find the exact lines after sessionRepository.save(session) where afterCommit hooks will be inserted.

Report all findings. Only proceed after verification.

Step 2 — Implementation order (strictly sequential — each step depends on the previous):
2.1 (MongoDB infra: build.gradle → application.properties → ChatRoomDocument → ChatRoomRepository port → MongoChatRoomRepository adapter)
  → 2.2 (afterCommit hook in MatchingCommandService.acceptProposal())
  → 2.3 (afterCommit hooks in SessionCommandService: completeSession, cancelSession, abortSession, handleExpiredSessions)

For every afterCommit hook: wrap the MongoDB call in try-catch. Log errors at ERROR level. Never rethrow. Never let a MongoDB failure propagate to the caller.

Constraints:
- Touch only files listed in the Phase 2 "Files Touched Summary" for steps 2.1–2.3.
- Do not add a MongoTransactionManager bean under any circumstances.
- After completing each sub-step, mark its checklist items in implementation_plan.md.

Step 3 — Output:
Produce docs/dev/main-flow-v2/phase_outputs/phase2a_report.md and docs/dev/main-flow-v2/adr/ADR-001-mongodb-chat.md as specified by the playbook.
```

---

#### Outputs

**`docs/dev/main-flow-v2/phase_outputs/phase2a_report.md`** — Same structure as prior phase reports, plus:

```markdown
## MongoDB Integration State
<!-- Confirm:
     - Dependency present in build.gradle: yes/no
     - MONGODB_URI / MONGODB_DATABASE in application.properties: yes/no
     - ChatRoomRepository port path: <full path>
     - MongoChatRoomRepository adapter path: <full path>
     - ChatRoomDocument collection name: <name>
     - afterCommit hook present in acceptProposal(): yes/no
     - afterCommit hooks present in completeSession() / cancelSession() / abortSession() / handleExpiredSessions(): yes/no -->
```

**`docs/dev/main-flow-v2/adr/ADR-001-mongodb-chat.md`**

```markdown
# ADR-001: MongoDB Atlas for Chat Room Lifecycle

**Date:** <ISO date>  
**Status:** Accepted  
**Deciders:** <name(s)>

## Context
WalkMate requires a chat room scoped to each WalkSession (invariant P-3). The relational
tables were removed in V101. MongoDB Atlas is the target store. The primary DB is PostgreSQL
(Supabase), managed via Spring JDBC and @Transactional.

## Decision
<!-- One paragraph: exactly what was decided. -->

## Transaction Boundary
<!-- Describe why MongoDB writes use afterCommit() hooks instead of being inside @Transactional.
     Describe the consequence if the afterCommit write fails (WalkSession exists, chat room absent — reconciliation path). -->

## Alternatives Considered
<!-- E.g., ChainedTransactionManager / distributed 2PC — and why rejected. -->

## Consequences
<!-- What becomes easier. What new failure modes exist and how they are handled. -->
```

---

### Phase 2B · Exclude List + Intent Expiry Scheduler + FCM Dual-Channel

#### Inputs

| File | Purpose |
|---|---|
| `@docs/dev/main-flow-v2/implementation_plan.md` | Steps 2.4–2.6 with full code and checklists |
| `@docs/dev/main-flow-v2/gap_analysis.md` | GAP-11, GAP-13, GAP-14, GAP-18, GAP-19 detail |
| `@docs/single-source-of-truth/lifecycle/invariants.md` | X-3, I-5 invariant rules |
| `@docs/dev/main-flow-v2/phase_outputs/phase1_report.md` | Confirmed post-Phase-1 state |
| `@docs/dev/main-flow-v2/phase_outputs/phase2a_report.md` | Confirmed post-Phase-2A state |

---

#### The Prompt

```
Role: You are an Expert Spring Boot Backend Engineer (Java). This task is STRICTLY backend — do NOT generate any Android/frontend code.

Context:
WalkMate is a Spring Boot 3.5 application. Phases 0, 1, and 2A are complete. Phase 2A wired MongoDB Atlas. Phase 2B covers three independent work streams: (1) per-intent exclude list, (2) intent expiry scheduler, and (3) FCM dual-channel notification dispatch. Read all attached documents before doing anything else.

Architectural contract for FCM (non-negotiable — agreed in a prior architecture session):
- NotificationPublisherImpl must dual-dispatch on every publish() call:
    Channel 1: persist Notification to PostgreSQL (always runs first).
    Channel 2: look up the user's FCM token via UserRepository, then call PushNotificationProvider.sendPush(). This is best-effort — exceptions are caught independently and never block Channel 1.
- The generic sendPush(String fcmToken, NotificationType type, Map<String,Object> payload) method must be added to the PushNotificationProvider port before implementing NotificationPublisherImpl changes.
- FcmNotificationProvider.sendPush() uses data-only FCM payloads and swallows all FirebaseMessagingException errors.
- After NotificationPublisherImpl is upgraded, the manual pushNotificationProvider.sendMatchFound() call in MatchingCommandService.findOrCreateProposal() must be removed — the existing notificationPublisher.publish() call already handles it.
- Firebase Admin SDK types remain confined to infrastructure/notification only.

Task: Implement steps 2.4, 2.5, and 2.6 from implementation_plan.md.
Steps 2.4, 2.5, and the 2.6 sub-sequence are independent of each other.
Within 2.6, the sub-steps must run in order: 2.6a → 2.6b → 2.6c → 2.6d.

Step 1 — Grapuco MCP Verification (mandatory before writing any code):
1. Search for "PushNotificationProvider" — confirm it only has sendMatchFound() and no sendPush() method yet.
2. Search for "NotificationPublisherImpl" — confirm it only calls notificationRepository.save() and has no FCM dispatch.
3. Search for "MatchingCommandService" — confirm the manual sendMatchFound() block still exists in findOrCreateProposal().
4. Search for "SessionScheduler" — read it to understand the existing @Scheduled pattern before creating IntentScheduler.
5. Search for "WalkIntentJdbcRepository" — confirm no findIntentsExpiringSoon() or findOverdueOpenIntents() methods exist yet.
6. Search for "walk_intent" table definition — confirm excluded_user_ids column is absent (V106 migration not yet applied).

Report all findings. Only proceed after verification.

Step 2 — Implementation:
Implement 2.4, 2.5, and 2.6 as specified in implementation_plan.md.
For 2.4: create migration V106 first, then the domain changes, then the JDBC changes, then the service changes.
For 2.5: model IntentScheduler after the existing SessionScheduler pattern (same @Scheduled style, same logging conventions).
For 2.6: follow the strict sub-step order (2.6a → 2.6b → 2.6c → 2.6d). Do not touch NotificationPublisherImpl until sendPush() is implemented on both the port and the adapter.

Constraints:
- Touch only files listed in the Phase 2 "Files Touched Summary" for steps 2.4–2.6.
- Do not add logging or comments that don't match the existing code style.
- After completing each step, mark its checklist items in implementation_plan.md.

Step 3 — Output:
Produce docs/dev/main-flow-v2/phase_outputs/phase2b_report.md and docs/dev/main-flow-v2/adr/ADR-002-fcm-dual-channel.md as specified by the playbook.
```

---

#### Outputs

**`docs/dev/main-flow-v2/phase_outputs/phase2b_report.md`** — Same structure as prior reports, plus:

```markdown
## FCM Dual-Channel State
<!-- Confirm:
     - sendPush() added to PushNotificationProvider: yes/no
     - sendPush() implemented in FcmNotificationProvider: yes/no
     - NotificationPublisherImpl dual-dispatches (DB + FCM): yes/no
     - Manual sendMatchFound() removed from MatchingCommandService: yes/no
     - PushNotificationProvider dependency removed from MatchingCommandService: yes/no
     - Notification types now auto-pushed via FCM (no call-site changes required):
         PROPOSAL_RECEIVED: yes/no
         SESSION_CONFIRMED: yes/no
         SESSION_ACTIVE: yes/no
         REVIEW_REQUESTED: yes/no -->

## Scheduler State
<!-- Confirm:
     - IntentScheduler created at: <full path>
     - @Scheduled interval: <value>
     - findIntentsExpiringSoon() query: <SQL snippet>
     - findOverdueOpenIntents() query: <SQL snippet>
     - V106 migration applied: yes/no -->
```

**`docs/dev/main-flow-v2/adr/ADR-002-fcm-dual-channel.md`**

```markdown
# ADR-002: FCM Dual-Channel Notification Dispatch

**Date:** <ISO date>  
**Status:** Accepted  
**Deciders:** <name(s)>

## Context
<!-- NotificationPublisher (domain port) previously only persisted to the DB.
     FCM was a manual, ad-hoc call in one service. SESSION_CONFIRMED etc. were never pushed. -->

## Decision
<!-- NotificationPublisherImpl dual-dispatches: DB persist then FCM push.
     PushNotificationProvider gains a generic sendPush() method. -->

## Channel Failure Isolation
<!-- Describe: DB failure does not block FCM. FCM failure does not roll back DB.
     Exception handling strategy for each channel. -->

## Alternatives Considered
<!-- E.g., keeping FCM calls explicit at each call site — and why rejected. -->

## Consequences
<!-- What becomes easier (zero call-site changes for new NotificationTypes).
     What to watch for (FCM token staleness, token refresh). -->
```

---

## Phase 3 · Polish

### Inputs

Attach all of the following at the start of the session:

| File | Purpose |
|---|---|
| `@docs/dev/main-flow-v2/implementation_plan.md` | Steps 3.1–3.2 with exact code and checklists |
| `@docs/dev/main-flow-v2/gap_analysis.md` | GAP-15, GAP-16 detail |
| `@docs/single-source-of-truth/lifecycle/invariants.md` | S-4, X-4 invariant rules |
| `@docs/dev/main-flow-v2/phase_outputs/phase2a_report.md` | Post-Phase-2A state |
| `@docs/dev/main-flow-v2/phase_outputs/phase2b_report.md` | Post-Phase-2B state |

---

### The Prompt

```
Role: You are an Expert Spring Boot Backend Engineer (Java). This task is STRICTLY backend — do NOT generate any Android/frontend code.

Context:
WalkMate is a Spring Boot 3.5 application. Phases 0, 1, 2A, and 2B are complete. Phase 3 covers two polish items: reputation penalty events for NO_SHOW and ABORTED, and including MATCHING intents in the active intent list. Read all attached documents before doing anything else.

Task: Implement steps 3.1 and 3.2 from implementation_plan.md. These steps are independent and can be implemented in any order.

Step 1 — Grapuco MCP Verification (mandatory before writing any code):
1. Search for "SessionCommandService" — read handleExpiredSessions() and abortSession() to confirm no penalty events are currently published.
2. Search for "SessionNoShowEvent" or "SessionAbortedEvent" — confirm neither class exists yet.
3. Search for "GamificationCommandService" — read it to understand the existing @EventListener pattern and trust_score update logic before adding new event handlers.
4. Search for "WalkIntentQueryService" — read listActiveIntents() and confirm its SQL uses status = 'OPEN' (not IN ('OPEN', 'MATCHING')).
5. Search for "WalkIntentJdbcRepository" — confirm the same single-status query.

Report all findings. Only proceed after verification.

Step 2 — Implementation:
3.1 (penalty events): Create SessionNoShowEvent and SessionAbortedEvent. Publish from handleExpiredSessions() and abortSession(). Add @EventListener handlers in GamificationCommandService for trust_score deduction. Follow the exact event structure and gamification patterns already present in the codebase for SessionCompletedEvent.
3.2 (listActiveIntents): Update the SQL in both WalkIntentQueryService and WalkIntentJdbcRepository to include MATCHING.

Constraints:
- Touch only files listed in the "Files Touched Summary" table for Phase 3.
- Mirror the existing event class structure (SessionCompletedEvent) for the new events — do not invent a different pattern.
- After completing each step, mark its checklist items in implementation_plan.md.

Step 3 — Output:
Produce docs/dev/main-flow-v2/phase_outputs/phase3_report.md (final completion report) as specified by the playbook.
```

---

### Outputs

**`docs/dev/main-flow-v2/phase_outputs/phase3_report.md`** — Final completion report. Same structure as prior reports, plus a final sign-off section:

```markdown
## Final Implementation Sign-Off

### All Gaps Closed
<!-- For each gap in gap_analysis.md, one line: GAP-N: CLOSED / OPEN (with reason if open). -->

### implementation_plan.md Checklist Status
<!-- Confirm all checklist items across all phases are marked complete.
     List any that remain unchecked with the reason. -->

### Regression Risk Areas
<!-- List any areas of the codebase that changed significantly and should be reviewed
     or tested before merging to main.
     Example:
     - MatchingCommandService.acceptProposal(): pessimistic lock + afterCommit hook added
     - NotificationPublisherImpl: dual-channel dispatch — verify FCM failure isolation
     - IntentScheduler: new @Scheduled job — verify it does not double-expire with SessionScheduler -->
```

---

## Appendix: Phase Output File Locations

All report files should be created in a subdirectory so the `main-flow-v2` directory stays organised:

```
docs/dev/main-flow-v2/
├── execution_playbook.md         ← this file
├── implementation_plan.md
├── gap_analysis.md
├── architecture-decision.md
├── adr/
│   ├── ADR-001-mongodb-chat.md           (produced by Phase 2A)
│   └── ADR-002-fcm-dual-channel.md       (produced by Phase 2B)
└── phase_outputs/
    ├── phase0_report.md                  (produced by Phase 0)
    ├── phase1_report.md                  (produced by Phase 1)
    ├── phase2a_report.md                 (produced by Phase 2A)
    ├── phase2b_report.md                 (produced by Phase 2B)
    └── phase3_report.md                  (produced by Phase 3)
```

> The `adr/` and `phase_outputs/` directories do not need to exist beforehand — the AI will create them when it writes the output files.
