package com.walkmate.data.datasource.remote.dto.request.walkpost;

import com.google.gson.annotations.SerializedName;

public class CreateWalkPostRequest {

    @SerializedName("caption")        private final String caption;
    @SerializedName("visibility")     private final String visibility;
    @SerializedName("show_companion") private final boolean showCompanion;
    @SerializedName("show_route_map") private final boolean showRouteMap;
    @SerializedName("show_stats")     private final boolean showStats;

    public CreateWalkPostRequest(String caption, String visibility,
                                  boolean showCompanion, boolean showRouteMap, boolean showStats) {
        this.caption       = caption;
        this.visibility    = visibility;
        this.showCompanion = showCompanion;
        this.showRouteMap  = showRouteMap;
        this.showStats     = showStats;
    }

    public String getCaption()       { return caption; }
    public String getVisibility()    { return visibility; }
    public boolean isShowCompanion() { return showCompanion; }
    public boolean isShowRouteMap()  { return showRouteMap; }
    public boolean isShowStats()     { return showStats; }
}
