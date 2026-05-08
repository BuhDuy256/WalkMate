package com.walkmate.ui.walkpost;

public class CreateWalkPostUiState {

    public enum Kind { IDLE, LOADING, SUCCESS, ERROR }

    public final Kind kind;
    public final String error;

    private CreateWalkPostUiState(Kind kind, String error) {
        this.kind = kind;
        this.error = error;
    }

    public static CreateWalkPostUiState idle() {
        return new CreateWalkPostUiState(Kind.IDLE, null);
    }

    public static CreateWalkPostUiState loading() {
        return new CreateWalkPostUiState(Kind.LOADING, null);
    }

    public static CreateWalkPostUiState success() {
        return new CreateWalkPostUiState(Kind.SUCCESS, null);
    }

    public static CreateWalkPostUiState error(String message) {
        return new CreateWalkPostUiState(Kind.ERROR, message);
    }
}
