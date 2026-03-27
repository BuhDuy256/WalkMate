package com.walkmate.ui.coordination;

import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.domain.walkintent.WalkIntent;

import java.util.Collections;
import java.util.List;

public class CoordinationUiState {

    public enum AppState {
        IDLE,
        HOTSPOT_SELECTED,
        CREATE_INTENT,
        MATCHING,
        MATCH_RESULT
    }

    private final boolean isLoading;
    private final List<Hotspot> hotspots;
    private final Hotspot selectedHotspot;
    private final AppState appState;
    private final WalkIntent activeIntent;
    private final String error;

    public CoordinationUiState(boolean isLoading, List<Hotspot> hotspots,
                                Hotspot selectedHotspot, AppState appState,
                                WalkIntent activeIntent, String error) {
        this.isLoading = isLoading;
        this.hotspots = hotspots != null ? hotspots : Collections.emptyList();
        this.selectedHotspot = selectedHotspot;
        this.appState = appState != null ? appState : AppState.IDLE;
        this.activeIntent = activeIntent;
        this.error = error;
    }

    public static CoordinationUiState initial() {
        return new CoordinationUiState(true, Collections.emptyList(),
                null, AppState.IDLE, null, null);
    }

    public boolean isLoading() { return isLoading; }
    public List<Hotspot> getHotspots() { return hotspots; }
    public Hotspot getSelectedHotspot() { return selectedHotspot; }
    public AppState getAppState() { return appState; }
    public WalkIntent getActiveIntent() { return activeIntent; }
    public String getError() { return error; }
}
