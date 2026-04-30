package com.walkmate.ui.badge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.data.datasource.remote.api.SessionManager;

public class BadgeFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView txtEmpty;

    // Filter tab views
    private TextView tabAll;
    private TextView tabEarned;
    private TextView tabLocked;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private BadgeViewModel viewModel;
    private BadgeAdapter   adapter;
    private String         currentFilter = "all";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_badge, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupRecyclerView();
        setupViewModel();
        setupFilterTabs();

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        recyclerView  = null;
        progressBar   = null;
        txtEmpty      = null;
        tabAll        = null;
        tabEarned     = null;
        tabLocked     = null;
        adapter       = null;
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        progressBar  = root.findViewById(R.id.progressBadge);
        recyclerView = root.findViewById(R.id.rvBadges);
        txtEmpty     = root.findViewById(R.id.txtBadgeEmpty);
        tabAll       = root.findViewById(R.id.tabAll);
        tabEarned    = root.findViewById(R.id.tabEarned);
        tabLocked    = root.findViewById(R.id.tabLocked);

        root.findViewById(R.id.btnSubPageBack).setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigateUp());
        ((TextView) root.findViewById(R.id.txtSubPageTitle)).setText("My Badges");
    }

    private void setupRecyclerView() {
        adapter = new BadgeAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        SessionManager sessionManager = app.getSessionManager();
        String userId = sessionManager.getUserId();
        BadgeViewModelFactory factory = new BadgeViewModelFactory(
                app.getGamificationRepository(), userId);
        viewModel = new ViewModelProvider(this, factory).get(BadgeViewModel.class);
    }

    private void setupFilterTabs() {
        tabAll.setOnClickListener(v    -> applyFilter("all"));
        tabEarned.setOnClickListener(v -> applyFilter("earned"));
        tabLocked.setOnClickListener(v -> applyFilter("locked"));
        updateTabStyles();
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        updateTabStyles();
        BadgeUiState current = viewModel.getUiState().getValue();
        if (current != null && !current.isLoading()) {
            adapter.submitState(current, currentFilter);
            boolean empty = recyclerView.getAdapter().getItemCount() == 0;
            showEmptyIfNeeded(empty, current);
        }
    }

    private void updateTabStyles() {
        styleTab(tabAll,    "all".equals(currentFilter));
        styleTab(tabEarned, "earned".equals(currentFilter));
        styleTab(tabLocked, "locked".equals(currentFilter));
    }

    private void styleTab(TextView tab, boolean active) {
        if (tab == null) return;
        tab.setTextColor(active
                ? ContextCompat.getColor(requireContext(), R.color.orange_primary)
                : ContextCompat.getColor(requireContext(), R.color.text_muted));
        tab.setTypeface(null, active
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(BadgeUiState state) {
        if (state.isLoading()) {
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            txtEmpty.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.GONE);

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }

        adapter.submitState(state, currentFilter);
        boolean empty = recyclerView.getAdapter().getItemCount() == 0;
        showEmptyIfNeeded(empty, state);
    }

    private void showEmptyIfNeeded(boolean empty, BadgeUiState state) {
        if (empty) {
            recyclerView.setVisibility(View.GONE);
            txtEmpty.setVisibility(View.VISIBLE);
            if ("earned".equals(currentFilter)) {
                txtEmpty.setText("No badges earned yet.\nComplete walk sessions to start collecting.");
            } else if ("locked".equals(currentFilter)) {
                txtEmpty.setText("All badges unlocked — amazing work!");
            } else {
                txtEmpty.setText("No badges available.");
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            txtEmpty.setVisibility(View.GONE);
        }
    }
}
