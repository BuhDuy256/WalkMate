package com.walkmate;

import android.app.Application;

import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.repository.TrackingRepositoryImpl;
import com.walkmate.domain.tracking.TrackingRepository;

/**
 * Application-level Service Locator.
 *
 * Heavy singletons (Room DB, Repositories) are created once here and shared
 * across Activities/Fragments via typed getters. This avoids the overhead of
 * Hilt/Dagger while keeping the DI contract explicit and testable.
 *
 * Usage in a ViewModel factory:
 *   WalkMateApplication app = (WalkMateApplication) context.getApplicationContext();
 *   TrackingRepository repo = app.getTrackingRepository();
 */
public class WalkMateApplication extends Application {

    private WalkMateDatabase database;
    private TrackingRepository trackingRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Eagerly instantiate the DB so the first Room query doesn't block the UI.
        database = WalkMateDatabase.getInstance(this);
    }

    // ── Singletons ────────────────────────────────────────────────────────────

    public WalkMateDatabase getDatabase() {
        return database;
    }

    public TrackingRepository getTrackingRepository() {
        if (trackingRepository == null) {
            trackingRepository = new TrackingRepositoryImpl(database.routePointDao());
        }
        return trackingRepository;
    }
}
