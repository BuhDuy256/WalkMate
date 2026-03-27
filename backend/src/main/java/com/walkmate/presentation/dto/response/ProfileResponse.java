package com.walkmate.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private UUID userId;
    private String fullName;
    private String city;
    private String avatarUrl;
    private String bio;
    private String profileMode;
    private String infoVisibilityMode;
    private List<String> tags;
    private String email;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
