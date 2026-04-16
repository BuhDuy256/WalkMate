package com.walkmate.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.WalkMateStatColumn;
import com.walkmate.core.util.LocationHelper;

/**
 * Thin view for the Home Dashboard tab.
 *
 * Responsibilities:
 *   1. Inflate fragment_home.xml.
 *   2. Wire click listeners — navigation uses NavController; all other
 *      actions delegate to the ViewModel.
 *   3. Observe LiveData<HomeDashboardUiState> and call renderState().
 *   4. renderState() is the single place that writes to Views.
 *
 * Zero business logic lives here; no direct access to repositories or databases.
 */
public class HomeFragment extends Fragment {

    public static final String TAG = "home";

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView txtGreeting;
    private TextView txtLocation;
    private View btnNotification;
    private View viewNotificationBadge;
    private MaterialButton btnFindWalkMate;
    private WalkMateStatColumn statDistance;
    private WalkMateStatColumn statSessions;
    private ProgressBar loadingIndicator;
    private NestedScrollView contentContainer;
    private MaterialButton btnViewLeaderboard;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private HomeViewModel viewModel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupViewModel();
        setupClickListeners(view);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel.getUiState().getValue() == null) {
            // First entry — load everything.
            viewModel.loadDashboard();
        } else {
            // Returning from another screen (e.g. NotificationFragment).
            // Re-check unread count so the badge clears immediately after the user
            // reads all notifications without forcing a full dashboard reload.
            viewModel.refreshNotificationBadge();
        }
        resolveLocationName();
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        txtGreeting           = root.findViewById(R.id.txtGreeting);
        txtLocation           = root.findViewById(R.id.txtLocation);
        btnNotification       = root.findViewById(R.id.btnNotification);
        viewNotificationBadge = root.findViewById(R.id.viewNotificationBadge);
        btnFindWalkMate       = root.findViewById(R.id.btnFindWalkMate);
        statDistance          = root.findViewById(R.id.statDistance);
        statSessions          = root.findViewById(R.id.statSessions);
        loadingIndicator      = root.findViewById(R.id.loadingIndicator);
        contentContainer      = root.findViewById(R.id.contentContainer);
        btnViewLeaderboard    = root.findViewById(R.id.btnViewLeaderboard);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        HomeViewModelFactory factory = new HomeViewModelFactory(
                app.getWalkSessionRepository(),
                app.getUserRepository(),
                app.getUserProfileRepository(),
                app.getNotificationRepository(),
                app.getGamificationRepository());
        // Scope to Activity so the VM survives tab switches — fixes reload-on-every-navigate.
        viewModel = new ViewModelProvider(requireActivity(), factory).get(HomeViewModel.class);
    }

    private void setupClickListeners(View root) {
        btnFindWalkMate.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_explore));

        btnNotification.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_notifications));

        btnViewLeaderboard.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_leaderboardFragment));
    }

    // ── Location resolution ───────────────────────────────────────────────────

    @SuppressWarnings("MissingPermission")
    private void resolveLocationName() {
        FusedLocationProviderClient locationClient =
                LocationServices.getFusedLocationProviderClient(requireContext());
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LocationHelper.resolveCity(
                        requireContext().getApplicationContext(),
                        location,
                        cityName -> viewModel.onLocationResolved(cityName));
            }
        });
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(HomeDashboardUiState state) {
        loadingIndicator.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        contentContainer.setVisibility(state.isLoading() ? View.GONE : View.VISIBLE);
        if (state.isLoading()) return;

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
        }

        // ── Greeting / Location ──
        if (state.getGreetingName() != null) {
            txtGreeting.setText(getString(R.string.home_greeting_format, state.getGreetingName()));
        }
        if (state.getLocationName() != null) {
            txtLocation.setText(state.getLocationName());
        }

        // ── Notification badge ──
        viewNotificationBadge.setVisibility(
                state.hasUnreadNotification() ? View.VISIBLE : View.GONE);

        // ── Stats ──
        statDistance.setValue(String.format("%.1f", state.getTotalDistanceKm()));
        statSessions.setValue(String.valueOf(state.getCompletedSessions()));
    }
}
