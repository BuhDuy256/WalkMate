package com.walkmate.ui.matches;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.walkmate.R;

public class MatchesFragment extends Fragment {

    public static final String TAG = "MatchesFragment";

    private TabLayout subTabLayout;
    private ViewPager2 subTabPager;
    private MatchesPagerAdapter pagerAdapter;

    // Shared ViewModel — sub-fragments access this via ViewModelProvider(requireParentFragment())
    private MatchesViewModel matchesViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_matches, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        subTabLayout = view.findViewById(R.id.subTabLayout);
        subTabPager  = view.findViewById(R.id.subTabPager);

        pagerAdapter = new MatchesPagerAdapter(getChildFragmentManager(), getLifecycle());
        subTabPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(subTabLayout, subTabPager, (tab, position) -> {
            switch (position) {
                case MatchesPagerAdapter.TAB_FINDING:
                    tab.setText(R.string.tab_finding);
                    break;
                case MatchesPagerAdapter.TAB_PROPOSAL:
                    tab.setText(R.string.tab_proposal);
                    break;
                case MatchesPagerAdapter.TAB_SESSION:
                    tab.setText(R.string.tab_session);
                    break;
            }
        }).attach();

        matchesViewModel = new ViewModelProvider(
                this, new MatchesViewModelFactory(requireActivity().getApplication()))
                .get(MatchesViewModel.class);

        matchesViewModel.loadAll();

        // Handle navigation argument — NavController passes this when deep-linking
        // from FCM (via AppEventBus → MainActivity) or from the nav_graph deep link.
        Bundle args = getArguments();
        if (args != null) {
            int scrollToTab = args.getInt("scrollToTab", 0);
            if (scrollToTab != 0) {
                scrollToSubTab(scrollToTab);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Prevent memory leak: ViewPager2 holds a reference to the adapter which
        // holds fragment instances. Null it out when the view is destroyed.
        if (subTabPager != null) {
            subTabPager.setAdapter(null);
        }
        subTabLayout = null;
        subTabPager  = null;
        pagerAdapter = null;
    }

    /**
     * Called by MainActivity when a push notification deep-links to a specific sub-tab.
     * Example: new Proposal arrives → scrollToSubTab(MatchesPagerAdapter.TAB_PROPOSAL)
     */
    public void scrollToSubTab(int index) {
        if (subTabPager != null) {
            subTabPager.setCurrentItem(index, true);
        }
    }
}
