package com.walkmate.ui.rating;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RatingViewData {
    private final UUID sessionId;
    private final PartnerInfo partner;
    private final WalkStats stats;
    private final int selectedStars;
    private final List<TagViewData> availableTags;
    private final String comment;

    public RatingViewData(
            UUID sessionId,
            PartnerInfo partner,
            WalkStats stats,
            int selectedStars,
            List<TagViewData> availableTags,
            String comment
    ) {
        this.sessionId = sessionId;
        this.partner = partner;
        this.stats = stats;
        this.selectedStars = selectedStars;
        this.availableTags = availableTags;
        this.comment = comment;
    }

    public boolean isValid() {
        return selectedStars >= 1 && selectedStars <= 5;
    }

    public List<TagViewData> getSelectedTags() {
        return availableTags.stream()
                .filter(TagViewData::isSelected)
                .collect(Collectors.toList());
    }

    public List<TagViewData> getSelectedTagsWithDbCode() {
        return availableTags.stream()
                .filter(TagViewData::isSelected)
                .filter(tag -> tag.getDbCode() != null && !tag.getDbCode().isEmpty())
                .collect(Collectors.toList());
    }

    public RatingViewData withStars(int stars) {
        return new RatingViewData(sessionId, partner, stats, stars, availableTags, comment);
    }

    public RatingViewData withTags(List<TagViewData> newTags) {
        return new RatingViewData(sessionId, partner, stats, selectedStars, newTags, comment);
    }

    public RatingViewData withComment(String newComment) {
        return new RatingViewData(sessionId, partner, stats, selectedStars, availableTags, newComment);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public PartnerInfo getPartner() {
        return partner;
    }

    public WalkStats getStats() {
        return stats;
    }

    public int getSelectedStars() {
        return selectedStars;
    }

    public List<TagViewData> getAvailableTags() {
        return availableTags;
    }

    public String getComment() {
        return comment;
    }

    public static class PartnerInfo {
        private final UUID userId;
        private final String name;
        private final String avatarUrl;

        public PartnerInfo(UUID userId, String name, String avatarUrl) {
            this.userId = userId;
            this.name = name;
            this.avatarUrl = avatarUrl;
        }

        public UUID getUserId() {
            return userId;
        }

        public String getName() {
            return name;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }

    public static class WalkStats {
        private final String duration;
        private final String distance;
        private final String steps;
        private final String walkDate;

        public WalkStats(String duration, String distance, String steps, String walkDate) {
            this.duration = duration;
            this.distance = distance;
            this.steps = steps;
            this.walkDate = walkDate;
        }

        public String getDuration() {
            return duration;
        }

        public String getDistance() {
            return distance;
        }

        public String getSteps() {
            return steps;
        }

        public String getWalkDate() {
            return walkDate;
        }
    }

    public static class TagViewData {
        private final String code;
        private final String dbCode;
        private final String displayText;
        private final boolean selected;

        public TagViewData(String code, String dbCode, String displayText, boolean selected) {
            this.code = code;
            this.dbCode = dbCode;
            this.displayText = displayText;
            this.selected = selected;
        }

        public String getCode() {
            return code;
        }

        public String getDbCode() {
            return dbCode;
        }

        public String getDisplayText() {
            return displayText;
        }

        public boolean isSelected() {
            return selected;
        }

        public TagViewData withSelected(boolean isSelected) {
            return new TagViewData(code, dbCode, displayText, isSelected);
        }
    }

    public static RatingViewData createInitial(UUID sessionId, UUID partnerId, String partnerName) {
        PartnerInfo partner = new PartnerInfo(partnerId, partnerName, null);
        WalkStats stats = new WalkStats("32 min", "1.2 km", "1,580 steps", "04 MAR");

        List<TagViewData> tags = new ArrayList<>();
        tags.add(new TagViewData("FRIENDLY", "FRIENDLY", "Friendly", false));
        tags.add(new TagViewData("ON_TIME", "PUNCTUAL", "On-time", false));
        tags.add(new TagViewData("GREAT_CHAT", "GOOD_CONVERSATION", "Great chat", false));
        tags.add(new TagViewData("GOOD_PACE", "RESPECTFUL", "Good pace", false));
        tags.add(new TagViewData("NATURE_LOVER", null, "Nature lover", false));
        tags.add(new TagViewData("SAFE_ROUTE", null, "Safe route", false));
        tags.add(new TagViewData("ENCOURAGING", null, "Encouraging", false));
        tags.add(new TagViewData("FOCUSED", null, "Focused", false));

        return new RatingViewData(sessionId, partner, stats, 0, tags, "");
    }

    public static RatingViewData createWithPartnerData(
            UUID sessionId,
            UUID partnerId,
            String partnerName,
            String avatarUrl,
            String duration,
            String distance,
            String steps,
            String walkDate
    ) {
        PartnerInfo partner = new PartnerInfo(partnerId, partnerName, avatarUrl);
        WalkStats stats = new WalkStats(duration, distance, steps, walkDate);

        List<TagViewData> tags = new ArrayList<>();
        tags.add(new TagViewData("FRIENDLY", "FRIENDLY", "Friendly", false));
        tags.add(new TagViewData("ON_TIME", "PUNCTUAL", "On-time", false));
        tags.add(new TagViewData("GREAT_CHAT", "GOOD_CONVERSATION", "Great chat", false));
        tags.add(new TagViewData("GOOD_PACE", "RESPECTFUL", "Good pace", false));
        tags.add(new TagViewData("NATURE_LOVER", null, "Nature lover", false));
        tags.add(new TagViewData("SAFE_ROUTE", null, "Safe route", false));
        tags.add(new TagViewData("ENCOURAGING", null, "Encouraging", false));
        tags.add(new TagViewData("FOCUSED", null, "Focused", false));

        return new RatingViewData(sessionId, partner, stats, 0, tags, "");
    }
}
