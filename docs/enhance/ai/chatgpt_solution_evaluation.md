# Validation of AI Matching Solutions

This report evaluates the feasibility of the solutions proposed by ChatGPT to fix the AI Matching bugs and misbehaviors in the WalkMate application. The evaluation is based on a direct review of the existing backend codebase (Controllers, Services, Repositories, Domain models, and Database schema).

## Executive Summary
ChatGPT's proposed "4-layer defense" architecture is **highly feasible and strongly recommended**. It perfectly aligns with the existing DDD-lite architecture of the application. Most of the proposed fixes require only minor adjustments to existing logic, while a few require new tables or scheduled jobs. 

Here is the detailed technical validation for each phase.

---

## Phase 1: High Priority / Easy Wins (Bug Fixes)

### 1. Clamp scores to `[0, 100]`
**Feasibility: VERY HIGH**
- **Codebase Context:** `AiWeightedMatchingStrategy.scoreTrust()` currently calculates `trustScore / 10.0`. If a user's trust score exceeds 1000, the score breaks the 100 ceiling. 
- **Action:** Add a simple `Math.min(candidateTrustScore / 10.0, 100.0)` wrapper. This ensures the `MAX_WEIGHT_CAP` rule remains mathematically sound.

### 2. Time Overlap Ratio vs. Fixed 60 mins
**Feasibility: HIGH**
- **Codebase Context:** `AiWeightedMatchingStrategy.scoreTime()` limits overlap to `60.0` minutes. 
- **Action:** The `WalkIntent` object contains `timeWindowStart` and `timeWindowEnd`. We can easily calculate the desired duration of both users, find the minimum, and calculate the ratio `overlapMinutes / desiredDuration * 100.0`. This is a straightforward logic change in the scoring strategy.

### 3. Lower Empty Profile Score
**Feasibility: VERY HIGH**
- **Codebase Context:** `AiWeightedMatchingStrategy.scoreTags()` currently explicitly returns `50.0` if both users have empty tags.
- **Action:** Change the `50.0` constant to `20.0` as proposed. This stops rewarding incomplete profiles without requiring structural changes.

### 4. Lock Private Intents from Public Matching
**Feasibility: HIGH**
- **Codebase Context:** The `walk_intent` table has an `is_private` boolean flag. 
- **Action:** We must verify that `WalkIntentRepository.findOpenCandidates()` explicitly filters `WHERE is_private = false`. If not, adding it is a trivial but critical fix to prevent private intents from leaking.

### 5. Prevent Re-match (Exclusion List)
**Feasibility: MEDIUM-HIGH**
- **Codebase Context:** The app currently only checks the `block_relation` table to exclude candidates. Rejecting a match does not block them, so they can immediately be re-matched.
- **Action:** Creating a `match_exclusion` table (or similar temporary caching) is highly feasible. The check can be seamlessly added to the Hard Filter stage in `AiWeightedMatchingStrategy.findCandidates()`.

---

## Phase 2: Medium Priority / AI Learning Fixes

### 6. Floor for `weightTimeOverlap`
**Feasibility: VERY HIGH**
- **Codebase Context:** `MatchingPreference.normalize()` handles the distribution of weights and already contains logic for a `MAX_WEIGHT_CAP` (0.70). 
- **Action:** Adding a `MIN_TIME_WEIGHT` (e.g., 0.25) to the normalization logic is entirely feasible and will prevent the time overlap from decaying to zero due to continuous review/report bumps.

### 7. Prevent Review Spam / Click-through Bias
**Feasibility: HIGH**
- **Codebase Context:** `AiTrainingService.trainWeightsFromReview()` currently loops over all submitted tags and adds `+0.05` for *each* tag.
- **Action:** Update the loop to use a boolean flag (`hasInterestTag`, `hasBehaviorTag`) and apply a single `+0.05` cap per category per review, regardless of how many tags were selected. 

### 8. Provisional vs Confirmed Report Signals
**Feasibility: MEDIUM**
- **Codebase Context:** `AiTrainingService.trainWeightsFromReport()` currently applies a massive `+0.10` or `+0.15` bump unconditionally upon report creation.
- **Action:** Implementing provisional vs. confirmed signals requires moving the training logic. The `session_report` table already supports admin resolution (`resolved_by`, `status`). We would move the strong weight adjustment to the Admin resolution service rather than the initial report submission service. Adding a `preference_training_event` log table is an excellent idea for auditability and would be a standard Spring Data JPA implementation.

---

## Phase 3: Architectural Guardrails

### 9. Lifecycle Guard (Matching Orchestrator)
**Feasibility: HIGH**
- **Codebase Context:** The database tables (`walk_intent`, `match_proposal`) already have a `version` column configured for optimistic locking (`CHECK (version >= 0)`).
- **Action:** Wrapping the match selection and proposal creation inside a Spring `@Transactional` block with JPA `@Version` annotations ensures that race conditions (e.g., intent cancelled while AI is scoring) will safely throw an `OptimisticLockException` rather than creating a phantom match.

### 10. Proposal Timeout Job
**Feasibility: HIGH**
- **Codebase Context:** The `match_proposal` table has an `expires_at` column.
- **Action:** Implementing a Spring `@Scheduled` cron job to sweep for expired proposals and transition their status is a standard pattern and highly feasible. 

## Conclusion
ChatGPT's solution demonstrates a solid understanding of the flaws in a simple weighted scoring model. Better yet, **all proposed changes map perfectly onto the existing WalkMate DB schema and Spring Boot architecture**. The fixes can be implemented incrementally without requiring a rewrite of the matching engine.
