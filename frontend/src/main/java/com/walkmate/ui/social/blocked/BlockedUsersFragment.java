package com.walkmate.ui.social.blocked;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;

/**
 * Blocked Users settings screen.
 *
 * Displays the list of users the current user has blocked.
 * Each row has an "Unblock" button.
 *
 * Navigation entry point: ProfileFragment → "Blocked Users" menu row.
 */
public class BlockedUsersFragment extends Fragment {

    public static final String TAG = "BlockedUsersFragment";

    private ProgressBar           progressBar;
    private RecyclerView          recyclerView;
    private TextView              txtEmpty;
    private View                  btnBack;

    private BlockedUsersAdapter   adapter;
    private BlockedUsersViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_blocked_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressBar  = view.findViewById(R.id.progressBlockedUsers);
        recyclerView = view.findViewById(R.id.recyclerViewBlocked);
        txtEmpty     = view.findViewById(R.id.txtEmptyBlocked);
        btnBack      = view.findViewById(R.id.btnBackBlocked);

        // ── Adapter ───────────────────────────────────────────────────────────
        adapter = new BlockedUsersAdapter();
        adapter.setActionListener((userId, displayName) ->
                viewModel.unblock(userId, displayName));

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // ── ViewModel ─────────────────────────────────────────────────────────
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        BlockedUsersViewModelFactory factory =
                new BlockedUsersViewModelFactory(app.getSocialRepository());
        viewModel = new ViewModelProvider(this, factory).get(BlockedUsersViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        viewModel.getUnblockSuccessEvent().observe(getViewLifecycleOwner(), name -> {
            if (name == null) return;
            viewModel.consumeUnblockSuccessEvent();
            Toast.makeText(requireContext(), "User unblocked.", Toast.LENGTH_SHORT).show();
        });

        // ── Back ──────────────────────────────────────────────────────────────
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // ── Initial load ──────────────────────────────────────────────────────
        viewModel.loadBlocked();
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(BlockedUsersUiState state) {
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

        adapter.submitList(state.getBlockedUsers());

        boolean empty = state.getBlockedUsers().isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        progressBar  = null;
        recyclerView = null;
        txtEmpty     = null;
        btnBack      = null;
        adapter      = null;
    }
}
