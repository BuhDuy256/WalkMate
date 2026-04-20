package com.walkmate.data.datasource.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.dao.TrackingStateDao;
import com.walkmate.data.datasource.local.dao.UserProfileDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.data.datasource.local.entity.TrackingStateEntity;
import com.walkmate.data.datasource.local.entity.UserProfileEntity;

/**
 * Single Room database for the WalkMate app.
 *
 * Version history:
 *   1 → first release (route_points table only)
 *   2 → added tracking_state table for runtime session restore
 *
 * MIGRATION_1_2 creates the new table without touching route_points,
 * so no existing GPS data is lost on upgrade.
 *
 * fallbackToDestructiveMigration() is retained as a last-resort safety net
 * for devices that somehow skipped a version; normal upgrades use the
 * explicit migrations.
 */
@Database(
        entities = {RoutePointEntity.class, TrackingStateEntity.class, UserProfileEntity.class},
        version = 3,
        exportSchema = false
)
public abstract class WalkMateDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "walkmate_database";

    // Double-checked locking singleton — safe across multiple threads.
    private static volatile WalkMateDatabase instance;

    public abstract RoutePointDao routePointDao();
    public abstract TrackingStateDao trackingStateDao();
    public abstract UserProfileDao userProfileDao();

    // ── Migrations ────────────────────────────────────────────────────────────

    /**
     * 1 → 2: Adds the {@code tracking_state} table.
     * The existing {@code route_points} table is untouched.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracking_state` ("
                    + "`sessionId` TEXT NOT NULL, "
                    + "`walkState` TEXT, "
                    + "`walkStartEpochMs` INTEGER NOT NULL, "
                    + "`pausedAccumulatedMs` INTEGER NOT NULL, "
                    + "`pauseStartEpochMs` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`sessionId`))"
            );
        }
    };

    /**
     * 2 → 3: Adds the {@code user_profile} cache table.
     * Existing tables are untouched.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profile` ("
                    + "`userId` TEXT NOT NULL, "
                    + "`fullName` TEXT, "
                    + "`gender` TEXT, "
                    + "`dateOfBirth` TEXT, "
                    + "`avatarUrl` TEXT, "
                    + "`bio` TEXT, "
                    + "`searchRadius` INTEGER NOT NULL DEFAULT 0, "
                    + "`trustScore` INTEGER NOT NULL DEFAULT 0, "
                    + "`totalDistanceKm` REAL NOT NULL DEFAULT 0, "
                    + "`totalSessions` INTEGER NOT NULL DEFAULT 0, "
                    + "`tags` TEXT, "
                    + "`cachedAtEpochMs` INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY(`userId`))"
            );
        }
    };

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static WalkMateDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (WalkMateDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    WalkMateDatabase.class,
                                    DATABASE_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
