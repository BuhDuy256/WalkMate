package com.walkmate.data.datasource.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;

/**
 * Single Room database for the WalkMate app.
 *
 * Instantiated once as a Singleton in {@code WalkMateApplication} and injected
 * into repositories via the Service Locator pattern — no direct Activity/Fragment
 * reference allowed.
 *
 * Migration strategy: fallbackToDestructiveMigration() — schema bumps will drop
 * and recreate the DB. Switch to explicit Migrations before the first production
 * release to avoid data loss.
 */
@Database(
        entities = {RoutePointEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class WalkMateDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "walkmate_database";

    // Double-checked locking singleton — safe across multiple threads.
    private static volatile WalkMateDatabase instance;

    public abstract RoutePointDao routePointDao();

    public static WalkMateDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (WalkMateDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    WalkMateDatabase.class,
                                    DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
