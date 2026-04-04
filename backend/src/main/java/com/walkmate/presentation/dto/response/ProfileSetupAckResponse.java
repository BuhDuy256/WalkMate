package com.walkmate.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileSetupAckResponse {
    private boolean success;
    private String message;
}
