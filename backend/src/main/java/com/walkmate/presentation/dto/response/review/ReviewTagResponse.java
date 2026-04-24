package com.walkmate.presentation.dto.response.review;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ReviewTagResponse(

        @JsonProperty("tag_id")
        UUID tagId,

        @JsonProperty("tag_name")
        String tagName,

        @JsonProperty("tag_type")
        String tagType
) {}
