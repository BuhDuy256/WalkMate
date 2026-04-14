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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.WalkMateStatColumn;
import com.walkmate.core.util.GlideHelper;
import com.walkmate.core.util.LocationHelper;
import com.walkmate.ui.home.quickinvite.QuickInviteAdapter;

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
    private ProgressBar streakProgress;
    private TextView txtStreakDays;
    private TextView txtHeroSubtitle;
    private MaterialButton btnFindWalkMate;
    private MaterialCardView cardUpcomingSession;
    private Chip chipSessionStatus;
    private ImageView imgSessionAvatar;
    private TextView txtSessionPartner;
    private TextView txtSessionTime;
    private RecyclerView rvQuickInvite;
    private WalkMateStatColumn statDistance;
    private WalkMateStatColumn statSessions;
    private WalkMateStatColumn statStreak;
    private ProgressBar loadingIndicator;
    private NestedScrollView contentContainer;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private HomeViewModel viewModel;
    private QuickInviteAdapter quickInviteAdapter;

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
        viewModel.loadDashboard();
        resolveLocationName();
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        txtGreeting           = root.findViewById(R.id.txtGreeting);
        txtLocation           = root.findViewById(R.id.txtLocation);
        btnNotification       = root.findViewById(R.id.btnNotification);
        viewNotificationBadge = root.findViewById(R.id.viewNotificationBadge);
        streakProgress        = root.findViewById(R.id.streakProgress);
        txtStreakDays         = root.findViewById(R.id.txtStreakDays);
        txtHeroSubtitle       = root.findViewById(R.id.txtHeroSubtitle);
        btnFindWalkMate       = root.findViewById(R.id.btnFindWalkMate);
        cardUpcomingSession   = root.findViewById(R.id.cardUpcomingSession);
        chipSessionStatus     = root.findViewById(R.id.chipSessionStatus);
        imgSessionAvatar      = root.findViewById(R.id.imgSessionAvatar);
        txtSessionPartner     = root.findViewById(R.id.txtSessionPartner);
        txtSessionTime        = root.findViewById(R.id.txtSessionTime);
        rvQuickInvite         = root.findViewById(R.id.rvQuickInvite);
        statDistance          = root.findViewById(R.id.statDistance);
        statSessions          = root.findViewById(R.id.statSessions);
        statStreak            = root.findViewById(R.id.statStreak);
        loadingIndicator      = root.findViewById(R.id.loadingIndicator);
        contentContainer      = root.findViewById(R.id.contentContainer);
    }

    private void setupRecyclerView() {
        quickInviteAdapter = new QuickInviteAdapter();
        quickInviteAdapter.setOnUserClickListener(userId -> {
            Bundle args = new Bundle();
            args.putString("userId", userId);
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_home_to_publicProfileFragment, args);
        });
        rvQuickInvite.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvQuickInvite.setAdapter(quickInviteAdapter);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        HomeViewModelFactory factory = new HomeViewModelFactory(
                app.getWalkSessionRepository(),
                app.getUserRepository(),
                app.getUserProfileRepository(),
                app.getNotificationRepository(),
                app.getHotspotRepository(),
                app.getGamificationRepository(),
                app.getSocialRepository());
        // Scope to Activity so the VM survives tab switches — fixes reload-on-every-navigate.
        viewModel = new ViewModelProvider(requireActivity(), factory).get(HomeViewModel.class);
    }

    private void setupClickListeners(View root) {
        // Navigate to ExploreFragment via NavController — no Activity interface needed.
        btnFindWalkMate.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_explore));

        // Navigate to NotificationFragment when the bell icon is tapped.
        btnNotification.setOnClickListener(v ->
                Navigation.findNavController(root).navigate(R.id.action_home_to_notifications));
    }

    // ── Location resolution ───────────────────────────────────────────────────

    /**
     * Gets the last known device location and resolves it to a city name via
     * {@link LocationHelper}, then forwards the result to the ViewModel.
     * Silently skips if location permission has not been granted.
     */
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

    /**
     * Single source of truth for all View mutations.
     * Called every time the LiveData emits a new HomeDashboardUiState.
     */
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

        // ── Streak widget ──
        streakProgress.setMax(state.getStreakGoal());
        streakProgress.setProgress(state.getStreakDays());
        txtStreakDays.setText(getString(
                R.string.home_streak_days_format, state.getStreakDays(), state.getStreakGoal()));

        // ── Hero subtitle ──
        txtHeroSubtitle.setText(getString(
                R.string.home_hero_subtitle_format, state.getNearbyHotspotCount()));

        // ── Upcoming session card ──
        HomeDashboardUiState.UpcomingSessionSnapshot session = state.getUpcomingSession();
        if (session != null) {
            cardUpcomingSession.setVisibility(View.VISIBLE);
            chipSessionStatus.setText(session.statusLabel);
            txtSessionPartner.setText(session.partnerName);
            txtSessionTime.setText(session.timeAndPlace);

            GlideHelper.loadCircle(imgSessionAvatar, session.partnerAvatarUrl);
        } else {
            cardUpcomingSession.setVisibility(View.GONE);
        }

        // ── Quick invite list ──
        quickInviteAdapter.submitList(state.getQuickInviteList());

        // ── Quick stats ──
        statDistance.setValue(String.format("%.1f", state.getWeeklyDistanceKm()));
        statSessions.setValue(String.valueOf(state.getWeeklySessionCount()));
        statStreak.setValue(String.valueOf(state.getStreakDays()));
    }
}
