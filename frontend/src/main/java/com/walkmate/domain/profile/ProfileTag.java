package com.walkmate.domain.profile;

import java.util.Arrays;

public enum ProfileTag {
    PET_WALKING("INTERESTS", "Pet Walking"),
    INDIE_MUSIC("INTERESTS", "Indie Music"),
    PHOTOGRAPHY("INTERESTS", "Photography"),
    NATURE_LOVER("INTERESTS", "Nature Lover"),
    COFFEE_WALKS("INTERESTS", "Coffee Walks"),
    BOOK_CLUB("INTERESTS", "Book Club"),
    PODCAST_LISTENER("INTERESTS", "Podcast Listener"),
    STREET_ART("INTERESTS", "Street Art"),
    FOODIE("INTERESTS", "Foodie"),
    YOGA_WELLNESS("INTERESTS", "Yoga & Wellness"),

    QUIET_WALK("WALK_VIBES", "Quiet Walk"),
    CHATTY_SOCIAL("WALK_VIBES", "Chatty & Social"),
    CHALLENGE_PACE("WALK_VIBES", "Challenge Pace"),
    SLOW_SCENIC("WALK_VIBES", "Slow & Scenic"),
    CITY_EXPLORER("WALK_VIBES", "City Explorer"),
    FOREST_TRAILS("WALK_VIBES", "Forest Trails"),

    MORNING_BIRD("BEST_TIME", "Morning Bird"),
    MIDDAY_BREAK("BEST_TIME", "Midday Break"),
    GOLDEN_HOUR("BEST_TIME", "Golden Hour"),
    NIGHT_OWL("BEST_TIME", "Night Owl"),
    WEEKENDS_ONLY("BEST_TIME", "Weekends Only"),
    FLEXIBLE("BEST_TIME", "Flexible");

    private final String category;
    private final String displayName;

    ProfileTag(String category, String displayName) {
        this.category = category;
        this.displayName = displayName;
    }

    public String getCategory() {
        return category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProfileTag fromCode(String code) {
        return Arrays.stream(values())
                .filter(tag -> tag.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid profile tag: " + code));
    }
}
