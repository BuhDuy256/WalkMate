package com.walkmate.ui.gamification;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.ui.main.MainActivity;

import java.util.List;
import java.util.Locale;

/**
 * Post-Session Completed Screen.
 *
 * Full-page opaque screen shown after a walk finishes. Displays distance,
 * duration, and pace for the current user. Review is handled separately
 * from the Session History card — not here.
 *
 * Entry: TrackingScreenActivity observes WalkState.FINISHED → adds this
 *        Fragment over android.R.id.content.
 */
public class PostSessionSummaryFragment extends Fragment {

    public static final String TAG                       = "PostSessionSummary";
    public static final String ARG_SESSION_ID            = "SESSION_ID";
    public static final String ARG_PARTNER_NAME          = "PARTNER_NAME";
    public static final String ARG_PARTNER_ID            = "PARTNER_ID";
    public static final String ARG_LOCAL_DISTANCE_KM     = "LOCAL_DISTANCE_KM";
    public static final String ARG_LOCAL_ELAPSED_SECONDS = "LOCAL_ELAPSED_SECONDS";

    public static PostSessionSummaryFragment newInstance(String sessionId,
                                                          String partnerName) {
        PostSessionSummaryFragment f = new PostSessionSummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,   sessionId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        f.setArguments(args);
        return f;
    }

    public static PostSessionSummaryFragment newInstance(String sessionId,
                                                          String partnerName,
                                                          String partnerId,
                                                          double localDistanceKm,
                                                          long   localElapsedSeconds) {
        PostSessionSummaryFragment f = new PostSessionSummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,             sessionId);
        args.putString(ARG_PARTNER_NAME,           partnerName);
        args.putString(ARG_PARTNER_ID,             partnerId);
        args.putDouble(ARG_LOCAL_DISTANCE_KM,      localDistanceKm);
        args.putLong(ARG_LOCAL_ELAPSED_SECONDS,    localElapsedSeconds);
        f.setArguments(args);
        return f;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView          txtSummarySubtitle;
    private TextView          txtSummaryPartner;
    private TextView          txtSummaryDistance;
    private TextView          txtSummaryDuration;
    private TextView          txtSummaryPace;
    private TextView          txtSummaryBadges;
    private AvatarInitialView avatarSummaryPartner;
    private MaterialButton    btnDone;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private PostSessionSummaryViewModel viewModel;
    private String currentUserId;
    private String partnerName;

    // Local stats captured at session end — shown immediately before backend responds.
    private double localDistanceKm    = 0.0;
    private long   localElapsedSeconds = 0L;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_post_session_summary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtSummarySubtitle    = view.findViewById(R.id.txtSummarySubtitle);
        txtSummaryPartner     = view.findViewById(R.id.txtSummaryPartner);
        txtSummaryDistance    = view.findViewById(R.id.txtSummaryDistance);
        txtSummaryDuration    = view.findViewById(R.id.txtSummaryDuration);
        txtSummaryPace        = view.findViewById(R.id.txtSummaryPace);
        txtSummaryBadges      = view.findViewById(R.id.txtSummaryBadges);
        avatarSummaryPartner  = view.findViewById(R.id.avatarSummaryPartner);
        btnDone               = view.findViewById(R.id.btnDoneSummary);

        Bundle args         = getArguments();
        String sessionId    = args != null ? args.getString(ARG_SESSION_ID)            : null;
        partnerName         = args != null ? args.getString(ARG_PARTNER_NAME)           : null;
        String partnerId    = args != null ? args.getString(ARG_PARTNER_ID)             : null;
        localDistanceKm     = args != null ? args.getDouble(ARG_LOCAL_DISTANCE_KM, 0.0) : 0.0;
        localElapsedSeconds = args != null ? args.getLong(ARG_LOCAL_ELAPSED_SECONDS, 0L) : 0L;

        // Back press finishes the host Activity (returns to the caller, e.g. Matches).
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().finish();
                    }
                });

        // Populate partner info immediately (available from bundle args).
        if (partnerName != null) {
            txtSummaryPartner.setText(partnerName);
            txtSummarySubtitle.setText(
                    getString(R.string.post_session_subtitle_format, partnerName));
            avatarSummaryPartner.bind(partnerName, null);
        }

        // Partner name taps navigate to their public profile.
        final String resolvedPartnerId = partnerId;
        if (resolvedPartnerId != null) {
            txtSummaryPartner.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_NAVIGATE_USER_ID, resolvedPartnerId);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        currentUserId = app.getSessionManager().getUserId();
        viewModel = new ViewModelProvider(this,
                new PostSessionSummaryViewModelFactory(
                        app.getGamificationRepository(),
                        app.getWalkSessionRepository()))
                .get(PostSessionSummaryViewModel.class);

        viewModel.getSessionSummary().observe(getViewLifecycleOwner(), this::renderSummary);
        viewModel.getBadges().observe(getViewLifecycleOwner(), this::renderBadges);

        btnDone.setOnClickListener(v -> requireActivity().finish());

        // Show local values immediately — no network latency, no incomplete-sync zeros.
        renderLocalStats(localDistanceKm, localElapsedSeconds);

        if (sessionId != null) {
            viewModel.loadSummary(sessionId);
        }
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderSummary(SessionSummary summary) {
        if (summary == null) return;

        ParticipantSummary caller = summary.getCallerParticipant(currentUserId);
        if (caller == null) return;

        double backendDistanceKm  = caller.getDistanceKm();
        int    backendDurationMins = caller.getDurationMinutes();

        // Prefer backend values when non-zero; fall back to local to avoid blanking
        // out values that the backend hasn't synced yet (e.g. 0.00 km on short walks).
        double effectiveDistanceKm  = backendDistanceKm  > 0 ? backendDistanceKm
                                                              : localDistanceKm;
        int    effectiveDurationMins = backendDurationMins > 0 ? backendDurationMins
                                                               : (int)(localElapsedSeconds / 60);

        txtSummaryDistance.setText(
                String.format(Locale.getDefault(), "%.2f km", effectiveDistanceKm));
        txtSummaryDuration.setText(effectiveDurationMins + " min");
        txtSummaryPace.setText(formatPace(effectiveDistanceKm, effectiveDurationMins));

        // Re-bind partner avatar with the real photo URL from the API response.
        ParticipantSummary partner = summary.getPartnerParticipant(currentUserId);
        if (partner != null) {
            avatarSummaryPartner.bind(partner.getFullName(), partner.getAvatarUrl());
        }
    }

    private void renderLocalStats(double distanceKm, long elapsedSeconds) {
        txtSummaryDistance.setText(
                String.format(Locale.getDefault(), "%.2f km", distanceKm));
        int durationMins = (int)(elapsedSeconds / 60);
        txtSummaryDuration.setText(durationMins + " min");
        txtSummaryPace.setText(formatPace(distanceKm, durationMins));
    }

    private void renderBadges(List<com.walkmate.domain.gamification.UserBadge> badges) {
        if (badges == null || badges.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < badges.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(badges.get(i).getBadgeName());
        }
        txtSummaryBadges.setText(sb.toString());
        txtSummaryBadges.setVisibility(View.VISIBLE);
    }

    // ── Pace formatter ────────────────────────────────────────────────────────

    /**
     * Computes and formats pace as "X'YY\"" (min/km).
     * Returns placeholder when distance is too small to be meaningful.
     */
    private static String formatPace(double distanceKm, int durationMins) {
        if (distanceKm * 1000.0 < 50.0 || durationMins <= 0) {
            return "--'--\"";
        }
        double paceMinPerKm = durationMins / distanceKm;
        int min = (int) paceMinPerKm;
        int sec = (int) Math.round((paceMinPerKm - min) * 60.0);
        if (sec >= 60) { min++; sec = 0; }
        return String.format(Locale.getDefault(), "%d'%02d\"", min, sec);
    }
}
