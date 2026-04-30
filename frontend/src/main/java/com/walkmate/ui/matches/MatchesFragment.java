package com.walkmate.ui.matches;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.walkmate.R;
import com.walkmate.core.util.WindowInsetUtils;

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

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.matchesHeader));

        subTabLayout = view.findViewById(R.id.subTabLayout);
        subTabPager  = view.findViewById(R.id.subTabPager);

        view.findViewById(R.id.btnMatchesBack).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        view.findViewById(R.id.btnNewIntent).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_matches_to_explore));

        pagerAdapter = new MatchesPagerAdapter(getChildFragmentManager(), getLifecycle());
        subTabPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(subTabLayout, subTabPager, (tab, position) -> {
            View customView = LayoutInflater.from(requireContext()).inflate(R.layout.item_match_tab, null);
            android.widget.TextView txtTitle = customView.findViewById(R.id.txtTabTitle);
            switch (position) {
                case MatchesPagerAdapter.TAB_FINDING:
                    txtTitle.setText(R.string.tab_finding);
                    break;
                case MatchesPagerAdapter.TAB_PROPOSAL:
                    txtTitle.setText(R.string.tab_proposal);
                    break;
                case MatchesPagerAdapter.TAB_SESSION:
                    txtTitle.setText(R.string.tab_session);
                    break;
            }
            tab.setCustomView(customView);
            updateTabState(tab, false);
        }).attach();

        subTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateTabState(tab, true);
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                updateTabState(tab, false);
            }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        if (subTabLayout.getTabCount() > 0) {
            updateTabState(subTabLayout.getTabAt(subTabLayout.getSelectedTabPosition()), true);
        }

        matchesViewModel = new ViewModelProvider(
                requireActivity(), new MatchesViewModelFactory(requireActivity().getApplication()))
                .get(MatchesViewModel.class);

        subTabPager.registerOnPageChangeCallback(pageChangeCallback);

        // Observe shared state to update tab badge counts
        matchesViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            updateTabBadge(MatchesPagerAdapter.TAB_FINDING,  safeSize(state.getActiveIntents()));
            updateTabBadge(MatchesPagerAdapter.TAB_PROPOSAL, safeSize(state.getProposals()));
            updateTabBadge(MatchesPagerAdapter.TAB_SESSION,  safeSize(state.getActiveSessions()));
        });

        // Handle navigation argument from ExploreFragment (match found)
        Bundle args = getArguments();
        if (args != null) {
            int scrollToTab = args.getInt("scrollToTab", 0);
            if (scrollToTab != 0) {
                subTabPager.post(() -> scrollToSubTab(scrollToTab));
            }
        }

        // Restore last-viewed sub-tab on return visits
        int lastTab = matchesViewModel.getLastViewedSubTab();
        if (lastTab != MatchesPagerAdapter.TAB_FINDING) {
            subTabPager.post(() -> scrollToSubTab(lastTab));
        }

        // Scroll to Session tab after accepting a proposal
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

    private void updateTabBadge(int position, int count) {
        if (subTabLayout == null) return;
        TabLayout.Tab tab = subTabLayout.getTabAt(position);
        if (tab == null || tab.getCustomView() == null) return;

        android.widget.TextView txtBadge = tab.getCustomView().findViewById(R.id.txtTabBadge);
        if (count > 0) {
            txtBadge.setText(String.valueOf(count));
            txtBadge.setVisibility(View.VISIBLE);
        } else {
            txtBadge.setVisibility(View.GONE);
        }
    }

    private void updateTabState(TabLayout.Tab tab, boolean isSelected) {
        if (tab == null || tab.getCustomView() == null) return;
        View view = tab.getCustomView();
        android.widget.TextView txtTitle = view.findViewById(R.id.txtTabTitle);
        android.widget.TextView txtBadge = view.findViewById(R.id.txtTabBadge);

        int activeColor = ContextCompat.getColor(requireContext(), R.color.orange_primary);
        int inactiveColor = android.graphics.Color.parseColor("#A8A29E");

        txtTitle.setTextColor(isSelected ? activeColor : inactiveColor);
        txtTitle.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(getResources().getDisplayMetrics().density * 9);
        bg.setColor(isSelected ? activeColor : android.graphics.Color.parseColor("#E7E5E4"));
        txtBadge.setBackground(bg);

        txtBadge.setTextColor(isSelected ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#78716C"));
    }

    private static int safeSize(java.util.List<?> list) {
        return list != null ? list.size() : 0;
    }
}
