package com.walkmate.ui.coordination.matching;

public class MatchingUiState {

    private final String hotspotName;
    private final boolean isMatchFound;

    public MatchingUiState(String hotspotName, boolean isMatchFound) {
        this.hotspotName = hotspotName;
        this.isMatchFound = isMatchFound;
    }

    public static MatchingUiState initial(String hotspotName) {
        return new MatchingUiState(hotspotName, false);
    }

    public String getHotspotName() { return hotspotName; }
    public boolean isMatchFound() { return isMatchFound; }
}
