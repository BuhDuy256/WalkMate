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

    // Shared ViewModel — scoped to Activity so it survives tab switches.
    // Sub-fragments access it via ViewModelProvider(requireActivity()).
    private MatchesViewModel matchesViewModel;

    // Phase 5 — prevents auto-scroll from firing more than once per session.
    private boolean hasAutoScrolledToProposal = false;

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

        // Scope to Activity — VM survives tab switches and sub-fragments share the same instance.
        matchesViewModel = new ViewModelProvider(
                requireActivity(), new MatchesViewModelFactory(requireActivity().getApplication()))
                .get(MatchesViewModel.class);

        // Only load when there is no cached data yet.
        if (matchesViewModel.getUiState().getValue() == null
                || matchesViewModel.getUiState().getValue().isLoading()) {
            matchesViewModel.loadAll();
        }

        // Phase 5a — handle navigation argument from ExploreFragment (match found)
        // or from AppEventBus (FCM notification via MainActivity).
        Bundle args = getArguments();
        if (args != null) {
            int scrollToTab = args.getInt("scrollToTab", 0);
            if (scrollToTab != 0) {
                subTabPager.post(() -> scrollToSubTab(scrollToTab));
            }
        }

        // Phase 5b — auto-scroll to Proposal tab when proposals are loaded for the first time.
        matchesViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (!hasAutoScrolledToProposal
                    && state.getProposals() != null
                    && !state.getProposals().isEmpty()) {
                hasAutoScrolledToProposal = true;
                scrollToSubTab(MatchesPagerAdapter.TAB_PROPOSAL);
            }
        });

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
     * Scrolls to the given sub-tab index.
     * Called by navigation arguments and by the scrollToTabEvent observer.
     */
    public void scrollToSubTab(int index) {
        if (subTabPager != null) {
            subTabPager.setCurrentItem(index, true);
        }
    }
}
