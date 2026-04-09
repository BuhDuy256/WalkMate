package com.walkmate.ui.profile;

import com.walkmate.domain.user.VisibilityMode;

import java.util.List;

/**
 * Immutable snapshot of all data rendered on the Profile screen.
 *
 * Rule: no setters. ProfileViewModel calls postValue(new ProfileUiState(...))
 * to deliver each new state to the Fragment.
 *
 * Badge carries Android resource IDs (not strings) so the Fragment can call
 * imgBadge.setImageResource(badge.iconDrawableResId) without any string
 * matching or switch statements.
 */
public class ProfileUiState {

    // ── Badge inner class ──────────────────────────────────────────────────────

    public static class Badge {
        /**
         * @param labelStringResId  e.g. R.string.profile_badge_first_walk
         * @param iconDrawableResId e.g. R.drawable.ic_badge_first_walk
         */
        public final int labelStringResId;
        public final int iconDrawableResId;

        public Badge(int labelStringResId, int iconDrawableResId) {
            this.labelStringResId = labelStringResId;
            this.iconDrawableResId = iconDrawableResId;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final String name;
    private final String avatarUrl;      // null → use ic_user placeholder
    private final boolean isOnline;
    private final float trustScore;      // e.g. 4.9f
    private final List<String> personalityTags;  // e.g. ["Chatty", "Dog Friendly"]
    private final double totalDistanceKm;
    private final int totalSessions;
    private final int currentStreak;
    private final List<Badge> badges;    // max 3 shown in the Milestones card
    private final VisibilityMode visibilityMode;
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
            VisibilityMode visibilityMode,
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
        this.visibilityMode = visibilityMode;
        this.error = error;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Returns a loading placeholder state. Displayed while data is being fetched. */
    public static ProfileUiState loading() {
        return new ProfileUiState(
                true, null, null, false,
                0f, null, 0.0, 0, 0, null, null, null);
    }

    /** Returns an error state. */
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
    public VisibilityMode getVisibilityMode() { return visibilityMode; }
    public String getError()                 { return error; }
}
