# WalkMate - Trace Current Matching Logic & Unused Form Fields

## 1) Scope & Source Notes

- Requested generated path was checked: `frontend/build/intermediates/java_res/debug/processDebugJavaRes/out/com/walkmate/ui/explore/`.
- In current workspace snapshot, that folder contains `createintent/` but it is empty.
- So this report traces the **active runtime flow** from source files that actually build and run the screen:
  - Frontend form + submit flow in `frontend/src/main/res/layout/fragment_explore.xml` and `frontend/src/main/java/com/walkmate/ui/explore/ExploreFragment.java`.
  - Backend create-intent + matching flow in `backend/src/main/java/com/walkmate/presentation/controller/walkintent/WalkIntentController.java` and matching services/repositories.

## 2) Current "AI Matching" at Abstraction Level

## 2.1 High-level architecture

Current production matching is **rule-based (MVP)**, not an AI model yet.

- Matching entrypoints:
  - `POST /api/v1/intents` (create intent, then inline match attempt for public intents).
  - `POST /api/v1/intents/{intentId}/match` (manual trigger to find/create proposal).
- Core orchestrators:
  - `WalkIntentController` -> `WalkIntentCommandService` (create + optional inline match)
  - `WalkIntentController` -> `MatchingCommandService` (trigger match)
- Matching strategy interface:
  - `MatchingStrategy` (2-stage pipeline)
  - Active implementation: `RuleBasedMatchingStrategy` (`@Primary`)

## 2.2 Two-stage matching pipeline (current)

### Stage 0 - Create intent

User submits form -> backend converts `(date + time_start/time_end)` to `Instant` in `Asia/Ho_Chi_Minh` timezone -> saves `walk_intent`.

For public intent:
- Save intent as `OPEN`.
- Best-effort inline call `matchingCommandService.findOrCreateProposal(intentId, userId)`.

For private invite:
- No public search.
- Creates sender + receiver intents (both `MATCHING`), creates proposal directly, auto-accepts sender side.

### Stage 1 - Hard filtering (DB + exclusion rules)

Implemented via `walkIntentRepository.findOpenCandidates(...)` + block filter in `RuleBasedMatchingStrategy`.

Candidate must satisfy:
- same `hotspot_id`
- status = `OPEN`
- not same user
- private visibility rule: `(is_private = false OR invited_friend_id = callerId)`
- caller not in candidate's `excluded_user_ids`
- age range intersection:
  - `candidate.age_min <= my.age_max`
  - `candidate.age_max >= my.age_min`
- minimum overlap >= 15 minutes:
  - `candidate.start < my.end - 15m`
  - `candidate.end > my.start + 15m`
- plus in-memory social block pruning:
  - remove users I blocked or who blocked me (`getBlockedAndBlockerIds`)

### Stage 2 - Scoring and selection

Current score only uses overlap duration:

`score = overlapMinutes * 1`

Then choose max score candidate.

No active AI features yet (still TODO comments):
- shared tags
- social/follow bonus
- trust score bonus
- no-show penalty

## 3) Matching Form Fields (Current Runtime Form)

From `fragment_explore.xml` + `ExploreFragment.submitCreateIntent()`:

- Hotspot (selected on map/chip)
- Date (`rowDatePicker` / `txtSelectedDate`)
- Time range (`sliderTime`)
- Age range (`sliderAge`)
- Gender preference (`chipGroupGender`)
- Tags (`chipGroupTags`)
- Private walk switch (`switchPrivateWalk`)
- Invited friend (`rowFriendPicker` / `txtSelectedFriend`)
- Private hint text indicates public filters not used in private mode

## 4) Field Usage Matrix: Form -> API -> Matching Algorithm

| Form Field | Sent from FE | Accepted by BE CreateWalkIntentRequest | Persisted in walk_intent | Used in current matching algorithm? | Notes |
|---|---:|---:|---:|---:|---|
| Hotspot | Yes | Yes | Yes | Yes | Stage 1 hard filter `hotspot_id` |
| Date | Yes | Yes | Indirect | Yes (indirect) | Converted with time to `timeWindowStart/End`; not stored as raw date |
| Time Start/End | Yes | Yes | Yes | Yes | Stage 1 overlap filter + Stage 2 overlap scoring |
| Age Min/Max | Yes | Yes | Yes (`matching_constraints`) | Yes (public) | Stage 1 age intersection filter |
| Gender | No (not read in submit) | No | No | No | UI exists but no binding in `submitCreateIntent()` |
| Tags | Yes | **No** (DTO has no `tags`) | No | No | Collected in FE and passed over network, but BE intent DTO/model/mapper do not carry tags |
| Private Switch (`is_private`) | Yes | Yes | Yes | Not in scoring | Changes flow to private-invite path (bypass candidate search/scoring) |
| Invited Friend | Yes (if private) | Yes | Yes | Not in scoring | Used to target private invite, not public ranking score |
| Description | Not exposed in current runtime form | Yes | Yes | No | Supported by API/domain, but no current input control in active Explore form |

## 5) Fields Not Used in Matching Algorithm (Answer to your question)

In the **current active app flow**, fields not used by algorithm scoring/filtering are:

1. `Gender` (UI present, but not even read/sent from submit logic).
2. `Tags` (read + sent by FE, but backend create-intent DTO/domain/matching pipeline do not use it).
3. `Description` (supported in API/domain but not part of current runtime form, and not used for matching).

And in **private walk mode**, these are effectively not part of matching decision:

4. `Age Min/Max` for partner selection (private flow directly targets invited friend, no open-candidate search).
5. `Tags` and `Gender` are also irrelevant in private mode (UI already hints this behavior).

## 6) Extra finding: Legacy/unused form variant

There is another layout file `frontend/src/main/res/layout/fragment_create_intent.xml` containing a **Duration** chip group (`chipDur15/chipDur30/chipDur60`).

- This Duration control is not wired into `ExploreFragment.submitCreateIntent()`.
- Current active runtime layout (`fragment_explore.xml`) does not include that Duration block.
- So Duration is currently a legacy/unwired preference, not part of the active matching flow.

## 7) Practical conclusion

If your goal is to evolve to true AI matching, the largest current gaps are:

- Persisting and consuming intent-level tags/gender preference in backend intent model.
- Activating scoring features now marked TODO in `RuleBasedMatchingStrategy`.
- Defining an explicit contract so FE form fields and BE matching inputs are always 1:1 aligned.
