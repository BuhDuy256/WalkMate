package com.walkmate.data.datasource.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.walkmate.data.datasource.local.entity.RoutePointEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

/**
 * Verifies the 4 → 5 upgrade on a database that already holds unsynced GPS rows.
 *
 * <p>This is the migration the plan calls out explicitly: replacing
 * {@code fallbackToDestructiveMigration()} with a real migration only counts as a
 * fix if an <em>existing, non-empty</em> database survives it. The v4 database is
 * built here with raw SQL (the project never exported a v4 schema, so
 * {@code MigrationTestHelper} cannot synthesise one) and then opened through Room,
 * which runs the migration and validates the resulting schema against the v5
 * entities.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class WalkMateDatabaseMigrationTest {

    private static final String DB_NAME = "migration-4-5-test.db";

    private Context context;
    private File dbFile;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
        dbFile = context.getDatabasePath(DB_NAME);
        //noinspection ResultOfMethodCallIgnored
        dbFile.getParentFile().mkdirs();
    }

    @After
    public void tearDown() {
        context.deleteDatabase(DB_NAME);
    }

    /** Creates the schema exactly as version 4 left it. */
    private void createV4DatabaseWithUnsyncedRow() {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS `route_points` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`sessionId` TEXT, `userId` TEXT, "
                    + "`lat` REAL NOT NULL, `lng` REAL NOT NULL, "
                    + "`timestamp` INTEGER NOT NULL, `accuracy` REAL NOT NULL, "
                    + "`isSynced` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_route_points_sessionId_userId_timestamp` "
                    + "ON `route_points` (`sessionId`, `userId`, `timestamp`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `tracking_state` ("
                    + "`sessionId` TEXT NOT NULL, `userId` TEXT NOT NULL, "
                    + "`walkState` TEXT, `walkStartEpochMs` INTEGER NOT NULL, "
                    + "`pausedAccumulatedMs` INTEGER NOT NULL, "
                    + "`pauseStartEpochMs` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`sessionId`, `userId`))");

            db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` ("
                    + "`userId` TEXT NOT NULL, `fullName` TEXT, `gender` TEXT, "
                    + "`dateOfBirth` TEXT, `avatarUrl` TEXT, `bio` TEXT, "
                    + "`trustScore` INTEGER NOT NULL, `totalDistanceKm` REAL NOT NULL, "
                    + "`totalSessions` INTEGER NOT NULL, `tags` TEXT, "
                    + "`cachedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`userId`))");

            // Two unsynced points recorded by the old (v1) push path.
            db.execSQL("INSERT INTO `route_points` "
                    + "(sessionId, userId, lat, lng, timestamp, accuracy, isSynced) "
                    + "VALUES ('s-1', 'u-1', 10.5, 106.5, 1000, 5.0, 0)");
            db.execSQL("INSERT INTO `route_points` "
                    + "(sessionId, userId, lat, lng, timestamp, accuracy, isSynced) "
                    + "VALUES ('s-1', 'u-1', 10.6, 106.6, 2000, 4.0, 0)");

            db.setVersion(4);
        } finally {
            db.close();
        }
    }

    @Test
    public void migration_4_to_5_preservesUnsyncedRowsAndAddsStreamIdentity() throws Exception {
        createV4DatabaseWithUnsyncedRow();

        WalkMateDatabase room = Room.databaseBuilder(context, WalkMateDatabase.class, DB_NAME)
                .addMigrations(WalkMateDatabase.MIGRATION_1_2,
                        WalkMateDatabase.MIGRATION_2_3,
                        WalkMateDatabase.MIGRATION_3_4,
                        WalkMateDatabase.MIGRATION_4_5)
                .allowMainThreadQueries()
                .build();

        try {
            // Opening must succeed at all: with the destructive fallback removed,
            // a migration that does not match the entities now fails loudly here.
            List<RoutePointEntity> points =
                    room.routePointDao().getPointsBySessionAndUser("s-1", "u-1").getValue();
            assertNull("LiveData has no value until observed", points);

            // Read the rows through the new schema.
            int unsynced = room.routePointDao().getUnsyncedCount("s-1", "u-1");
            assertEquals("existing unsynced rows must survive the upgrade", 2, unsynced);

            // The legacy rows keep their data and simply carry NULL stream identity,
            // which is what marks them as pre-v2 and keeps them out of v2 sealing.
            assertEquals(2, room.routePointDao().countUnsequencedLegacy("s-1", "u-1"));
            assertEquals(0, room.routePointDao().getUnbatchedPoints("s-1", "u-1", 10).size());

            // The new tables exist and start empty.
            assertNull(room.gpsStreamDao().find("s-1", "u-1"));
            assertEquals(0, room.gpsBatchDao().countOutstanding("s-1", "u-1"));

            // Writing a v2 point alongside the legacy rows works and gets sequence 1.
            GpsQueueStore store = new GpsQueueStore(room);
            String streamId = store.ensureStream("s-1", "u-1");
            assertNotNull(streamId);
            long seq = store.saveAcceptedPoint("s-1", "u-1", streamId, 10.7, 106.7, 3000, 3.0f);
            assertEquals(1L, seq);

            assertEquals("legacy rows are untouched by new writes",
                    2, room.routePointDao().countUnsequencedLegacy("s-1", "u-1"));
            assertEquals(1, room.routePointDao().countUnbatched("s-1", "u-1"));
        } finally {
            room.close();
        }
    }

    @Test
    public void migration_4_to_5_createsTheV2TablesAndIndices() throws Exception {
        createV4DatabaseWithUnsyncedRow();

        WalkMateDatabase room = Room.databaseBuilder(context, WalkMateDatabase.class, DB_NAME)
                .addMigrations(WalkMateDatabase.MIGRATION_1_2,
                        WalkMateDatabase.MIGRATION_2_3,
                        WalkMateDatabase.MIGRATION_3_4,
                        WalkMateDatabase.MIGRATION_4_5)
                .allowMainThreadQueries()
                .build();
        room.getOpenHelper().getWritableDatabase().close();
        room.close();

        // Inspect the migrated file directly: the schema Room validated on open is
        // the schema that is actually on disk.
        SQLiteDatabase raw = SQLiteDatabase.openDatabase(dbFile.getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
        try {
            assertTrue("gps_stream table must exist", tableExists(raw, "gps_stream"));
            assertTrue("gps_batches table must exist", tableExists(raw, "gps_batches"));
            assertTrue("route_points must gain streamId",
                    columnExists(raw, "route_points", "streamId"));
            assertTrue("route_points must gain sequence",
                    columnExists(raw, "route_points", "sequence"));
            assertTrue("route_points must gain batchId",
                    columnExists(raw, "route_points", "batchId"));
            assertTrue("v2 sequence uniqueness index must exist",
                    indexExists(raw, "index_route_points_stream_sequence"));
        } finally {
            raw.close();
        }
    }

    // ── Schema introspection helpers ──────────────────────────────────────────

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{table})) {
            return c.moveToFirst();
        }
    }

    private static boolean indexExists(SQLiteDatabase db, String index) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
                new String[]{index})) {
            return c.moveToFirst();
        }
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String column) {
        try (Cursor c = db.rawQuery("PRAGMA table_info(`" + table + "`)", null)) {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameIdx))) {
                    return true;
                }
            }
            return false;
        }
    }
}
