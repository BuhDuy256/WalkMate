package com.walkmate.ui.main;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.walkmate.R;
import com.walkmate.ui.explore.ExploreFragment;
import com.walkmate.ui.home.HomeFragment;
import com.walkmate.ui.matches.MatchesFragment;
import com.walkmate.ui.profile.ProfileFragment;

/**
 * Global shell Activity. Hosts the three top-level tabs via a BottomNavigationView.
 *
 * Routing strategy: hide/show (not replace) so each tab preserves its state —
 * map position, list scroll, back stack — across tab switches.
 *
 * Implements {@link HomeFragment.OnHomeActionListener} to receive navigation
 * commands from the Home Dashboard without the Fragment holding a direct
 * Activity reference. This keeps HomeFragment independently testable and
 * decoupled from the concrete host.
 */
public class MainActivity extends AppCompatActivity
        implements HomeFragment.OnHomeActionListener {

    private BottomNavigationView bottomNav;
    private int cachedBottomNavHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable edge-to-edge globally so the Explore map can render behind the
        // status bar. Set once here — no individual Fragment needs to toggle it.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_explore) {
                showTab(HomeFragment.TAG);
            } else if (id == R.id.tab_matches) {
                showTab(MatchesFragment.TAG);
            } else if (id == R.id.tab_profile) {
                showTab(ProfileFragment.TAG);
            }
            return true;
        });

        // On first launch (not a process-death restore), show the Home Dashboard.
        // On restore, FragmentManager already has the fragments; the listener
        // will not fire until the user taps, so we re-select the previously
        // active item to trigger a show.
        if (savedInstanceState == null) {
            showTab(HomeFragment.TAG);
        } else {
            // Re-drive visibility to match whichever item the system restored as checked.
            int checkedId = bottomNav.getSelectedItemId();
            if (checkedId == R.id.tab_explore)       showTab(HomeFragment.TAG);
            else if (checkedId == R.id.tab_matches)  showTab(MatchesFragment.TAG);
            else if (checkedId == R.id.tab_profile)  showTab(ProfileFragment.TAG);
        }
    }

    // -------------------------------------------------------------------------
    // Tab routing
    // -------------------------------------------------------------------------

    private void showTab(String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Hide every tab fragment that is currently visible.
        for (String t : new String[]{HomeFragment.TAG, ExploreFragment.TAG, MatchesFragment.TAG, ProfileFragment.TAG}) {
            Fragment f = fm.findFragmentByTag(t);
            if (f != null && !f.isHidden()) {
                ft.hide(f);
            }
        }

        // Show or lazily create the requested tab.
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            target = createFragmentForTag(tag);
            ft.add(R.id.tabContentContainer, target, tag);
        } else {
            ft.show(target);
        }

        ft.commitNow();
    }

    private Fragment createFragmentForTag(String tag) {
        if (HomeFragment.TAG.equals(tag))     return new HomeFragment();
        if (ExploreFragment.TAG.equals(tag))  return new ExploreFragment();
        if (MatchesFragment.TAG.equals(tag))  return new MatchesFragment();
        if (ProfileFragment.TAG.equals(tag))  return new ProfileFragment();
        throw new IllegalArgumentException("Unknown tab tag: " + tag);
    }

    // -------------------------------------------------------------------------
    // Public helpers — called by child fragments to drive cross-tab navigation
    // -------------------------------------------------------------------------

    // ── HomeFragment.OnHomeActionListener ─────────────────────────────────────

    /**
     * Called by HomeFragment when the user taps "Find a WalkMate Now".
     *
     * Implementation note: we call showTab() directly rather than going through
     * bottomNav.setSelectedItemId(R.id.tab_explore), because tab_explore is now
     * mapped to HomeFragment (Phase B). Calling showTab(ExploreFragment.TAG)
     * directly shows the Explore map without altering the bottom nav selection —
     * the user is "drilling into" the explore flow from the Home tab, not
     * switching to a different root tab.
     */
    @Override
    public void switchToExplore() {
        showTab(ExploreFragment.TAG);
    }

    // ── Public helpers — called by child fragments ─────────────────────────────

    /**
     * Navigates to the Matches tab and selects it in the nav bar.
     * Called, for example, after a WalkIntent is successfully submitted
     * so the user can immediately see it appear in the Finding sub-tab.
     */
    public void switchToMatchesTab() {
        bottomNav.setSelectedItemId(R.id.tab_matches);
    }

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
