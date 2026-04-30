package com.walkmate.presentation.controller.gamification;

import com.walkmate.domain.gamification.UserBadgeRepository;
import com.walkmate.domain.user.User;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.gamification.BadgeCatalogResponse;
import com.walkmate.presentation.dto.response.gamification.BadgeResponse;
import com.walkmate.presentation.dto.response.gamification.LeaderboardEntryResponse;
import com.walkmate.presentation.dto.response.gamification.UserStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GamificationController {

    private static final int LEADERBOARD_SIZE = 50;

    private final UserRepository      userRepository;
    private final UserBadgeRepository badgeRepository;

    // ── GET /api/v1/users/{userId}/badges ─────────────────────────────────────

    @GetMapping("/users/{userId}/badges")
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getBadges(
            @PathVariable String userId) {

        List<BadgeResponse> badges = badgeRepository.findByUserId(userId)
                .stream()
                .map(r -> new BadgeResponse(
                        r.badgeName(), r.displayName(), r.description(),
                        r.awardedAt(), r.rarity(), r.category()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(badges));
    }

    // ── GET /api/v1/badges/catalog ────────────────────────────────────────────

    @GetMapping("/badges/catalog")
    public ResponseEntity<ApiResponse<List<BadgeCatalogResponse>>> getBadgeCatalog() {
        List<BadgeCatalogResponse> catalog = badgeRepository.findAllActive()
                .stream()
                .map(r -> new BadgeCatalogResponse(r.name(), r.displayName(), r.description(),
                        r.rarity(), r.category()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(catalog));
    }

    // ── GET /api/v1/users/{userId}/stats ──────────────────────────────────────

    @GetMapping("/users/{userId}/stats")
    public ResponseEntity<ApiResponse<UserStatsResponse>> getStats(
            @PathVariable String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.walkmate.domain.shared.exception.DomainException(
                        com.walkmate.domain.user.UserErrorCode.USER_NOT_FOUND));

        UserStatsResponse stats = new UserStatsResponse(
                userId,
                user.getTotalPoints(),
                user.getTotalDistanceKm(),
                user.getCompletedSessions(),
                user.getTrustScore()
        );

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ── GET /api/v1/leaderboard ───────────────────────────────────────────────

    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<List<LeaderboardEntryResponse>>> getLeaderboard() {
        AtomicInteger rank = new AtomicInteger(1);

        List<LeaderboardEntryResponse> entries = userRepository.findTopByPoints(LEADERBOARD_SIZE)
                .stream()
                .map(u -> new LeaderboardEntryResponse(
                        rank.getAndIncrement(),
                        u.getUserId().toString(),
                        u.getTotalPoints(),
                        u.getTotalDistanceKm(),
                        u.getCompletedSessions(),
                        u.getTrustScore()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(entries));
    }
}
