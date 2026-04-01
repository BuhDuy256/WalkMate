package com.walkmate.domain.user;

import java.util.List;

/** Domain entity — a user's editable profile data. */
public class UserProfile {

    private final String       userId;
    private final String       fullName;
    private final String       gender;         // nullable
    private final String       dateOfBirth;    // "YYYY-MM-DD", nullable
    private final String       avatarUrl;      // nullable
    private final String       bio;            // nullable
    private final int          searchRadius;   // metres
    private final int          trustScore;
    private final double       totalDistanceKm;
    private final int          totalSessions;
    private final List<String> tags;

    public UserProfile(String userId, String fullName, String gender, String dateOfBirth,
                       String avatarUrl, String bio, int searchRadius,
                       int trustScore, double totalDistanceKm, int totalSessions,
                       List<String> tags) {
        this.userId          = userId;
        this.fullName        = fullName;
        this.gender          = gender;
        this.dateOfBirth     = dateOfBirth;
        this.avatarUrl       = avatarUrl;
        this.bio             = bio;
        this.searchRadius    = searchRadius;
        this.trustScore      = trustScore;
        this.totalDistanceKm = totalDistanceKm;
        this.totalSessions   = totalSessions;
        this.tags            = tags;
    }

    public String       getUserId()          { return userId; }
    public String       getFullName()        { return fullName; }
    public String       getGender()          { return gender; }
    public String       getDateOfBirth()     { return dateOfBirth; }
    public String       getAvatarUrl()       { return avatarUrl; }
    public String       getBio()             { return bio; }
    public int          getSearchRadius()    { return searchRadius; }
    public int          getTrustScore()      { return trustScore; }
    public double       getTotalDistanceKm() { return totalDistanceKm; }
    public int          getTotalSessions()   { return totalSessions; }
    public List<String> getTags()            { return tags; }
}
