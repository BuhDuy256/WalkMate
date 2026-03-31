package com.walkmate;

import android.app.Application;

import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.repository.TrackingRepositoryImpl;
import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.data.repository.WalkSessionRepositoryImpl;
import com.walkmate.domain.tracking.TrackingRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

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
    private WalkSessionRepository walkSessionRepository;
    private UserRepository userRepository;

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

    public WalkSessionRepository getWalkSessionRepository() {
        if (walkSessionRepository == null) {
            walkSessionRepository = new WalkSessionRepositoryImpl();
        }
        return walkSessionRepository;
    }

    public UserRepository getUserRepository() {
        if (userRepository == null) {
            userRepository = new UserRepositoryImpl(this);
        }
        return userRepository;
    }
}
