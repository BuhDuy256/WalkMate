package com.walkmate.ui.session;

import com.walkmate.tracking.TrackingCommand;

public class SessionUiState {
    public final SessionScreenStatus status;
    public final double distanceMeters;
    public final long durationSeconds;
    public final String errorMessage;
    public final TrackingCommand pendingCommand;
    public final long commandVersion;

    public SessionUiState(
            SessionScreenStatus status,
            double distanceMeters,
            long durationSeconds,
            String errorMessage,
            TrackingCommand pendingCommand,
            long commandVersion) {
        this.status = status;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.errorMessage = errorMessage;
        this.pendingCommand = pendingCommand;
        this.commandVersion = commandVersion;
    }

    public static SessionUiState idle() {
        return new SessionUiState(
                SessionScreenStatus.IDLE,
                0.0,
                0L,
                null,
                TrackingCommand.NONE,
                0L);
    }
}
