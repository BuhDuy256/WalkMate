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
    // Phase 4 — scanning lifecycle signals
    private final boolean scanTimedOut;        // true = show "Still looking…" dialog
    private final String matchFoundProposalId; // non-null = navigate to Proposal tab; consume after use
    // Intent was created with OPEN status (no immediate match) — navigate to Finding tab.
    private final boolean intentOpenPending;
    // Phase 4 — auth gate: non-null when user tapped a hotspot but is not logged in.
    // Fragment navigates to login then clears this via consumePendingHotspot().
    private final String pendingHotspotId;

    /** Public constructor — filteredHotspots defaults to the full hotspots list. */
    public ExploreUiState(boolean isLoading, List<Hotspot> hotspots,
                          Hotspot selectedHotspot, AppState appState, String error) {
        this.isLoading             = isLoading;
        this.hotspots              = hotspots != null ? hotspots : Collections.emptyList();
        this.filteredHotspots      = this.hotspots;
        this.selectedHotspot       = selectedHotspot;
        this.appState              = appState != null ? appState : AppState.WELCOME;
        this.error                 = error;
        this.scanTimedOut          = false;
        this.matchFoundProposalId  = null;
        this.intentOpenPending     = false;
        this.pendingHotspotId      = null;
    }

    /** Full private constructor used by with*() copy helpers. */
    private ExploreUiState(boolean isLoading, List<Hotspot> hotspots,
                           List<Hotspot> filteredHotspots,
                           Hotspot selectedHotspot, AppState appState, String error,
                           boolean scanTimedOut, String matchFoundProposalId,
                           boolean intentOpenPending, String pendingHotspotId) {
        this.isLoading             = isLoading;
        this.hotspots              = hotspots != null ? hotspots : Collections.emptyList();
        this.filteredHotspots      = filteredHotspots != null ? filteredHotspots : this.hotspots;
        this.selectedHotspot       = selectedHotspot;
        this.appState              = appState != null ? appState : AppState.WELCOME;
        this.error                 = error;
        this.scanTimedOut          = scanTimedOut;
        this.matchFoundProposalId  = matchFoundProposalId;
        this.intentOpenPending     = intentOpenPending;
        this.pendingHotspotId      = pendingHotspotId;
    }

    /** Returns a copy of this state with a different filtered list. */
    public ExploreUiState withFilteredHotspots(List<Hotspot> filtered) {
        return new ExploreUiState(isLoading, hotspots, filtered,
                selectedHotspot, appState, error, scanTimedOut, matchFoundProposalId,
                intentOpenPending, pendingHotspotId);
    }

    /** Returns a copy of this state with scanTimedOut=true. */
    public ExploreUiState withScanTimedOut(boolean timedOut) {
        return new ExploreUiState(isLoading, hotspots, filteredHotspots,
                selectedHotspot, appState, error, timedOut, matchFoundProposalId,
                intentOpenPending, pendingHotspotId);
    }

    /** Returns a copy of this state signalling a match was found (navigate to Proposal tab). */
    public ExploreUiState withMatchFound(String proposalId) {
        return new ExploreUiState(isLoading, hotspots, filteredHotspots,
                selectedHotspot, appState, error, false, proposalId,
                false, pendingHotspotId);
    }

    /** Returns a copy signalling intent was created with OPEN status (navigate to Finding tab). */
    public ExploreUiState withIntentOpen() {
        return new ExploreUiState(isLoading, hotspots, filteredHotspots,
                selectedHotspot, appState, error, false, null,
                true, pendingHotspotId);
    }

    /** Clears the intentOpenPending signal after navigation. */
    public ExploreUiState withIntentOpenConsumed() {
        return new ExploreUiState(isLoading, hotspots, filteredHotspots,
                selectedHotspot, appState, error, scanTimedOut, matchFoundProposalId,
                false, pendingHotspotId);
    }

    /** Returns a copy signalling the user must log in before selecting a hotspot. */
    public ExploreUiState withPendingHotspot(String hotspotId) {
        return new ExploreUiState(isLoading, hotspots, filteredHotspots,
                selectedHotspot, appState, error, scanTimedOut, matchFoundProposalId,
                intentOpenPending, hotspotId);
    }

    /** Clears the pending hotspot event after the Fragment has handled it. */
    public ExploreUiState withPendingHotspotConsumed() {
        return new ExploreUiState(isLoading, hotspots, filteredHotspots,
                selectedHotspot, appState, error, scanTimedOut, matchFoundProposalId,
                intentOpenPending, null);
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
    public boolean isScanTimedOut()                { return scanTimedOut; }
    public String getMatchFoundProposalId()        { return matchFoundProposalId; }
    public boolean isIntentOpenPending()           { return intentOpenPending; }
    public String getPendingHotspotId()            { return pendingHotspotId; }
}
