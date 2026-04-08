package com.walkmate.application.gamification;

import com.walkmate.domain.gamification.Badge;
import com.walkmate.domain.gamification.BadgePolicy;
import com.walkmate.domain.gamification.UserBadgeRepository;
import com.walkmate.domain.gamification.UserStats;
import com.walkmate.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Evaluates and awards badges for a user whose stats have just changed.
 *
 * <p>Safe to call from any flow that modifies user stats (session completion,
 * review submission, etc.). Idempotent — the DB constraint
 * {@code UNIQUE(user_id, badge_name)} and the {@code ON CONFLICT DO NOTHING}
 * clause on {@link UserBadgeRepository#saveAll} guarantee no duplicate awards.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeEvaluationService {

    private final UserBadgeRepository badgeRepository;

    /**
     * Evaluates which badges {@code user} has newly earned and persists them.
     *
     * @param user the user whose stats have already been updated and saved
     */
    public void evaluateAndAward(User user) {
        String userId = user.getUserId().toString();

        UserStats stats = new UserStats(
                userId,
                user.getCompletedSessions(),
                user.getTotalDistanceKm(),
                user.getTotalPoints(),
                user.getTrustScore()
        );

        Set<String> existing  = badgeRepository.findBadgeNamesByUserId(userId);
        List<Badge> newBadges = BadgePolicy.evaluateEarned(stats, existing);

        if (!newBadges.isEmpty()) {
            badgeRepository.saveAll(userId, newBadges);
            log.info("BadgeEvaluation: awarded {} badge(s) to user {}: {}",
                    newBadges.size(), userId, newBadges);
        }
    }
}
