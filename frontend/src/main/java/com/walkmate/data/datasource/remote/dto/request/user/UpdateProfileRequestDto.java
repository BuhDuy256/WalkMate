package com.walkmate.data.datasource.remote.dto.request.user;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UpdateProfileRequestDto {

    @SerializedName("fullName")
    public final String       fullName;

    @SerializedName("gender")
    public final String       gender;

    @SerializedName("dateOfBirth")
    public final String       dateOfBirth;

    @SerializedName("bio")
    public final String       bio;

    @SerializedName("searchRadius")
    public final int          searchRadius;

    @SerializedName("tags")
    public final List<String> tags;

    public UpdateProfileRequestDto(String fullName, String gender, String dateOfBirth,
                                   String bio, int searchRadius, List<String> tags) {
        this.fullName     = fullName;
        this.gender       = gender;
        this.dateOfBirth  = dateOfBirth;
        this.bio          = bio;
        this.searchRadius = searchRadius;
        this.tags         = tags;
    }
}
