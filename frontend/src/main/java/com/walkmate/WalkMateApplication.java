package com.walkmate;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.walkmate.core.event.AuthEventBus;
import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.repository.GamificationRepositoryImpl;
import com.walkmate.data.repository.NotificationRepositoryImpl;
import com.walkmate.data.repository.SocialRepositoryImpl;
import com.walkmate.data.repository.TrackingRepositoryImpl;
import com.walkmate.data.repository.UserProfileRepositoryImpl;
import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.data.repository.WalkSessionRepositoryImpl;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.tracking.TrackingRepository;
import com.walkmate.domain.user.UserProfileRepository;
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
 * WalkMateApplication app = (WalkMateApplication)
 * context.getApplicationContext();
 * TrackingRepository repo = app.getTrackingRepository();
 */
public class WalkMateApplication extends Application {

    private WalkMateDatabase database;
    private SessionManager sessionManager;
    private TrackingRepository trackingRepository;
    private WalkSessionRepository walkSessionRepository;
    private UserRepository userRepository;
    private UserProfileRepository userProfileRepository;
    private GamificationRepository gamificationRepository;
    private SocialRepository socialRepository;
    private NotificationRepository notificationRepository;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase first — required for FCM token generation
        // and push notifications to function correctly.
        FirebaseApp.initializeApp(this);
        Log.d("WalkMateApp", "FirebaseApp initialized");

        // Eagerly instantiate the DB and SessionManager so the first Room query
        // and any authenticated network call don't pay a cold-start penalty.
        database = WalkMateDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        // Eagerly initialize AuthEventBus so the singleton exists before any
        // background OkHttp thread could invoke TokenRefreshAuthenticator.
        AuthEventBus.getInstance();
    }

    // ── Singletons ────────────────────────────────────────────────────────────

    public WalkMateDatabase getDatabase() {
        return database;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public TrackingRepository getTrackingRepository() {
        if (trackingRepository == null) {
            trackingRepository = new TrackingRepositoryImpl(database.routePointDao(), sessionManager);
        }
        return trackingRepository;
    }

    public WalkSessionRepository getWalkSessionRepository() {
        if (walkSessionRepository == null) {
            walkSessionRepository = new WalkSessionRepositoryImpl(this);
        }
        return walkSessionRepository;
    }

    public UserRepository getUserRepository() {
        if (userRepository == null) {
            userRepository = new UserRepositoryImpl(this);
        }
        return userRepository;
    }

    public UserProfileRepository getUserProfileRepository() {
        if (userProfileRepository == null) {
            userProfileRepository = new UserProfileRepositoryImpl(this);
        }
        return userProfileRepository;
    }

    public GamificationRepository getGamificationRepository() {
        if (gamificationRepository == null) {
            gamificationRepository = new GamificationRepositoryImpl(this);
        }
        return gamificationRepository;
    }

    public SocialRepository getSocialRepository() {
        if (socialRepository == null) {
            socialRepository = new SocialRepositoryImpl(this);
        }
        return socialRepository;
    }

    public NotificationRepository getNotificationRepository() {
        if (notificationRepository == null) {
            notificationRepository = new NotificationRepositoryImpl(this);
        }
        return notificationRepository;
    }
}
