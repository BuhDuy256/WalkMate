package com.walkmate.ui.profile;

import com.walkmate.domain.review.WalkReview;

import java.util.List;

/**
 * Immutable snapshot of all data rendered on the Profile screen.
 *
 * Rule: no setters. ProfileViewModel calls postValue(new ProfileUiState(...))
 * to deliver each new state to the Fragment.
 */
public class ProfileUiState {

    // ── Badge inner class ──────────────────────────────────────────────────────

    public static class Badge {
        /**
         * Display label resolved from the backend badge name (e.g. "First Walk").
         * Previously held a string resource ID; replaced with a real string now
         * that badge data comes from GamificationRepository.
         */
        public final String label;
        /**
         * Drawable resource ID for the badge icon.
         * 0 means "no specific icon" — the Fragment uses a generic placeholder.
         * Phase 14 will wire specific drawables per badge name.
         */
        public final int iconDrawableResId;

        public Badge(String label, int iconDrawableResId) {
            this.label = label;
            this.iconDrawableResId = iconDrawableResId;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final String name;
    private final String avatarUrl;             // null → use ic_user placeholder
    private final boolean isOnline;
    private final float trustScore;             // e.g. 4.9f
    private final List<String> personalityTags; // e.g. ["Chatty", "Dog Friendly"]
    private final double totalDistanceKm;
    private final int totalSessions;
    private final int currentStreak;
    private final List<Badge> badges;           // max 3 shown in the Milestones card
    private final List<WalkReview> reviews;     // received reviews for this user
    private final String error;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ProfileUiState(
            boolean isLoading,
            String name,
            String avatarUrl,
            boolean isOnline,
            float trustScore,
            List<String> personalityTags,
            double totalDistanceKm,
            int totalSessions,
            int currentStreak,
            List<Badge> badges,
            List<WalkReview> reviews,
            String error) {
        this.isLoading = isLoading;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.isOnline = isOnline;
        this.trustScore = trustScore;
        this.personalityTags = personalityTags;
        this.totalDistanceKm = totalDistanceKm;
        this.totalSessions = totalSessions;
        this.currentStreak = currentStreak;
        this.badges = badges;
        this.reviews = reviews;
        this.error = error;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static ProfileUiState loading() {
        return new ProfileUiState(
                true, null, null, false,
                0f, null, 0.0, 0, 0, null, null, null);
    }

    public static ProfileUiState error(String message) {
        return new ProfileUiState(
                false, null, null, false,
                0f, null, 0.0, 0, 0, null, null, message);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()               { return isLoading; }
    public String getName()                  { return name; }
    public String getAvatarUrl()             { return avatarUrl; }
    public boolean isOnline()                { return isOnline; }
    public float getTrustScore()             { return trustScore; }
    public List<String> getPersonalityTags() { return personalityTags; }
    public double getTotalDistanceKm()       { return totalDistanceKm; }
    public int getTotalSessions()            { return totalSessions; }
    public int getCurrentStreak()            { return currentStreak; }
    public List<Badge> getBadges()           { return badges; }
    public List<WalkReview> getReviews()     { return reviews; }
    public String getError()                 { return error; }
}
