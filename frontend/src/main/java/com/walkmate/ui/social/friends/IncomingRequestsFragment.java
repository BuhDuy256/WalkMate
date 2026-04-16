package com.walkmate.ui.social.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;

/**
 * Incoming Requests tab — shows pending friend requests sent to the current user.
 * ViewModel is scoped to the parent FriendsFragment.
 */
public class IncomingRequestsFragment extends Fragment {

    private RecyclerView         recyclerView;
    private TextView             txtEmpty;
    private FriendRequestsAdapter adapter;
    private FriendsViewModel     viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_incoming_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewIncoming);
        txtEmpty     = view.findViewById(R.id.txtEmptyIncoming);

        // showActions = true → Accept + Decline buttons visible
        adapter = new FriendRequestsAdapter(true);
        adapter.setActionListener(new FriendRequestsAdapter.ActionListener() {
            @Override public void onAccept(String requestId) {
                viewModel.acceptRequest(requestId);
            }
            @Override public void onDecline(String requestId) {
                viewModel.declineRequest(requestId);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Shared ViewModel scoped to parent FriendsFragment.
        viewModel = new ViewModelProvider(requireParentFragment()).get(FriendsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state.isLoading() || state.getError() != null) return;

            adapter.submitList(state.getIncomingRequests());

            boolean empty = state.getIncomingRequests().isEmpty();
            txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        recyclerView = null;
        txtEmpty     = null;
        adapter      = null;
    }
}
