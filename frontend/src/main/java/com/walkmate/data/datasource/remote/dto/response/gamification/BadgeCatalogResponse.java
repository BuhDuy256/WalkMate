package com.walkmate.data.datasource.remote.dto.response.gamification;

import com.google.gson.annotations.SerializedName;

public class BadgeCatalogResponse {

    @SerializedName("name")
    public String name;

    @SerializedName("displayName")
    public String displayName;

    @SerializedName("description")
    public String description;

    @SerializedName("rarity")
    public String rarity;

    @SerializedName("category")
    public String category;
}
