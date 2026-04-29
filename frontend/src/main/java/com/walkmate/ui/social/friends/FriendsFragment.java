package com.walkmate.ui.social.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;

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

        tabLayout = view.findViewById(R.id.tabLayoutFriends);
        viewPager = view.findViewById(R.id.viewPagerFriends);
        btnBack   = view.findViewById(R.id.btnSubPageBack);
        ((TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Friends");

        // ── ViewModel (scoped to this fragment) ───────────────────────────────
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        FriendsViewModelFactory factory = new FriendsViewModelFactory(app.getSocialRepository());
        viewModel = new ViewModelProvider(this, factory).get(FriendsViewModel.class);

        // ── ViewPager2 + TabLayout ────────────────────────────────────────────
        pagerAdapter = new FriendsPagerAdapter(getChildFragmentManager(), getLifecycle());
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case FriendsPagerAdapter.TAB_FRIENDS:
                    tab.setText("Friends");
                    break;
                case FriendsPagerAdapter.TAB_INCOMING:
                    tab.setText("Incoming");
                    break;
                case FriendsPagerAdapter.TAB_OUTGOING:
                    tab.setText("Sent");
                    break;
            }
        }).attach();

        // ── Incoming badge count on the Incoming tab ──────────────────────────
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (!state.isLoading() && state.getError() == null) {
                TabLayout.Tab incomingTab = tabLayout.getTabAt(FriendsPagerAdapter.TAB_INCOMING);
                if (incomingTab != null) {
                    int count = state.getIncomingBadgeCount();
                    if (count > 0) {
                        incomingTab.setText("Incoming (" + count + ")");
                    } else {
                        incomingTab.setText("Incoming");
                    }
                }
            }
        });

        // ── Invite Walk deep-link event ───────────────────────────────────────
        viewModel.getInviteWalkEvent().observe(getViewLifecycleOwner(), friendId -> {
            if (friendId == null) return;
            viewModel.consumeInviteWalkEvent();
            // Phase 5: deep-link to ExploreFragment with friendId pre-filled.
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
