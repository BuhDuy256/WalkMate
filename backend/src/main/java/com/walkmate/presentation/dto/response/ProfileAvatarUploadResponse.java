package com.walkmate.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileAvatarUploadResponse {
    @JsonProperty("avatar_url")
    private String avatarUrl;
}
