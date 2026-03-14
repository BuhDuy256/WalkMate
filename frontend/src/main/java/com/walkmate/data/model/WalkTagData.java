package com.walkmate.data.model;

import java.util.ArrayList;
import java.util.List;

public class WalkTagData {
    private static final List<WalkTag> tags = new ArrayList<>();

    static {
        tags.add(new WalkTag(
                "friendly",
                "Friendly",
                com.walkmate.R.drawable.ic_friendly,
                0xFFFFF9E6,
                0xFFFFD54F,
                0xFF8B6F47
        ));
        tags.add(new WalkTag(
                "on_time",
                "On-time",
                com.walkmate.R.drawable.ic_on_time,
                0xFFFFE6E6,
                0xFFFF6B6B,
                0xFF8B4545
        ));
        tags.add(new WalkTag(
                "great_chat",
                "Great chat",
                com.walkmate.R.drawable.ic_direction_walk,
                0xFFE6E8F5,
                0xFF5B7FB8,
                0xFF5B7FB8
        ));
        tags.add(new WalkTag(
                "good_pace",
                "Good pace",
                com.walkmate.R.drawable.ic_direction_walk,
                0xFFFFF3E0,
                0xFFFFA500,
                0xFF8B6F47
        ));
        tags.add(new WalkTag(
                "nature_lover",
                "Nature lover",
                com.walkmate.R.drawable.ic_nature,
                0xFFE8F5E9,
                0xFF4CAF50,
                0xFF4CAF50
        ));
        tags.add(new WalkTag(
                "safe_route",
                "Safe route",
                com.walkmate.R.drawable.ic_direction_walk,
                0xFFF0F0F0,
                0xFF9C9C9C,
                0xFF666666
        ));
        tags.add(new WalkTag(
                "encouraging",
                "Encouraging",
                com.walkmate.R.drawable.ic_friendly,
                0xFFFFF9E6,
                0xFFFFD54F,
                0xFF8B6F47
        ));
        tags.add(new WalkTag(
                "focused",
                "Focused",
                com.walkmate.R.drawable.ic_on_time,
                0xFFFFE6E6,
                0xFFFF5252,
                0xFF8B4545
        ));
    }

    public static List<WalkTag> getAllTags() {
        return new ArrayList<>(tags);
    }

    public static WalkTag getTagById(String id) {
        for (WalkTag tag : tags) {
            if (tag.getId().equals(id)) {
                return tag;
            }
        }
        return null;
    }
}
