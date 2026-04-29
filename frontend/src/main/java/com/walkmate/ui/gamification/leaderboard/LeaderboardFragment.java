package com.walkmate.ui.gamification.leaderboard;

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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;

/**
 * Standalone Leaderboard screen (GAP-5).
 *
 * Entry points:
 *   - HomeFragment      → action_home_to_leaderboardFragment
 *   - ProfileFragment   → action_profile_to_leaderboardFragment
 */
public class LeaderboardFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────────────────

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView       recyclerView;
    private TextView           txtEmpty;
    private TextView           txtBanner;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private LeaderboardViewModel viewModel;
    private LeaderboardAdapter   adapter;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupRecyclerView();
        setupViewModel();
        setupSwipeRefresh();

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.loadLeaderboard();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        swipeRefresh  = null;
        recyclerView  = null;
        txtEmpty      = null;
        txtBanner     = null;
        adapter       = null;
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        swipeRefresh = root.findViewById(R.id.swipeRefresh);
        recyclerView = root.findViewById(R.id.recyclerView);
        txtEmpty     = root.findViewById(R.id.txtEmpty);
        txtBanner    = root.findViewById(R.id.txtBanner);
        root.findViewById(R.id.btnSubPageBack).setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigateUp());
        ((TextView) root.findViewById(R.id.txtSubPageTitle)).setText("Leaderboard");
    }

    private void setupRecyclerView() {
        adapter = new LeaderboardAdapter();
        adapter.setMyUserId(viewModel != null ? viewModel.getMyUserId() : null);
        adapter.setOnItemClickListener(userId -> {
            Bundle args = new Bundle();
            args.putString("userId", userId);
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_leaderboard_to_publicProfileFragment, args);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        LeaderboardViewModelFactory factory = new LeaderboardViewModelFactory(
                app.getGamificationRepository(),
                app.getSessionManager());
        viewModel = new ViewModelProvider(this, factory).get(LeaderboardViewModel.class);
        // Propagate myUserId to adapter now that VM is ready
        if (adapter != null) {
            adapter.setMyUserId(viewModel.getMyUserId());
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadLeaderboard());
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(LeaderboardUiState state) {
        swipeRefresh.setRefreshing(state.isLoading());

        // Stale-data banner
        if (state.getLastUpdatedLabel() != null) {
            txtBanner.setText(state.getLastUpdatedLabel());
            txtBanner.setVisibility(View.VISIBLE);
        } else {
            txtBanner.setVisibility(View.GONE);
        }

        // Network error toast (only when we have no cached data to show)
        if (state.getError() != null && state.getEntries() == null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
        }

        // List / empty state
        if (state.getEntries() != null) {
            adapter.setMyUserId(viewModel.getMyUserId());
            adapter.submitList(state.getEntries());
            boolean empty = state.getEntries().isEmpty();
            txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        } else if (!state.isLoading()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }
    }
}
