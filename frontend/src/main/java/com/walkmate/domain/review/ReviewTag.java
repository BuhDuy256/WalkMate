package com.walkmate.domain.review;

/** Domain model for one entry from the review_tag_master vocabulary. */
public class ReviewTag {

    private final String tagId;
    private final String tagName;
    private final String tagType; // POSITIVE_INTEREST | POSITIVE_BEHAVIOR | NEGATIVE_INTEREST | NEGATIVE_BEHAVIOR

    public ReviewTag(String tagId, String tagName, String tagType) {
        this.tagId   = tagId;
        this.tagName = tagName;
        this.tagType = tagType;
    }

    public String getTagId()   { return tagId; }
    public String getTagName() { return tagName; }
    public String getTagType() { return tagType; }

    /** True when this tag belongs to the positive sentiment bucket (4–5 star reviews). */
    public boolean isPositive() { return tagType != null && tagType.startsWith("POSITIVE"); }
}
