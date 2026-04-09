package com.walkmate.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
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
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private NavController navController;
    private int cachedBottomNavHeight = 0;

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
        bottomNav.setOnItemSelectedListener(item -> {
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (item.getItemId() == R.id.homeFragment) {
                // Clear any restored backstack on top of homeFragment
                navController.popBackStack(R.id.homeFragment, false);
            }
            return handled;
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
     * Observes the process-singleton AppEventBus for foreground push events.
     *
     * When a MATCH_FOUND push arrives while the app is visible, navigate to
     * MatchesFragment and scroll directly to the Proposal sub-tab so the user
     * can see the incoming match without any extra taps.
     */
    private void observeAppEventBus() {
        AppEventBus.get().observe().observe(this, event -> {
            if (event == null) return;

            if (event.type == AppEvent.Type.MATCH_FOUND) {
                Bundle args = new Bundle();
                args.putInt("scrollToTab", MatchesPagerAdapter.TAB_PROPOSAL);

                // Navigate to matchesFragment, popping the home back-stack to avoid
                // accumulating duplicate entries.
                navController.navigate(
                        R.id.matchesFragment,
                        args,
                        new NavOptions.Builder()
                                .setPopUpTo(R.id.homeFragment, false)
                                .build()
                );

                // Consume so a config-change (rotation) doesn't re-trigger the navigation.
                AppEventBus.get().consumeEvent();
            }
        });
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
