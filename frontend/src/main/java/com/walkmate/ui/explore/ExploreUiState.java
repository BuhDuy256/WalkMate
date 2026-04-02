package com.walkmate.ui.explore;

import com.walkmate.domain.hotspot.Hotspot;

import java.util.Collections;
import java.util.List;

/**
 * Immutable state for the Explore tab.
 *
 * AppState lifecycle:
 *   WELCOME  — Map visible, hotspot markers shown. Welcome bottom sheet at bottom (Phase B4).
 *   SETUP    — A hotspot was selected; map zooms in. Create-intent form shown (Phase B5).
 *   SCANNING — Intent submitted; scanning floating card shown above map (Phase B6).
 *
 * NOTE: The old IDLE / HOTSPOT_SELECTED / CREATE_INTENT / MATCHING / MATCH_RESULT states
 * from CoordinationUiState are fully replaced by this enum as part of Phase B3.
 */
public class ExploreUiState {

    public enum AppState {
        WELCOME,
        SETUP,
        SCANNING
    }

    private final boolean isLoading;
    private final List<Hotspot> hotspots;
    private final List<Hotspot> filteredHotspots; // subset of hotspots; equals hotspots when no filter
    private final Hotspot selectedHotspot;  // non-null in SETUP and SCANNING
    private final AppState appState;
    private final String error;

    /** Public constructor — filteredHotspots defaults to the full hotspots list. */
    public ExploreUiState(boolean isLoading, List<Hotspot> hotspots,
                          Hotspot selectedHotspot, AppState appState, String error) {
        this.isLoading        = isLoading;
        this.hotspots         = hotspots != null ? hotspots : Collections.emptyList();
        this.filteredHotspots = this.hotspots;
        this.selectedHotspot  = selectedHotspot;
        this.appState         = appState != null ? appState : AppState.WELCOME;
        this.error            = error;
    }

    /** Private full constructor used by withFilteredHotspots(). */
    private ExploreUiState(boolean isLoading, List<Hotspot> hotspots,
                           List<Hotspot> filteredHotspots,
                           Hotspot selectedHotspot, AppState appState, String error) {
        this.isLoading        = isLoading;
        this.hotspots         = hotspots != null ? hotspots : Collections.emptyList();
        this.filteredHotspots = filteredHotspots != null ? filteredHotspots : this.hotspots;
        this.selectedHotspot  = selectedHotspot;
        this.appState         = appState != null ? appState : AppState.WELCOME;
        this.error            = error;
    }

    /** Returns a copy of this state with a different filtered list. */
    public ExploreUiState withFilteredHotspots(List<Hotspot> filtered) {
        return new ExploreUiState(isLoading, hotspots, filtered,
                selectedHotspot, appState, error);
    }

    public static ExploreUiState initial() {
        return new ExploreUiState(true, Collections.emptyList(), null, AppState.WELCOME, null);
    }

    public boolean isLoading()                     { return isLoading; }
    public List<Hotspot> getHotspots()             { return hotspots; }
    public List<Hotspot> getFilteredHotspots()     { return filteredHotspots; }
    public Hotspot getSelectedHotspot()            { return selectedHotspot; }
    public AppState getAppState()                  { return appState; }
    public String getError()                       { return error; }
}
