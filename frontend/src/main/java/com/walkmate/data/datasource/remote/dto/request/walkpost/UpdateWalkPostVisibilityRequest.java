package com.walkmate.data.datasource.remote.dto.request.walkpost;

import com.google.gson.annotations.SerializedName;

public class UpdateWalkPostVisibilityRequest {

    @SerializedName("visibility") private final String visibility;

    public UpdateWalkPostVisibilityRequest(String visibility) {
        this.visibility = visibility;
    }

    public String getVisibility() { return visibility; }
}
