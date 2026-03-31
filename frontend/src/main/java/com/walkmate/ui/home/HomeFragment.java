package com.walkmate.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.WalkMateStatColumn;
import com.walkmate.core.util.GlideHelper;
import com.walkmate.ui.home.quickinvite.QuickInviteAdapter;

/**
 * Thin view for the Home Dashboard tab.
 *
 * Responsibilities:
 *   1. Inflate fragment_home.xml.
 *   2. Acquire the host Activity as an {@link OnHomeActionListener} in onAttach().
 *   3. Wire click listeners — navigation delegates to the listener; all other
 *      actions delegate to the ViewModel.
 *   4. Observe LiveData<HomeDashboardUiState> and call renderState().
 *   5. renderState() is the single place that writes to Views.
 *
 * Zero business logic lives here; no direct access to repositories or databases.
 */
public class HomeFragment extends Fragment {

    public static final String TAG = "home";

    // ── Navigation contract ───────────────────────────────────────────────────

    /**
     * Implemented by the host Activity. Keeps the Fragment decoupled from
     * concrete Activity types — the Fragment never casts getActivity() directly.
     *
     * Why an interface instead of direct Activity casting?
     *   - Testability: the Fragment can be tested in isolation with a mock listener.
     *   - Safety: a missing implementation throws a clear IllegalStateException at
     *     attach-time rather than a ClassCastException deep inside a click handler.
     *   - Decoupling: the Fragment expresses intent ("I want to show Explore") without
     *     knowing how the host achieves it (tab switch, back-stack push, etc.).
     */
    public interface OnHomeActionListener {
        /** Called when the user taps "Find a WalkMate Now". */
        void switchToExplore();
    }

    private OnHomeActionListener listener;

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView txtGreeting;
    private TextView txtLocation;
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

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private HomeViewModel viewModel;
    private QuickInviteAdapter quickInviteAdapter;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof OnHomeActionListener)) {
            throw new IllegalStateException(
                    context.getClass().getSimpleName()
                            + " must implement HomeFragment.OnHomeActionListener");
        }
        listener = (OnHomeActionListener) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Null the reference to prevent leaking the Activity after the Fragment
        // is detached from it (e.g., during a configuration change or back-stack pop).
        listener = null;
    }

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
        setupClickListeners();

        viewModel.loadDashboard();
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private void bindViews(View root) {
        txtGreeting           = root.findViewById(R.id.txtGreeting);
        txtLocation           = root.findViewById(R.id.txtLocation);
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
    }

    private void setupRecyclerView() {
        quickInviteAdapter = new QuickInviteAdapter();
        rvQuickInvite.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvQuickInvite.setAdapter(quickInviteAdapter);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        HomeViewModelFactory factory = new HomeViewModelFactory(
                app.getWalkSessionRepository(),
                app.getUserRepository());
        viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
    }

    private void setupClickListeners() {
        // Navigation action: delegated to the host Activity via the listener contract.
        // The Fragment expresses intent; the Activity decides how to fulfil it.
        btnFindWalkMate.setOnClickListener(v -> {
            if (listener != null) listener.switchToExplore();
        });
    }

    // ── State rendering ───────────────────────────────────────────────────────

    /**
     * Single source of truth for all View mutations.
     * Called every time the LiveData emits a new HomeDashboardUiState.
     */
    private void renderState(HomeDashboardUiState state) {
        if (state.isLoading()) {
            return;
        }

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
