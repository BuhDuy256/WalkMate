package com.walkmate.ui.matches.proposal;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.walkmate.R;
import com.walkmate.ui.matches.MatchesUiState;
import com.walkmate.ui.matches.MatchesViewModel;

public class ProposalFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private View txtEmpty;
    private FrameLayout celebrationOverlay;
    private ProposalAdapter adapter;

    private MatchesViewModel matchesViewModel;
    private final Handler celebrationHandler = new Handler(Looper.getMainLooper());
    private Runnable celebrationHideRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_proposal, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        matchesViewModel = new ViewModelProvider(requireActivity())
                .get(MatchesViewModel.class);

        swipeRefresh       = view.findViewById(R.id.swipeRefresh);
        recyclerView       = view.findViewById(R.id.recyclerView);
        txtEmpty           = view.findViewById(R.id.txtEmpty);
        celebrationOverlay = view.findViewById(R.id.celebrationOverlay);

        adapter = new ProposalAdapter();
        adapter.setProposalActionListener(new ProposalAdapter.ProposalActionListener() {
            @Override public void onPass(String proposalId, boolean isPrivateInvite) {
                String message = isPrivateInvite
                        ? "Decline this private invite? This invite will be closed and you will not be added to the public wait list."
                        : "Pass on this match? Your intent will stay active and we'll keep looking for other partners.";
                new AlertDialog.Builder(requireContext())
                        .setMessage(message)
                        .setPositiveButton("Confirm", (d, w) ->
                                matchesViewModel.passProposal(proposalId, isPrivateInvite))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            @Override public void onAccept(String proposalId) {
                matchesViewModel.acceptProposal(proposalId);
            }
            @Override public void onCancel(String proposalId) {
                matchesViewModel.cancelProposal(proposalId);
            }
            @Override public void onProposalExpired() {
                matchesViewModel.loadProposals();
            }
            @Override public void onViewProfile(String userId) {
                Bundle args = new Bundle();
                args.putString("userId", userId);
                args.putBoolean(com.walkmate.ui.profile.publicprofile.PublicProfileFragment.ARG_ALLOW_FRIEND_REQUEST, false);
                NavHostFragment.findNavController(ProposalFragment.this)
                        .navigate(R.id.action_matches_to_publicProfileFragment, args);
            }
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> matchesViewModel.loadProposals());

        matchesViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        // GAP-19 — celebration animation on double-accept (Case B)
        matchesViewModel.getCelebrationEvent().observe(getViewLifecycleOwner(), fired -> {
            if (Boolean.TRUE.equals(fired)) {
                matchesViewModel.consumeCelebration();
                showCelebrationAnimation();
            }
        });

        matchesViewModel.loadProposals();
    }

    private void showCelebrationAnimation() {
        if (celebrationOverlay == null) return;
        if (celebrationHideRunnable != null) {
            celebrationHandler.removeCallbacks(celebrationHideRunnable);
        }
        celebrationOverlay.setAlpha(0f);
        celebrationOverlay.setScaleX(0.8f);
        celebrationOverlay.setScaleY(0.8f);
        celebrationOverlay.setVisibility(View.VISIBLE);

        AnimatorSet animIn = new AnimatorSet();
        animIn.playTogether(
                ObjectAnimator.ofFloat(celebrationOverlay, "alpha",  0f, 1f),
                ObjectAnimator.ofFloat(celebrationOverlay, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(celebrationOverlay, "scaleY", 0.8f, 1f));
        animIn.setDuration(300);
        animIn.start();

        celebrationHideRunnable = () -> {
            if (celebrationOverlay != null) {
                celebrationOverlay.animate().alpha(0f).setDuration(300)
                        .withEndAction(() -> {
                            if (celebrationOverlay != null)
                                celebrationOverlay.setVisibility(View.GONE);
                        }).start();
            }
        };
        celebrationHandler.postDelayed(celebrationHideRunnable, 1_500L);
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
        if (celebrationHideRunnable != null) {
            celebrationHandler.removeCallbacks(celebrationHideRunnable);
            celebrationHideRunnable = null;
        }
        recyclerView.setAdapter(null);
        swipeRefresh       = null;
        recyclerView       = null;
        txtEmpty           = null;
        celebrationOverlay = null;
        adapter            = null;
    }
}
