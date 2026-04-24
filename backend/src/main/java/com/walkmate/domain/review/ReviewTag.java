package com.walkmate.domain.review;

import java.util.UUID;

/**
 * Immutable value object representing one entry from the {@code review_tag_master} table.
 *
 * {@code tagType} follows the convention {@code POSITIVE_*} or {@code NEGATIVE_*} so
 * the client can bucket tags by the selected star rating without extra logic.
 */
public record ReviewTag(UUID tagId, String tagName, String tagType) {}
