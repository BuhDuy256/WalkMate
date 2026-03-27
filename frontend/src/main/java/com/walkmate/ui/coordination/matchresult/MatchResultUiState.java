package com.walkmate.ui.coordination.matchresult;

public class MatchResultUiState {

    public enum Action { NONE, ACCEPTED, PASSED }

    private final Action action;

    public MatchResultUiState(Action action) {
        this.action = action;
    }

    public static MatchResultUiState initial() {
        return new MatchResultUiState(Action.NONE);
    }

    public Action getAction() { return action; }
}
