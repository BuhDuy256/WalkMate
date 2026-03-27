package com.walkmate.domain.profile;

import java.util.Arrays;

public enum ProfileTag {
    PET_WALKING("INTERESTS"),
    INDIE_MUSIC("INTERESTS"),
    PHOTOGRAPHY("INTERESTS"),
    NATURE_LOVER("INTERESTS"),
    COFFEE_WALKS("INTERESTS"),
    BOOK_CLUB("INTERESTS"),
    PODCAST_LISTENER("INTERESTS"),
    STREET_ART("INTERESTS"),
    FOODIE("INTERESTS"),
    YOGA_WELLNESS("INTERESTS"),

    QUIET_WALK("WALK_VIBES"),
    CHATTY_SOCIAL("WALK_VIBES"),
    CHALLENGE_PACE("WALK_VIBES"),
    SLOW_SCENIC("WALK_VIBES"),
    CITY_EXPLORER("WALK_VIBES"),
    FOREST_TRAILS("WALK_VIBES"),

    MORNING_BIRD("BEST_TIME"),
    MIDDAY_BREAK("BEST_TIME"),
    GOLDEN_HOUR("BEST_TIME"),
    NIGHT_OWL("BEST_TIME"),
    WEEKENDS_ONLY("BEST_TIME"),
    FLEXIBLE("BEST_TIME"),

    HAS_PET("INTERESTS"),
    QUIET("WALK_VIBES"),
    MUSIC("INTERESTS"),
    EXERCISE("WALK_VIBES"),
    RELAX("WALK_VIBES");

    private final String category;

    ProfileTag(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public String toCode() {
        return name();
    }

    public static ProfileTag fromCode(String code) {
        return Arrays.stream(values())
                .filter(tag -> tag.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid profile tag: " + code));
    }
}
