package com.walkmate.ui.social.friends;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.WindowInsetUtils;

/**
 * Multi-tab Friends screen.
 *
 * Container fragment that hosts three sub-tabs via ViewPager2:
 *   0 — Friends (FriendListFragment)
 *   1 — Incoming Requests (IncomingRequestsFragment)
 *   2 — Sent Requests (OutgoingRequestsFragment)
 *
 * FriendsViewModel is scoped to this fragment. Sub-fragments access it via
 * ViewModelProvider(requireParentFragment()).
 *
 * Navigation entry point: ProfileFragment → "Friends" menu row.
 */
public class FriendsFragment extends Fragment {

    public static final String TAG = "FriendsFragment";

    private TabLayout           tabLayout;
    private ViewPager2          viewPager;
    private FriendsPagerAdapter pagerAdapter;
    private View                btnBack;

    private FriendsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        tabLayout = view.findViewById(R.id.tabLayoutFriends);
        viewPager = view.findViewById(R.id.viewPagerFriends);
        btnBack   = view.findViewById(R.id.btnSubPageBack);

        ((android.widget.TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Friends");

        // ── ViewModel (scoped to this fragment) ───────────────────────────────
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        FriendsViewModelFactory factory = new FriendsViewModelFactory(app.getSocialRepository());
        viewModel = new ViewModelProvider(this, factory).get(FriendsViewModel.class);

        // ── ViewPager2 + TabLayout ────────────────────────────────────────────
        pagerAdapter = new FriendsPagerAdapter(getChildFragmentManager(), getLifecycle());
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            View customView = LayoutInflater.from(requireContext()).inflate(R.layout.item_match_tab, null);
            android.widget.TextView txtTitle = customView.findViewById(R.id.txtTabTitle);
            switch (position) {
                case FriendsPagerAdapter.TAB_FRIENDS:
                    txtTitle.setText("Friends");
                    break;
                case FriendsPagerAdapter.TAB_INCOMING:
                    txtTitle.setText("Incoming");
                    break;
                case FriendsPagerAdapter.TAB_OUTGOING:
                    txtTitle.setText("Sent");
                    break;
            }
            tab.setCustomView(customView);
            updateTabState(tab, false);
        }).attach();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
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

        if (tabLayout.getTabCount() > 0) {
            updateTabState(tabLayout.getTabAt(tabLayout.getSelectedTabPosition()), true);
        }

        // ── Badge counts on all three tabs ────────────────────────────────────
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (!state.isLoading() && state.getError() == null) {
                updateTabBadge(FriendsPagerAdapter.TAB_FRIENDS,  state.getFriendsCount());
                updateTabBadge(FriendsPagerAdapter.TAB_INCOMING, state.getIncomingBadgeCount());
                updateTabBadge(FriendsPagerAdapter.TAB_OUTGOING, state.getOutgoingCount());
            }
        });

        // ── Invite Walk deep-link event ───────────────────────────────────────
        viewModel.getInviteWalkEvent().observe(getViewLifecycleOwner(), friendId -> {
            if (friendId == null) return;
            viewModel.consumeInviteWalkEvent();
            Toast.makeText(requireContext(), "Invite Walk — coming soon!", Toast.LENGTH_SHORT).show();
        });

        // ── Back navigation ───────────────────────────────────────────────────
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // ── Initial load ──────────────────────────────────────────────────────
        viewModel.loadAll();

        // ── Deep-link tab scroll (e.g. from FCM or NotificationFragment) ──────
        Bundle args = getArguments();
        if (args != null) {
            int scrollToTab = args.getInt("scrollToTab", -1);
            if (scrollToTab >= 0 && scrollToTab < FriendsPagerAdapter.TAB_COUNT) {
                viewPager.setCurrentItem(scrollToTab, false);
            }
        }
    }

    private void updateTabBadge(int tabIndex, int count) {
        if (tabLayout == null) return;
        TabLayout.Tab tab = tabLayout.getTabAt(tabIndex);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewPager != null) viewPager.setAdapter(null);
        tabLayout    = null;
        viewPager    = null;
        pagerAdapter = null;
        btnBack      = null;
    }
}
