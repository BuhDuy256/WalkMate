package com.walkmate.ui.matches.proposal;

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
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.walkmate.R;
import com.walkmate.ui.matches.MatchesUiState;
import com.walkmate.ui.matches.MatchesViewModel;

public class ProposalFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView txtEmpty;
    private ProposalAdapter adapter;

    private MatchesViewModel matchesViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_proposal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Shared ViewModel scoped to Activity — same instance as MatchesFragment.
        matchesViewModel = new ViewModelProvider(requireActivity())
                .get(MatchesViewModel.class);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        txtEmpty     = view.findViewById(R.id.txtEmpty);

        adapter = new ProposalAdapter();
        adapter.setProposalActionListener(new ProposalAdapter.ProposalActionListener() {
            @Override public void onPass(String proposalId) {
                matchesViewModel.passProposal(proposalId);
            }
            @Override public void onAccept(String proposalId) {
                matchesViewModel.acceptProposal(proposalId);
            }
            @Override public void onCancel(String proposalId) {
                matchesViewModel.cancelProposal(proposalId);
            }
            @Override public void onProposalExpired() {
                matchesViewModel.loadAll();
            }
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> matchesViewModel.loadAll());

        matchesViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    private void renderState(MatchesUiState state) {
        swipeRefresh.setRefreshing(state.isLoading());

        adapter.setItems(state.getProposals());

        boolean empty = !state.isLoading() && state.getProposals().isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            matchesViewModel.consumeError();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        swipeRefresh = null;
        recyclerView = null;
        txtEmpty     = null;
        adapter      = null;
    }
}
