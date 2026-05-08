package com.walkmate.ui.walkpost;

import android.app.AlertDialog;
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
import com.walkmate.core.designsystem.view.VisibilitySelectorView;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.walkpost.PostVisibility;

public class WalkActivityFragment extends Fragment {

    public static final String TAG = "WalkActivityFragment";

    private WalkActivityViewModel viewModel;
    private WalkPostAdapter adapter;

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView txtEmpty;
    private TextView txtError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_walk_activity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        progressBar  = view.findViewById(R.id.progressWalkActivity);
        recyclerView = view.findViewById(R.id.rvWalkActivity);
        txtEmpty     = view.findViewById(R.id.txtWalkActivityEmpty);
        txtError     = view.findViewById(R.id.txtWalkActivityError);

        view.findViewById(R.id.btnSubPageBack)
                .setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        ((TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Walk Activity");

        adapter = new WalkPostAdapter();
        adapter.setOnChangeVisibilityListener(postId -> showChangeVisibilityDialog(postId));
        adapter.setOnDeletePostListener(postId -> showDeleteConfirmation(postId));

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new WalkActivityViewModelFactory(app.getWalkPostRepository()))
                .get(WalkActivityViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            switch (state.kind) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    txtEmpty.setVisibility(View.GONE);
                    txtError.setVisibility(View.GONE);
                    break;
                case READY:
                    progressBar.setVisibility(View.GONE);
                    txtError.setVisibility(View.GONE);
                    if (state.posts.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        txtEmpty.setVisibility(View.VISIBLE);
                    } else {
                        txtEmpty.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.submitList(state.posts);
                    }
                    break;
                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                    txtEmpty.setVisibility(View.GONE);
                    txtError.setVisibility(View.VISIBLE);
                    txtError.setText(state.error);
                    break;
            }
        });

        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.loadMyPosts();
    }

    private void showChangeVisibilityDialog(String postId) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_visibility, null);
        VisibilitySelectorView selector = dialogView.findViewById(R.id.visibilitySelectorDialog);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change Visibility")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    PostVisibility selected = selector.getSelectedVisibility();
                    viewModel.changeVisibility(postId, selected);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmation(String postId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post?")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deletePost(postId))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
