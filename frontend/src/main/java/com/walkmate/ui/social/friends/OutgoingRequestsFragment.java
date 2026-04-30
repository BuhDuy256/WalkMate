package com.walkmate.ui.social.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.ui.profile.publicprofile.PublicProfileFragment;

/**
 * Sent Requests tab — shows friend requests sent by the current user that are still pending.
 * ViewModel is scoped to the parent FriendsFragment.
 */
public class OutgoingRequestsFragment extends Fragment {

    private RecyclerView          recyclerView;
    private View                  emptyState;
    private FriendRequestsAdapter adapter;
    private FriendsViewModel      viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_outgoing_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewOutgoing);
        emptyState   = view.findViewById(R.id.txtEmptyOutgoing);

        // showActions = false → shows Cancel button + "Pending · Sent X ago" subtitle
        adapter = new FriendRequestsAdapter(false);
        adapter.setActionListener(new FriendRequestsAdapter.ActionListener() {
            @Override public void onAccept(String requestId) {}
            @Override public void onDecline(String requestId) {}
            @Override public void onCancel(String requestId) {
                viewModel.cancelRequest(requestId);
            }
            @Override public void onViewProfile(String userId) {
                Bundle args = new Bundle();
                args.putString("userId", userId);
                args.putBoolean(PublicProfileFragment.ARG_ALLOW_FRIEND_REQUEST, true);
                NavHostFragment.findNavController(OutgoingRequestsFragment.this)
                        .navigate(R.id.action_friendsFragment_to_publicProfileFragment, args);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireParentFragment()).get(FriendsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state.isLoading() || state.getError() != null) return;

            adapter.submitList(state.getOutgoingRequests());

            boolean empty = state.getOutgoingRequests().isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        recyclerView = null;
        emptyState   = null;
        adapter      = null;
    }
}
