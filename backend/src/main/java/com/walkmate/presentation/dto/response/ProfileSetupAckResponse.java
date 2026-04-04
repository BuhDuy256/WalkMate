package com.walkmate.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ProfileSetupAckResponse {
    private boolean success;
    private String message;
    private LocalDateTime receivedAt;
    private String name;
    private String city;
    private String walkBio;
    private String avatar;
    private LocalDate dateOfBirth;
    private String gender;
    private Boolean publicProfile;
    private Boolean publicInfo;
    private List<String> interests;
    private List<String> walkVibes;
    private List<String> bestTimeToWalk;
}
