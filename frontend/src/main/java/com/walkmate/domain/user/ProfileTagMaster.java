package com.walkmate.domain.user;

/** Immutable master-tag option fetched from profile_tag_master. */
public class ProfileTagMaster {

    private final String tagId;
    private final String tagName;

    public ProfileTagMaster(String tagId, String tagName) {
        this.tagId   = tagId;
        this.tagName = tagName;
    }

    public String getTagId()   { return tagId; }
    public String getTagName() { return tagName; }
}
