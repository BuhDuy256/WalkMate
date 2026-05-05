package com.walkmate.ui.profile;

import com.walkmate.R;
import com.walkmate.domain.review.WalkReview;

import java.util.List;

/**
 * Immutable snapshot of all data rendered on the Profile screen.
 *
 * Rule: no setters. ProfileViewModel calls postValue(new ProfileUiState(...))
 * to deliver each new state to the Fragment.
 *
 * Removed fields (intentional simplification):
 *   - isOnline       — no presence system; always false → removed from UI
 *   - currentStreak  — no backend endpoint yet; always 0 → removed from UI
 *   - visibilityMode — not returned by the backend; switch removed from UI
 */
public class ProfileUiState {

    // ── Trust tier categorization ─────────────────────────────────────────────

    /**
     * Three-tier trust classification.
     *
     * Elite     ≥ 800 pts — consistently reliable, top-ranked in matching.
     * Standard  300–799   — normal standing.
     * Restricted < 300    — flagged behaviour; lower matching priority.
     */
    public enum TrustTier {
        ELITE     ("Elite",      R.color.orange_primary),
        STANDARD  ("Standard",   R.color.text_muted),
        RESTRICTED("Restricted", R.color.color_danger);

        public final String label;
        public final int    colorRes;

        TrustTier(String label, int colorRes) {
            this.label    = label;
            this.colorRes = colorRes;
        }

        public static TrustTier fromScore(int score) {
            if (score >= 800) return ELITE;
            if (score >= 300) return STANDARD;
            return RESTRICTED;
        }
    }

    // ── Badge inner class ──────────────────────────────────────────────────────

    public static class Badge {
        public final String label;
        public final String rarity;

        public Badge(String label, String rarity) {
            this.label  = label;
            this.rarity = rarity;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final boolean isLoading;
    private final String name;
    private final String avatarUrl;             // null → use ic_user placeholder
    private final float trustScore;             // e.g. 4.9f
    private final List<String> personalityTags; // e.g. ["Chatty", "Dog Friendly"]
    private final double totalDistanceKm;
    private final int totalSessions;
    private final List<Badge> badges;           // max 3 shown in the Milestones card
    private final List<WalkReview> reviews;     // received reviews for this user
    private final String error;
    private final boolean isAdmin;
    private final int adminPendingCount;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ProfileUiState(
            boolean isLoading,
            String name,
            String avatarUrl,
            float trustScore,
            List<String> personalityTags,
            double totalDistanceKm,
            int totalSessions,
            List<Badge> badges,
            List<WalkReview> reviews,
            String error) {
        this(isLoading, name, avatarUrl, trustScore, personalityTags,
             totalDistanceKm, totalSessions, badges, reviews, error, false, 0);
    }

    public ProfileUiState(
            boolean isLoading,
            String name,
            String avatarUrl,
            float trustScore,
            List<String> personalityTags,
            double totalDistanceKm,
            int totalSessions,
            List<Badge> badges,
            List<WalkReview> reviews,
            String error,
            boolean isAdmin,
            int adminPendingCount) {
        this.isLoading         = isLoading;
        this.name              = name;
        this.avatarUrl         = avatarUrl;
        this.trustScore        = trustScore;
        this.personalityTags   = personalityTags;
        this.totalDistanceKm   = totalDistanceKm;
        this.totalSessions     = totalSessions;
        this.badges            = badges;
        this.reviews           = reviews;
        this.error             = error;
        this.isAdmin           = isAdmin;
        this.adminPendingCount = adminPendingCount;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static ProfileUiState loading() {
        return new ProfileUiState(
                true, null, null,
                0f, null, 0.0, 0, null, null, null);
    }

    public static ProfileUiState error(String message) {
        return new ProfileUiState(
                false, null, null,
                0f, null, 0.0, 0, null, null, message);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isLoading()               { return isLoading; }
    public String getName()                  { return name; }
    public String getAvatarUrl()             { return avatarUrl; }
    public float getTrustScore()             { return trustScore; }
    /** Returns the trust tier derived from the integer trust score (0–1000). */
    public TrustTier getTrustTier()          { return TrustTier.fromScore((int) trustScore); }
    public List<String> getPersonalityTags() { return personalityTags; }
    public double getTotalDistanceKm()       { return totalDistanceKm; }
    public int getTotalSessions()            { return totalSessions; }
    public List<Badge> getBadges()           { return badges; }
    public String getError()                 { return error; }
    public List<WalkReview> getReviews()     { return reviews; }
    public boolean isAdmin()                 { return isAdmin; }
    public int getAdminPendingCount()        { return adminPendingCount; }
}
