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

    private MatchesViewModel matchesViewModel;

    private final ViewPager2.OnPageChangeCallback pageChangeCallback =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    if (matchesViewModel != null) {
                        matchesViewModel.setLastViewedSubTab(position);
                    }
                }
            };

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
                requireActivity(), new MatchesViewModelFactory(requireActivity().getApplication()))
                .get(MatchesViewModel.class);

        subTabPager.registerOnPageChangeCallback(pageChangeCallback);

        // Phase 5a — handle navigation argument from ExploreFragment (match found)
        // or from AppEventBus (FCM notification via MainActivity).
        Bundle args = getArguments();
        if (args != null) {
            int scrollToTab = args.getInt("scrollToTab", 0);
            if (scrollToTab != 0) {
                subTabPager.post(() -> scrollToSubTab(scrollToTab));
            }
        }

        // Phase 5b — restore last-viewed sub-tab on return visits.
        int lastTab = matchesViewModel.getLastViewedSubTab();
        if (lastTab != MatchesPagerAdapter.TAB_FINDING) {
            subTabPager.post(() -> scrollToSubTab(lastTab));
        }

        // Phase 5c — scroll to Session tab after accepting a proposal.
        matchesViewModel.getScrollToTabEvent().observe(getViewLifecycleOwner(), tabIndex -> {
            if (tabIndex != null) {
                scrollToSubTab(tabIndex);
                matchesViewModel.consumeScrollToTab();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (subTabPager != null) {
            subTabPager.unregisterOnPageChangeCallback(pageChangeCallback);
            subTabPager.setAdapter(null);
        }
        subTabLayout = null;
        subTabPager  = null;
        pagerAdapter = null;
    }

    public void scrollToSubTab(int index) {
        if (subTabPager != null) {
            subTabPager.setCurrentItem(index, true);
        }
    }
}
