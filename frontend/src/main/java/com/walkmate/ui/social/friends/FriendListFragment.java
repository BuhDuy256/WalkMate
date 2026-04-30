package com.walkmate.ui.social.friends;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;

/**
 * Friends tab — shows the accepted friends list.
 * ViewModel is scoped to the parent FriendsFragment.
 */
public class FriendListFragment extends Fragment {

    private RecyclerView recyclerView;
    private View         emptyState;
    private FriendsAdapter adapter;
    private FriendsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerViewFriends);
        emptyState   = view.findViewById(R.id.txtEmptyFriends);

        adapter = new FriendsAdapter();
        adapter.setActionListener(new FriendsAdapter.ActionListener() {
            @Override public void onInviteWalk(String userId) {
                viewModel.navigateToInviteWalk(userId);
            }
            @Override public void onViewProfile(String userId) {
                Bundle args = new Bundle();
                args.putString("userId", userId);
                args.putString(com.walkmate.ui.profile.publicprofile.PublicProfileFragment.ARG_VIEW_MODE, "FRIEND");
                NavHostFragment.findNavController(FriendListFragment.this)
                        .navigate(R.id.action_friendsFragment_to_publicProfileFragment, args);
            }
            @Override public void onRemoveFriend(String userId, String displayName) {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Remove Friend")
                        .setMessage("Remove " + displayName + " from your friends?")
                        .setPositiveButton("Remove", (d, w) -> viewModel.removeFriend(userId))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireParentFragment()).get(FriendsViewModel.class);
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state.isLoading()) return;

            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
                return;
            }

            adapter.submitList(state.getFriends());

            boolean empty = state.getFriends().isEmpty();
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
