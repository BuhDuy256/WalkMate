package com.walkmate.data.datasource.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity that caches the authenticated user's own profile.
 *
 * Only ONE row ever exists (the logged-in user), keyed by userId.
 * Tags are stored as a comma-separated string to avoid TypeConverter ceremony;
 * splitting/joining is handled entirely inside UserProfileEntityMapper.
 *
 * cachedAtEpochMs records the wall-clock time of the last successful write.
 * It is reserved for future TTL / staleness-indicator logic.
 */
@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey
    @NonNull
    public String userId = "";

    public String fullName;
    public String gender;
    public String dateOfBirth;
    public String avatarUrl;
    public String bio;
    public int    trustScore;
    public double totalDistanceKm;
    public int    totalSessions;

    /** Comma-separated list of personality tags, e.g. "Chatty,Dog Friendly". Null when empty. */
    public String tags;

    /** System.currentTimeMillis() captured at the moment this row was written. */
    public long cachedAtEpochMs;
}
