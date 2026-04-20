package com.walkmate.data.datasource.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.walkmate.data.datasource.local.entity.UserProfileEntity;

/**
 * DAO for the user_profile cache table.
 *
 * Only two operations are needed:
 *   - getProfileById: Phase A instant-read in UserProfileRepositoryImpl.
 *   - upsert: Phase B write-back after a successful network fetch or profile update.
 *
 * No LiveData / reactive queries — the offline-first orchestration is driven
 * by the Repository using ExecutorService, not by Room observers.
 */
@Dao
public interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    UserProfileEntity getProfileById(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(UserProfileEntity entity);
}
