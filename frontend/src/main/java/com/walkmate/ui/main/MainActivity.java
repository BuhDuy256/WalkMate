package com.walkmate.ui.main;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.walkmate.R;
import com.walkmate.ui.explore.ExploreFragment;
import com.walkmate.ui.matches.MatchesFragment;
import com.walkmate.ui.profile.ProfileFragment;

/**
 * Global shell Activity. Hosts the three top-level tabs via a BottomNavigationView.
 *
 * Routing strategy: hide/show (not replace) so each tab preserves its state —
 * map position, list scroll, back stack — across tab switches.
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_explore) {
                showTab(ExploreFragment.TAG);
            } else if (id == R.id.tab_matches) {
                showTab(MatchesFragment.TAG);
            } else if (id == R.id.tab_profile) {
                showTab(ProfileFragment.TAG);
            }
            return true;
        });

        // On first launch (not a process-death restore), show the Explore tab.
        // On restore, FragmentManager already has the fragments; the listener
        // will not fire until the user taps, so we re-select the previously
        // active item to trigger a show.
        if (savedInstanceState == null) {
            showTab(ExploreFragment.TAG);
        } else {
            // Re-drive visibility to match whichever item the system restored as checked.
            int checkedId = bottomNav.getSelectedItemId();
            if (checkedId == R.id.tab_explore)       showTab(ExploreFragment.TAG);
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
        for (String t : new String[]{ExploreFragment.TAG, MatchesFragment.TAG, ProfileFragment.TAG}) {
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
        if (ExploreFragment.TAG.equals(tag))  return new ExploreFragment();
        if (MatchesFragment.TAG.equals(tag))  return new MatchesFragment();
        if (ProfileFragment.TAG.equals(tag))  return new ProfileFragment();
        throw new IllegalArgumentException("Unknown tab tag: " + tag);
    }

    // -------------------------------------------------------------------------
    // Public helpers — called by child fragments to drive cross-tab navigation
    // -------------------------------------------------------------------------

    /**
     * Navigates to the Matches tab and selects it in the nav bar.
     * Called, for example, after a WalkIntent is successfully submitted
     * so the user can immediately see it appear in the Finding sub-tab.
     */
    public void switchToMatchesTab() {
        bottomNav.setSelectedItemId(R.id.tab_matches);
    }
}
