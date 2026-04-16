package com.walkmate.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.event.AppEvent;
import com.walkmate.core.event.AppEventBus;
import com.walkmate.core.event.AuthEvent;
import com.walkmate.core.event.AuthEventBus;
import com.walkmate.ui.auth.AuthActivity;
import com.walkmate.ui.matches.MatchesPagerAdapter;
import com.walkmate.ui.social.friends.FriendsPagerAdapter;

/**
 * Global shell Activity. Hosts the four destinations via a NavHostFragment
 * wired to a BottomNavigationView.
 *
 * Navigation strategy: Jetpack NavController + NavigationUI.setupWithNavController().
 * Navigation 2.7.7 automatically saves/restores fragment state when the user
 * switches between bottom-nav tabs (multiple back-stack support), preserving
 * the Explore map position, Matches scroll state, etc.
 *
 * The Activity is also responsible for:
 *   1. Handling bottom-nav visibility requests from ExploreFragment.
 *   2. Observing AppEventBus for foreground FCM events and routing to the
 *      correct destination with arguments.
 *   3. Handling background FCM deep-links via Intent extras placed by the
 *      WalkMateFcmService PendingIntent (see handleFcmIntent).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /** Intent extra key: pass a userId String to navigate directly to PublicProfileFragment. */
    public static final String EXTRA_NAVIGATE_USER_ID = "navigate_user_id";

    private BottomNavigationView bottomNav;
    private NavController navController;
    private int cachedBottomNavHeight = 0;

    /**
     * Handles the POST_NOTIFICATIONS runtime permission result (API 33+).
     * Must be registered before onCreate() completes — defined as a field initialiser
     * so it is registered at construction time, satisfying the Activity lifecycle contract.
     */
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                        } else {
                            Toast.makeText(
                                    this,
                                    "Notification permission is required to receive walk updates.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable edge-to-edge globally so the Explore map can render behind the
        // status bar. Set once here — no individual Fragment needs to toggle it.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        // Wire BottomNavigationView ↔ NavController.
        // Each menu item ID must equal the corresponding destination ID in nav_graph.xml.
        // NavigationUI handles selection, back-stack management, and state restoration.
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Custom override: The user requested that tapping the Home tab from another
        // tab should always display the HomeFragment, not any sub-fragments (like
        // ExploreFragment) that were previously left open. NavigationUI's default
        // behavior restores the state; we force it to pop back to the root of the tab.
        // Fix: use a single NavOptions-based navigate instead of two back-to-back
        // NavController calls, which caused a race condition / animation flicker.
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.homeFragment) {
                navController.navigate(R.id.homeFragment,
                        null,
                        new NavOptions.Builder()
                                .setPopUpTo(R.id.homeFragment, true)
                                .setLaunchSingleTop(true)
                                .build());
                return true;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // Restore bottom-nav visibility when navigating away from ExploreFragment
        // (e.g., the user switches to Matches while the explore flow was active).
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            // Hide bottom nav for full-screen sub-pages (Explore form, Notifications).
            // Explore manages its own visibility via ExploreFragment.renderState().
            if (destId == R.id.notificationFragment) {
                setBottomNavVisibility(false);
            } else if (destId != R.id.exploreFragment) {
                // Ensure nav bar is always visible on non-explore, non-notification destinations.
                setBottomNavVisibility(true);
            }
        });

        observeAppEventBus();
        observeAuthEventBus();

        // Request POST_NOTIFICATIONS permission on API 33+ once the user has a valid
        // session — avoids prompting the permission dialog on the auth/login screen.
        WalkMateApplication app = (WalkMateApplication) getApplication();
        if (app.getSessionManager().hasUsableAccessToken()) {
            askNotificationPermission();
        }

        if (savedInstanceState == null) {
            // Handle deep-link from TrackingScreenActivity (or any external caller) that
            // wants to open a public profile without using FragmentManager.
            handleNavigateIntent(getIntent());
            // Handle background FCM tap: WalkMateFcmService places type/ID extras on the
            // PendingIntent that launches this Activity from the system tray.
            handleFcmIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigateIntent(intent);
        handleFcmIntent(intent);
    }

    /**
     * If the launching Intent carries {@link #EXTRA_NAVIGATE_USER_ID}, navigate to
     * {@code publicProfileFragment} with that userId. The extra is consumed immediately
     * so a config-change (rotation) does not re-trigger the navigation.
     */
    private void handleNavigateIntent(Intent intent) {
        if (intent == null) return;
        String userId = intent.getStringExtra(EXTRA_NAVIGATE_USER_ID);
        if (userId == null || userId.isEmpty()) return;
        intent.removeExtra(EXTRA_NAVIGATE_USER_ID);
        Bundle args = new Bundle();
        args.putString("userId", userId);
        navController.navigate(R.id.publicProfileFragment, args);
    }

    /**
     * Handles a background FCM deep-link. When the user taps a system-tray notification
     * posted by {@code WalkMateFcmService}, the PendingIntent delivers
     * {@link AppEvent#EXTRA_FCM_TYPE} (and optionally
     * {@link AppEvent#EXTRA_FCM_PROPOSAL_ID} / {@link AppEvent#EXTRA_FCM_SESSION_ID})
     * as Intent extras. This method reads those extras and routes to the same
     * destination that the foreground AppEventBus handler would navigate to.
     *
     * <p>Extras are consumed immediately to prevent re-delivery on rotation.</p>
     */
    private void handleFcmIntent(Intent intent) {
        if (intent == null) return;
        String fcmTypeName = intent.getStringExtra(AppEvent.EXTRA_FCM_TYPE);
        if (fcmTypeName == null) return;

        intent.removeExtra(AppEvent.EXTRA_FCM_TYPE);
        String proposalId = intent.getStringExtra(AppEvent.EXTRA_FCM_PROPOSAL_ID);
        String sessionId  = intent.getStringExtra(AppEvent.EXTRA_FCM_SESSION_ID);
        intent.removeExtra(AppEvent.EXTRA_FCM_PROPOSAL_ID);
        intent.removeExtra(AppEvent.EXTRA_FCM_SESSION_ID);

        AppEvent.Type eventType;
        try {
            eventType = AppEvent.Type.valueOf(fcmTypeName);
        } catch (IllegalArgumentException e) {
            return; // unknown type from a future backend version — ignore
        }

        routeToDestination(eventType, proposalId, sessionId);
    }

    // ── Notification permission (API 33+) ────────────────────────────────────

    /**
     * Requests the POST_NOTIFICATIONS runtime permission on Android 13+ (TIRAMISU).
     *
     * <p>On API < 33 the method is a no-op — the manifest declaration alone is
     * sufficient for older SDKs and no runtime grant dialog exists.</p>
     *
     * <p>If the permission is already granted (user previously accepted, or API < 33)
     * the launcher is not invoked, preventing an unwanted dialog on every launch.</p>
     */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return; // No runtime permission needed below API 33
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return; // Already granted — nothing to do
        }
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // ── Forced-logout handling ────────────────────────────────────────────────

    /**
     * When TokenRefreshAuthenticator can no longer refresh the session, it posts
     * FORCE_LOGOUT on AuthEventBus. Clear the session and restart from AuthActivity.
     */
    private void observeAuthEventBus() {
        AuthEventBus.getInstance().observe().observe(this, event -> {
            if (event == AuthEvent.FORCE_LOGOUT) {
                ((WalkMateApplication) getApplication()).getSessionManager().clearSession();
                AuthEventBus.getInstance().consumeEvent();
                Intent intent = new Intent(this, AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
    }

    // ── FCM foreground event routing ──────────────────────────────────────────

    /**
     * Observes the process-singleton AppEventBus for foreground push events and
     * routes each event type to the correct destination.
     *
     * <ul>
     *   <li>MATCH_FOUND / PROPOSAL_RECEIVED / INVITE_SENT / PROPOSAL_ACCEPTED
     *       → Matches → Proposal tab (proposalId forwarded if present)</li>
     *   <li>SESSION_CONFIRMED / SESSION_ACTIVE
     *       → Matches → Session tab (sessionId forwarded if present)</li>
     *   <li>FRIEND_REQUEST_RECEIVED → Friends → Incoming tab</li>
     *   <li>FRIEND_REQUEST_ACCEPTED → Friends → Friends tab</li>
     *   <li>FRIEND_REQUEST_DECLINED → Friends → Outgoing (Sent) tab</li>
     * </ul>
     */
    private void observeAppEventBus() {
        AppEventBus.get().observe().observe(this, event -> {
            if (event == null) return;

            String proposalId = event.payload.get(AppEvent.KEY_PROPOSAL_ID);
            String sessionId  = event.payload.get(AppEvent.KEY_SESSION_ID);

            routeToDestination(event.type, proposalId, sessionId);

            // Consume so a config-change (rotation) doesn't re-trigger the navigation.
            AppEventBus.get().consumeEvent();
        });
    }

    /**
     * Shared routing logic used by both the foreground AppEventBus observer and
     * the background FCM Intent handler. All navigation uses
     * {@code setPopUpTo(homeFragment, false)} so the user lands on a clean back stack.
     *
     * @param type       the FCM event type
     * @param proposalId optional proposalId (included in the Bundle for MatchesFragment)
     * @param sessionId  optional sessionId (included in the Bundle for MatchesFragment)
     */
    private void routeToDestination(AppEvent.Type type,
                                    String proposalId,
                                    String sessionId) {
        NavOptions popToHome = new NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false)
                .build();

        switch (type) {
            case MATCH_FOUND:
            case PROPOSAL_RECEIVED:
            case INVITE_SENT:
            case PROPOSAL_ACCEPTED: {
                Bundle args = new Bundle();
                args.putInt("scrollToTab", MatchesPagerAdapter.TAB_PROPOSAL);
                if (proposalId != null) args.putString(AppEvent.KEY_PROPOSAL_ID, proposalId);
                navController.navigate(R.id.matchesFragment, args, popToHome);
                break;
            }
            case SESSION_CONFIRMED:
            case SESSION_ACTIVE: {
                Bundle args = new Bundle();
                args.putInt("scrollToTab", MatchesPagerAdapter.TAB_SESSION);
                if (sessionId != null) args.putString(AppEvent.KEY_SESSION_ID, sessionId);
                navController.navigate(R.id.matchesFragment, args, popToHome);
                break;
            }
            case FRIEND_REQUEST_RECEIVED: {
                Bundle args = new Bundle();
                args.putInt("scrollToTab", FriendsPagerAdapter.TAB_INCOMING);
                navController.navigate(R.id.friendsFragment, args, popToHome);
                break;
            }
            case FRIEND_REQUEST_ACCEPTED: {
                Bundle args = new Bundle();
                args.putInt("scrollToTab", FriendsPagerAdapter.TAB_FRIENDS);
                navController.navigate(R.id.friendsFragment, args, popToHome);
                break;
            }
            case FRIEND_REQUEST_DECLINED: {
                Bundle args = new Bundle();
                args.putInt("scrollToTab", FriendsPagerAdapter.TAB_OUTGOING);
                navController.navigate(R.id.friendsFragment, args, popToHome);
                break;
            }
        }
    }

    // ── Public helper — called by ExploreFragment to control bottom-nav UI ─────

    /**
     * Slides the bottom nav bar in or out with a 180 ms translate animation.
     *
     * Called by ExploreFragment when the state machine transitions between
     * WELCOME (nav visible) and SETUP / SCANNING (nav hidden so the bottom
     * sheet can expand full-height without the nav bar in the way).
     */
    public void setBottomNavVisibility(boolean visible) {
        // Cancel any in-flight animation to avoid conflicts on rapid state changes.
        bottomNav.animate().cancel();

        if (visible) {
            // Restore translationY to 0 before making visible so the slide-up
            // starts from off-screen rather than from the current (possibly mid-)position.
            if (bottomNav.getVisibility() != View.VISIBLE) {
                if (cachedBottomNavHeight > 0) {
                    bottomNav.setTranslationY(cachedBottomNavHeight);
                }
                bottomNav.setVisibility(View.VISIBLE);
            }
            bottomNav.animate().translationY(0).setDuration(180).start();
        } else {
            // Cache the height while the view is still laid-out and visible.
            if (cachedBottomNavHeight == 0) {
                cachedBottomNavHeight = bottomNav.getHeight();
            }
            float slideBy = cachedBottomNavHeight > 0
                    ? cachedBottomNavHeight
                    : bottomNav.getMeasuredHeight();
            bottomNav.animate()
                    .translationY(slideBy)
                    .setDuration(180)
                    .withEndAction(() -> bottomNav.setVisibility(View.GONE))
                    .start();
        }
    }
}
