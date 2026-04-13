package com.walkmate.domain.hotspot;

import com.walkmate.domain.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HotspotErrorCode implements ErrorCode {
    HOTSPOT_NOT_FOUND("Hotspot not found"),
    NO_HOTSPOT_AVAILABLE("No hotspots available");

    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
