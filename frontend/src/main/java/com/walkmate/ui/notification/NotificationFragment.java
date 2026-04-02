package com.walkmate.ui.notification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.notification.NotificationRepository;

/**
 * Notification Center screen.
 *
 * <p>Displays the user's notification feed in a RecyclerView and polls the backend
 * every 30 seconds while in the foreground (delegated to the ViewModel).</p>
 *
 * <p>Attach to the main navigation and launch it from the bell icon in the
 * Home toolbar. Badge count is driven by {@link NotificationUiState#unreadCount}.</p>
 */
public class NotificationFragment extends Fragment {

    public static final String TAG = "notifications";

    private NotificationViewModel viewModel;
    private NotificationAdapter   adapter;

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView    txtEmpty;
    private TextView    txtError;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressBar   = view.findViewById(R.id.progress_notifications);
        recyclerView  = view.findViewById(R.id.rv_notifications);
        txtEmpty      = view.findViewById(R.id.txt_notifications_empty);
        txtError      = view.findViewById(R.id.txt_notifications_error);
        View btnBack  = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });

        adapter = new NotificationAdapter();
        adapter.setOnReadListener(notification ->
                viewModel.markRead(notification.getNotificationId()));

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        WalkMateApplication app =
                (WalkMateApplication) requireActivity().getApplicationContext();
        NotificationRepository repo = app.getNotificationRepository();

        viewModel = new ViewModelProvider(this, new NotificationViewModelFactory(repo))
                .get(NotificationViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.startPolling();
    }

    @Override
    public void onPause() {
        super.onPause();
        viewModel.stopPolling();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderState(NotificationUiState state) {
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
                if (state.notifications.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    txtEmpty.setVisibility(View.VISIBLE);
                } else {
                    txtEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.submitList(state.notifications);
                }
                break;

            case ERROR:
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                txtEmpty.setVisibility(View.GONE);
                txtError.setVisibility(View.VISIBLE);
                txtError.setText(state.errorMessage);
                break;
        }
    }
}
