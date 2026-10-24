package com.walkmate.data.datasource.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.walkmate.data.datasource.local.dao.GpsBatchDao;
import com.walkmate.data.datasource.local.dao.GpsStreamDao;
import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.dao.TrackingStateDao;
import com.walkmate.data.datasource.local.dao.UserProfileDao;
import com.walkmate.data.datasource.local.entity.GpsBatchEntity;
import com.walkmate.data.datasource.local.entity.GpsStreamEntity;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.data.datasource.local.entity.TrackingStateEntity;
import com.walkmate.data.datasource.local.entity.UserProfileEntity;

/**
 * Single Room database for the WalkMate app.
 *
 * Version history:
 *   1 → first release (route_points table only)
 *   2 → added tracking_state table for runtime session restore
 *   3 → added user_profile cache
 *   4 → dropped user_profile.searchRadius
 *   5 → GPS v2: route_points gains stream identity, plus gps_stream and
 *       gps_batches (the persistent upload queue)
 *
 * Every upgrade is an explicit migration. {@code fallbackToDestructiveMigration()}
 * used to be the last-resort safety net, but it silently deletes unsynced GPS
 * routes on a version mismatch — which is exactly the data the persistent queue
 * exists to protect. It is removed on purpose: a missing migration must fail
 * loudly in development instead of destroying a walk that was never uploaded.
 */
@Database(
        entities = {
                RoutePointEntity.class,
                TrackingStateEntity.class,
                UserProfileEntity.class,
                GpsStreamEntity.class,
                GpsBatchEntity.class
        },
        version = 5,
        exportSchema = true
)
public abstract class WalkMateDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "walkmate_database";

    // Double-checked locking singleton — safe across multiple threads.
    private static volatile WalkMateDatabase instance;

    public abstract RoutePointDao routePointDao();
    public abstract TrackingStateDao trackingStateDao();
    public abstract UserProfileDao userProfileDao();
    public abstract GpsStreamDao gpsStreamDao();
    public abstract GpsBatchDao gpsBatchDao();

    // ── Migrations ────────────────────────────────────────────────────────────

    /**
     * 1 → 2: Adds the {@code tracking_state} table.
     * The existing {@code route_points} table is untouched.
     *
     * <p>This creates the table in its <em>current</em> shape rather than the
     * shape it had at v2. The original version keyed on {@code sessionId} alone
     * and had no {@code userId}; when that drifted from the entity the only thing
     * keeping upgrades alive was {@code fallbackToDestructiveMigration()}, which
     * silently wiped the database. Writing the final shape here means a v1 device
     * upgrades to v5 without ever needing that escape hatch.</p>
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracking_state` ("
                    + "`sessionId` TEXT NOT NULL, "
                    + "`userId` TEXT NOT NULL, "
                    + "`walkState` TEXT, "
                    + "`walkStartEpochMs` INTEGER NOT NULL, "
                    + "`pausedAccumulatedMs` INTEGER NOT NULL, "
                    + "`pauseStartEpochMs` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`sessionId`, `userId`))"
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

    /**
     * 3 → 4: Removes the {@code searchRadius} column from {@code user_profile}.
     * SQLite < 3.35 (Android < 12) cannot drop columns, so the table is
     * recreated without it. All data is discarded; the cache is repopulated
     * on the next profile fetch.
     *
     * <p>The DDL here mirrors Room's expected schema exactly, including the
     * absence of column defaults: Room compares a column's default value during
     * migration validation, so a stray {@code DEFAULT 0} makes the upgrade fail.</p>
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS `user_profile`");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profile` ("
                    + "`userId` TEXT NOT NULL, "
                    + "`fullName` TEXT, "
                    + "`gender` TEXT, "
                    + "`dateOfBirth` TEXT, "
                    + "`avatarUrl` TEXT, "
                    + "`bio` TEXT, "
                    + "`trustScore` INTEGER NOT NULL, "
                    + "`totalDistanceKm` REAL NOT NULL, "
                    + "`totalSessions` INTEGER NOT NULL, "
                    + "`tags` TEXT, "
                    + "`cachedAtEpochMs` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`userId`))"
            );
        }
    };

    // ── Singleton ─────────────────────────────────────────────────────────────

    /**
     * 4 → 5: GPS v2 stream identity + persistent upload queue.
     *
     * <p>Additive only: existing {@code route_points} rows keep their data and
     * simply carry NULL stream/sequence/batchId, which marks them as legacy v1
     * rows. SQLite permits multiple NULLs in a UNIQUE index, so the v2 sequence
     * uniqueness (sessionId, userId, streamId, sequence) never collides with
     * them. No table is dropped or rewritten.</p>
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // ── route_points: v2 identity columns (nullable for legacy rows) ──
            database.execSQL("ALTER TABLE `route_points` ADD COLUMN `streamId` TEXT");
            database.execSQL("ALTER TABLE `route_points` ADD COLUMN `sequence` INTEGER");
            database.execSQL("ALTER TABLE `route_points` ADD COLUMN `batchId` TEXT");

            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_route_points_stream_sequence` "
                    + "ON `route_points` (`sessionId`, `userId`, `streamId`, `sequence`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_route_points_batchId` "
                    + "ON `route_points` (`batchId`)"
            );

            // ── gps_stream: one stream cursor per (session, user) ─────────────
            // DDL matches Room's expected schema; no column defaults are declared
            // because Room compares default values during migration validation.
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gps_stream` ("
                    + "`sessionId` TEXT NOT NULL, "
                    + "`userId` TEXT NOT NULL, "
                    + "`streamId` TEXT, "
                    + "`nextSequence` INTEGER NOT NULL, "
                    + "`syncState` TEXT, "
                    + "`finalSequence` INTEGER, "
                    + "`recordedEndedAt` INTEGER, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`sessionId`, `userId`))"
            );

            // ── gps_batches: the persistent upload queue ──────────────────────
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gps_batches` ("
                    + "`batchId` TEXT NOT NULL, "
                    + "`sessionId` TEXT, "
                    + "`userId` TEXT, "
                    + "`streamId` TEXT, "
                    + "`seqStart` INTEGER NOT NULL, "
                    + "`seqEnd` INTEGER NOT NULL, "
                    + "`payloadHash` TEXT, "
                    + "`state` TEXT, "
                    + "`retryCount` INTEGER NOT NULL, "
                    + "`nextAttemptAt` INTEGER NOT NULL, "
                    + "`leaseUntil` INTEGER NOT NULL, "
                    + "`blockReason` TEXT, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`ackedAt` INTEGER, "
                    + "PRIMARY KEY(`batchId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gps_batches_owner` "
                    + "ON `gps_batches` (`sessionId`, `userId`, `state`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_gps_batches_stream_range` "
                    + "ON `gps_batches` (`streamId`, `seqStart`)"
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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }
        return instance;
    }
}
