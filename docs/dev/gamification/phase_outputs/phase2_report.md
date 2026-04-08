# Phase 2 Output Report — Badge Evaluation Service

**Date:** 2026-04-08
**Branch:** `implement/realtime`
**Implemented by:** Claude (Sonnet 4.6)

---

## 1. ACKG Pre-flight Results

### 1.1 Inline badge logic location in `GamificationCommandService`

`get_file_outline` confirmed `rewardUser` spans lines 103–128. The three-line inline block:

| Line | Statement |
|------|-----------|
| 113–119 | `UserStats stats = new UserStats(userId, ...)` |
| 121 | `Set<String> existingBadges = badgeRepository.findBadgeNamesByUserId(userId)` |
| 122 | `List<Badge> newBadges = BadgePolicy.evaluateEarned(stats, existingBadges)` |
| 124–127 | `if (!newBadges.isEmpty()) { badgeRepository.saveAll(...); log.info(...); }` |

### 1.2 Insertion point in `ReviewCommandService`

`get_file_outline` confirmed `submitReview` spans lines 43–86. The save call is at:

- **Line 82:** `reviewee.applyTrustScore(newScore);`
- **Line 83:** `userRepository.save(reviewee);`
- **Insertion point:** immediately after line 83.

### 1.3 `UserBadgeRepository` usages

Direct file inspection confirmed `UserBadgeRepository` is injected and used **only** in `GamificationCommandService` (field declaration line 43; call sites lines 121 and 125). No other class references it. Safe to remove from `GamificationCommandService` after delegation.

### 1.4 `BadgePolicy` usages

Direct file inspection confirmed `BadgePolicy.evaluateEarned` is called **only** in `GamificationCommandService.rewardUser` (line 122). No other class references `BadgePolicy`.

### 1.5 `BadgeEvaluationService` existence check

Confirmed: no class named `BadgeEvaluationService` existed anywhere in the codebase prior to this phase.

---

## 2. Files Created

```
backend/src/main/java/com/walkmate/application/gamification/BadgeEvaluationService.java  (new)
```

**Structure:**

| Element | Detail |
|---------|--------|
| Annotations | `@Slf4j`, `@Service`, `@RequiredArgsConstructor` |
| Dependency | `UserBadgeRepository badgeRepository` (single field) |
| Public method | `evaluateAndAward(User user)` |
| Behaviour | Builds `UserStats` from `user`, loads existing badge names, calls `BadgePolicy.evaluateEarned`, persists new badges via `badgeRepository.saveAll`, logs the award |
| Idempotency | Relies on `UNIQUE(user_id, badge_name)` + `ON CONFLICT DO NOTHING` in `UserBadgeRepository.saveAll` |

---

## 3. Files Modified

### `GamificationCommandService.java`

**Removed:**
- Import `com.walkmate.domain.gamification.Badge`
- Import `com.walkmate.domain.gamification.BadgePolicy`
- Import `com.walkmate.domain.gamification.UserBadgeRepository`
- Import `com.walkmate.domain.gamification.UserStats`
- Import `java.util.Set`
- Field `private final UserBadgeRepository badgeRepository`

**Added:**
- Field `private final BadgeEvaluationService badgeEvaluationService`
- Single delegation call in `rewardUser`: `badgeEvaluationService.evaluateAndAward(user)`

### `ReviewCommandService.java`

**Added:**
- Import `com.walkmate.application.gamification.BadgeEvaluationService`
- Field `private final BadgeEvaluationService badgeEvaluationService`
- One line in `submitReview` after `userRepository.save(reviewee)`: `badgeEvaluationService.evaluateAndAward(reviewee)`

---

## 4. Before/After Snippet — `rewardUser`

**Before (lines 110–128):**
```java
user.applySessionReward(points, distanceKm);
userRepository.save(user);

UserStats stats = new UserStats(
        userId,
        user.getCompletedSessions(),
        user.getTotalDistanceKm(),
        user.getTotalPoints(),
        user.getTrustScore()
);

Set<String>  existingBadges = badgeRepository.findBadgeNamesByUserId(userId);
List<Badge>  newBadges      = BadgePolicy.evaluateEarned(stats, existingBadges);

if (!newBadges.isEmpty()) {
    badgeRepository.saveAll(userId, newBadges);
    log.info("Awarded {} new badge(s) to user {}: {}", newBadges.size(), userId, newBadges);
}
```

**After:**
```java
user.applySessionReward(points, distanceKm);
userRepository.save(user);

badgeEvaluationService.evaluateAndAward(user);
```

---

## 5. Before/After Snippet — `submitReview`

**Before (lines 81–85):**
```java
int newScore = TrustScorePolicy.apply(reviewee.getTrustScore(), outcome);
reviewee.applyTrustScore(newScore);
userRepository.save(reviewee);

return review;
```

**After:**
```java
int newScore = TrustScorePolicy.apply(reviewee.getTrustScore(), outcome);
reviewee.applyTrustScore(newScore);
userRepository.save(reviewee);
badgeEvaluationService.evaluateAndAward(reviewee);

return review;
```

---

## 6. `compileJava` Output

```
> Task :backend:compileJava

BUILD SUCCESSFUL in 6s
1 actionable task: 1 executed
```

---

## 7. Idempotency Confirmation

`evaluateAndAward` is safe to call multiple times for the same user because:

1. **`BadgePolicy.evaluateEarned`** filters out badges already in `existingBadges` (the set loaded from the DB before each call), so it only returns badges not yet held.
2. **`UserBadgeRepository.saveAll`** is implemented with `ON CONFLICT DO NOTHING` on the `UNIQUE(user_id, badge_name)` constraint, so a duplicate insert is silently ignored at the database level.

Double-calling `evaluateAndAward` in the same session or across concurrent transactions will never produce duplicate badge rows.

---

## 8. Gaps Closed

| Gap | Description | Status |
|-----|-------------|--------|
| G-6 | Create `BadgeEvaluationService`; extract inline badge logic from `GamificationCommandService.rewardUser` | **CLOSED** |
| G-7 | Inject `BadgeEvaluationService` into `ReviewCommandService`; call `evaluateAndAward(reviewee)` after trust-score save | **CLOSED** |

---

## 9. Open Issues / Deviations

| # | Description | Severity |
|---|-------------|----------|
| 1 | `user.getUserId()` returns `UUID`, not `String`. `BadgeEvaluationService` calls `.toString()` internally before passing to `UserStats` and repository methods — consistent with how `rewardUser` previously received `userId` as a plain `String`. No impact on correctness. | None |
| 2 | `GamificationCommandService.calculateTotalDistanceKm` was already updated (per-user chunk selection) before this phase began — G-5 was already applied on the branch. Verified by inspection; no action required. | None |
| 3 | No deviations from the Phase 2 specification in `implementation_plan.md`. | — |
