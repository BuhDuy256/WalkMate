package com.walkmate.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.LocationHelper;

import java.util.Calendar;
import java.util.List;

public class HomeFragment extends Fragment {

    public static final String TAG = "home";

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView     txtTimeGreeting;
    private TextView     txtGreeting;
    private TextView     txtLocation;
    private View         btnNotification;
    private View         viewNotificationBadge;
    private View         cardHero;
    private TextView     txtDistanceValue;
    private TextView     txtSessionsValue;
    private View         sectionRecentMates;
    private RecyclerView rvRecentMates;
    private TextView     btnSeeAllMates;
    private ProgressBar  loadingIndicator;
    private NestedScrollView contentContainer;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private HomeViewModel          viewModel;
    private RecentMatesHomeAdapter recentMatesAdapter;

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
        setupRecyclerView();
        setupViewModel();
        setupClickListeners(view);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel.getUiState().getValue() == null) {
            viewModel.loadDashboard();
        } else {
            viewModel.refreshNotificationBadge();
        }
        resolveLocationName();
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        txtTimeGreeting       = root.findViewById(R.id.txtTimeGreeting);
        txtGreeting           = root.findViewById(R.id.txtGreeting);
        txtLocation           = root.findViewById(R.id.txtLocation);
        btnNotification       = root.findViewById(R.id.btnNotification);
        viewNotificationBadge = root.findViewById(R.id.viewNotificationBadge);
        cardHero              = root.findViewById(R.id.cardHero);
        txtDistanceValue      = root.findViewById(R.id.txtDistanceValue);
        txtSessionsValue      = root.findViewById(R.id.txtSessionsValue);
        sectionRecentMates    = root.findViewById(R.id.sectionRecentMates);
        rvRecentMates         = root.findViewById(R.id.rvRecentMates);
        btnSeeAllMates        = root.findViewById(R.id.btnSeeAllMates);
        loadingIndicator      = root.findViewById(R.id.loadingIndicator);
        contentContainer      = root.findViewById(R.id.contentContainer);
    }

    private void setupRecyclerView() {
        recentMatesAdapter = new RecentMatesHomeAdapter();
        rvRecentMates.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvRecentMates.setAdapter(recentMatesAdapter);
        rvRecentMates.setNestedScrollingEnabled(false);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        HomeViewModelFactory factory = new HomeViewModelFactory(
                app.getWalkSessionRepository(),
                app.getUserRepository(),
                app.getUserProfileRepository(),
                app.getNotificationRepository(),
                app.getGamificationRepository());
        viewModel = new ViewModelProvider(requireActivity(), factory).get(HomeViewModel.class);
    }

    private void setupClickListeners(View root) {
        cardHero.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_explore));

        btnNotification.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_notifications));

        btnSeeAllMates.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_sessionHistoryFragment));
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

        // ── Greeting ──
        txtTimeGreeting.setText(getTimeGreeting());
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
        txtDistanceValue.setText(String.format("%.1f", state.getTotalDistanceKm()));
        txtSessionsValue.setText(String.valueOf(state.getCompletedSessions()));

        // ── Recent Mates ──
        List<HomeDashboardUiState.RecentMateSnapshot> mates = state.getRecentMates();
        if (mates != null && !mates.isEmpty()) {
            sectionRecentMates.setVisibility(View.VISIBLE);
            recentMatesAdapter.submitList(mates);
            triggerGeocoding(mates);
        } else {
            sectionRecentMates.setVisibility(View.GONE);
        }
    }

    private void triggerGeocoding(List<HomeDashboardUiState.RecentMateSnapshot> mates) {
        for (int i = 0; i < mates.size(); i++) {
            final int position = i;
            HomeDashboardUiState.RecentMateSnapshot mate = mates.get(i);
            if (mate.meetingLat == 0.0 && mate.meetingLng == 0.0) continue;
            LocationHelper.resolveLocationName(
                    requireContext().getApplicationContext(),
                    mate.meetingLat,
                    mate.meetingLng,
                    locationName -> recentMatesAdapter.updateLocationAtPosition(position, locationName));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getTimeGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning 👋";
        if (hour < 18) return "Good afternoon 👋";
        return "Good evening 👋";
    }
}
