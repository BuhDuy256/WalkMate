package com.walkmate;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.walkmate.core.event.AuthEventBus;
import com.walkmate.data.datasource.local.WalkMateDatabase;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.api.AuthInterceptor;
import com.walkmate.data.repository.AdminReportRepositoryImpl;
import com.walkmate.data.repository.ChatRepositoryImpl;
import com.walkmate.data.repository.GamificationRepositoryImpl;
import com.walkmate.data.repository.HotspotRepositoryImpl;
import com.walkmate.data.repository.NotificationRepositoryImpl;
import com.walkmate.data.repository.ReviewRepositoryImpl;
import com.walkmate.data.repository.SocialRepositoryImpl;
import com.walkmate.data.datasource.local.GpsQueueStore;
import com.walkmate.data.repository.GpsQueueDrainer;
import com.walkmate.data.repository.TrackingRepositoryImpl;
import com.walkmate.data.repository.TrackingStateRepositoryImpl;
import com.walkmate.data.worker.GpsSyncScheduler;
import com.walkmate.data.repository.UserProfileRepositoryImpl;
import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.data.repository.WalkIntentRepositoryImpl;
import com.walkmate.data.repository.WalkProposalRepositoryImpl;
import com.walkmate.data.repository.WalkSessionRepositoryImpl;
import com.walkmate.domain.chat.ChatRepository;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.hotspot.HotspotRepository;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.social.SocialRepository;
import com.walkmate.domain.tracking.TrackingRepository;
import com.walkmate.domain.tracking.TrackingStateRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.domain.walkproposal.WalkProposalRepository;
import com.walkmate.domain.walksession.WalkSessionRepository;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

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
    private OkHttpClient sharedOkHttpClient;
    private ChatRepository chatRepository;
    private HotspotRepository hotspotRepository;
    private TrackingRepository trackingRepository;
    private TrackingStateRepository trackingStateRepository;
    private GpsQueueStore gpsQueueStore;
    private GpsQueueDrainer gpsQueueDrainer;
    private GpsSyncScheduler gpsSyncScheduler;
    private WalkIntentRepository walkIntentRepository;
    private WalkProposalRepository walkProposalRepository;
    private WalkSessionRepository walkSessionRepository;
    private UserRepository userRepository;
    private UserProfileRepository userProfileRepository;
    private GamificationRepository gamificationRepository;
    private SocialRepository socialRepository;
    private NotificationRepository notificationRepository;
    private ReviewRepository reviewRepository;
    private AdminReportRepository adminReportRepository;

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

        // Re-arm uploads for routes left unsent by a previous process (outage,
        // force-stop, reboot). Safe to call before login: it no-ops without a user.
        resumePendingGpsUploads();
    }

    // ── Singletons ────────────────────────────────────────────────────────────

    public WalkMateDatabase getDatabase() {
        return database;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Returns a lazily-created shared OkHttpClient with the AuthInterceptor attached.
     * Reused by ChatRepositoryImpl so we don't spin up a second connection pool.
     */
    public OkHttpClient getOkHttpClient() {
        if (sharedOkHttpClient == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BASIC);
            sharedOkHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(new AuthInterceptor(sessionManager))
                    .build();
        }
        return sharedOkHttpClient;
    }

    /**
     * Derives the WebSocket base URL from the HTTP base URL defined in BuildConfig.
     * e.g. "http://192.168.x.x:8080/" → "ws://192.168.x.x:8080/api/v1/sessions/"
     */
    public String getBaseWsUrl() {
        String httpBase = com.walkmate.BuildConfig.BASE_URL;
        String wsBase = httpBase
                .replace("https://", "wss://")
                .replace("http://",  "ws://");
        // Ensure trailing slash before appending path
        if (!wsBase.endsWith("/")) wsBase += "/";
        return wsBase + "api/v1/sessions/";
    }

    public ChatRepository getChatRepository() {
        if (chatRepository == null) {
            chatRepository = new ChatRepositoryImpl(getOkHttpClient(), getBaseWsUrl());
        }
        return chatRepository;
    }

    public HotspotRepository getHotspotRepository() {
        if (hotspotRepository == null) {
            hotspotRepository = new HotspotRepositoryImpl(this);
        }
        return hotspotRepository;
    }

    public TrackingRepository getTrackingRepository() {
        if (trackingRepository == null) {
            trackingRepository = new TrackingRepositoryImpl(
                    getDatabase(), getGpsQueueStore(), getGpsQueueDrainer(),
                    getGpsSyncScheduler(), sessionManager);
        }
        return trackingRepository;
    }

    /** Transactional local store for the persistent GPS upload queue. */
    public GpsQueueStore getGpsQueueStore() {
        if (gpsQueueStore == null) {
            gpsQueueStore = new GpsQueueStore(database);
        }
        return gpsQueueStore;
    }

    /** Drains the GPS queue against the v2 endpoints. */
    public GpsQueueDrainer getGpsQueueDrainer() {
        if (gpsQueueDrainer == null) {
            gpsQueueDrainer = new GpsQueueDrainer(getGpsQueueStore(), sessionManager);
        }
        return gpsQueueDrainer;
    }

    /** Enqueues upload work that survives process death. */
    public GpsSyncScheduler getGpsSyncScheduler() {
        if (gpsSyncScheduler == null) {
            gpsSyncScheduler = new GpsSyncScheduler(this);
        }
        return gpsSyncScheduler;
    }

    /**
     * Re-arms the uploader for any route that was left unsent by a previous
     * process. Without this, a walk that ended while the device was offline would
     * sit in Room until the user opened that exact session again.
     *
     * <p>Also called on login, so a queue that survived a logout/login cycle is
     * drained under the correct account.</p>
     */
    public void resumePendingGpsUploads() {
        final String userId = sessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        final GpsQueueStore store = getGpsQueueStore();
        final GpsSyncScheduler scheduler = getGpsSyncScheduler();
        // Room forbids main-thread reads; the lookup is cheap but must be off-UI.
        new Thread(() -> {
            try {
                for (String sessionId : store.sessionsWithOutstanding(userId)) {
                    scheduler.requestDrain(userId, sessionId, false);
                }
            } catch (Exception e) {
                Log.w("WalkMateApp", "resumePendingGpsUploads failed: " + e.getMessage());
            }
        }, "gps-resume").start();
    }

    public TrackingStateRepository getTrackingStateRepository() {
        if (trackingStateRepository == null) {
            trackingStateRepository = new TrackingStateRepositoryImpl(database.trackingStateDao());
        }
        return trackingStateRepository;
    }

    /**
     * Clears all local GPS tracking caches for the given user. Must be called
     * before {@link SessionManager#clearSession()} so the userId is still available.
     * Callers do not need to wait for the callback — the operation is fire-and-forget.
     */
    public void clearTrackingCacheForUser(String userId) {
        if (userId == null || userId.isEmpty()) return;

        // Stop queued uploads first: work enqueued with this user's credentials
        // must not run after the token is cleared, and this user's route must
        // never be uploaded under the next account.
        getGpsSyncScheduler().cancelForUser(userId);

        getTrackingRepository().clearForUser(userId, new com.walkmate.domain.shared.DomainCallback<Void>() {
            @Override public void onSuccess(Void r) {}
            @Override public void onError(Exception e) {
                Log.w("WalkMateApp", "clearTrackingCacheForUser route-points error: " + e.getMessage());
            }
        });
        getTrackingStateRepository().clearForUser(userId, new com.walkmate.domain.shared.DomainCallback<Void>() {
            @Override public void onSuccess(Void r) {}
            @Override public void onError(Exception e) {
                Log.w("WalkMateApp", "clearTrackingCacheForUser state error: " + e.getMessage());
            }
        });
    }

    public WalkIntentRepository getWalkIntentRepository() {
        if (walkIntentRepository == null) {
            walkIntentRepository = new WalkIntentRepositoryImpl(this);
        }
        return walkIntentRepository;
    }

    public WalkProposalRepository getWalkProposalRepository() {
        if (walkProposalRepository == null) {
            walkProposalRepository = new WalkProposalRepositoryImpl(this);
        }
        return walkProposalRepository;
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
            userProfileRepository = new UserProfileRepositoryImpl(this, database.userProfileDao());
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

    public ReviewRepository getReviewRepository() {
        if (reviewRepository == null) {
            reviewRepository = new ReviewRepositoryImpl(this);
        }
        return reviewRepository;
    }

    public AdminReportRepository getAdminReportRepository() {
        if (adminReportRepository == null) {
            adminReportRepository = new AdminReportRepositoryImpl(this);
        }
        return adminReportRepository;
    }
}
