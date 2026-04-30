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
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.notification.Notification;
import com.walkmate.domain.notification.NotificationRepository;
import com.walkmate.ui.matches.MatchesPagerAdapter;
import com.walkmate.ui.social.friends.FriendsPagerAdapter;

public class NotificationFragment extends Fragment {

    public static final String TAG = "notifications";

    private NotificationViewModel viewModel;
    private NotificationAdapter   adapter;

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView    txtEmpty;
    private TextView    txtError;
    private TextView    btnHeaderAction;

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

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        progressBar     = view.findViewById(R.id.progress_notifications);
        recyclerView    = view.findViewById(R.id.rv_notifications);
        txtEmpty        = view.findViewById(R.id.txt_notifications_empty);
        txtError        = view.findViewById(R.id.txt_notifications_error);
        btnHeaderAction = view.findViewById(R.id.btnHeaderAction);
        View btnBack    = view.findViewById(R.id.btnSubPageBack);

        ((TextView) view.findViewById(R.id.txtSubPageTitle)).setText(R.string.notification_screen_title);
        btnHeaderAction.setText(R.string.notification_mark_all_read);

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new NotificationAdapter();
        adapter.setOnReadListener(notification -> {
            if (!notification.isRead()) {
                viewModel.markRead(notification.getNotificationId());
            }
            navigateForNotification(notification);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        WalkMateApplication app =
                (WalkMateApplication) requireActivity().getApplicationContext();
        NotificationRepository repo = app.getNotificationRepository();

        viewModel = new ViewModelProvider(this, new NotificationViewModelFactory(repo))
                .get(NotificationViewModel.class);

        btnHeaderAction.setOnClickListener(v -> viewModel.markAllRead());

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

    // ── Notification tap navigation ───────────────────────────────────────────

    private void navigateForNotification(Notification notification) {
        NavOptions popSelf = new NavOptions.Builder()
                .setPopUpTo(R.id.notificationFragment, true)
                .build();

        Bundle args = new Bundle();

        switch (notification.getType()) {
            case PROPOSAL_RECEIVED:
            case INVITE_SENT:
            case PROPOSAL_ACCEPTED:
                args.putInt("scrollToTab", MatchesPagerAdapter.TAB_PROPOSAL);
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.matchesFragment, args, popSelf);
                break;

            case SESSION_CONFIRMED:
            case SESSION_ACTIVE:
                args.putInt("scrollToTab", MatchesPagerAdapter.TAB_SESSION);
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.matchesFragment, args, popSelf);
                break;

            case FRIEND_REQUEST_RECEIVED:
                args.putInt("scrollToTab", FriendsPagerAdapter.TAB_INCOMING);
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.friendsFragment, args, popSelf);
                break;

            case FRIEND_REQUEST_ACCEPTED:
                args.putInt("scrollToTab", FriendsPagerAdapter.TAB_FRIENDS);
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.friendsFragment, args, popSelf);
                break;

            case FRIEND_REQUEST_DECLINED:
                args.putInt("scrollToTab", FriendsPagerAdapter.TAB_OUTGOING);
                Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                        .navigate(R.id.friendsFragment, args, popSelf);
                break;

            default:
                break;
        }
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

                btnHeaderAction.setVisibility(state.unreadCount > 0 ? View.VISIBLE : View.GONE);

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
