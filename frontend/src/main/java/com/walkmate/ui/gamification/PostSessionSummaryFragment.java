package com.walkmate.ui.gamification;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.ui.main.MainActivity;

import java.util.Locale;

/**
 * Post-Session Summary screen.
 *
 * Shows distance, duration, partner name, and newly earned badges.
 * Review and Report actions are no longer available here — they are driven
 * by the resolved per-user statuses on the Session History card.
 *
 * Entry point:
 *   TrackingScreenActivity observes WalkState.FINISHED → adds this Fragment
 *   over android.R.id.content, passing:
 *     - ARG_SESSION_ID   — String
 *     - ARG_PARTNER_NAME — String
 *     - ARG_PARTNER_ID   — String (optional)
 */
public class PostSessionSummaryFragment extends Fragment {

    public static final String TAG              = "PostSessionSummary";
    public static final String ARG_SESSION_ID   = "SESSION_ID";
    public static final String ARG_PARTNER_NAME = "PARTNER_NAME";
    public static final String ARG_PARTNER_ID   = "PARTNER_ID";

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
                                                          String partnerId) {
        PostSessionSummaryFragment f = new PostSessionSummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,   sessionId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        args.putString(ARG_PARTNER_ID,   partnerId);
        f.setArguments(args);
        return f;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView txtSummaryPartner;
    private TextView txtSummaryDistance;
    private TextView txtSummaryDuration;
    private TextView txtSummaryBadges;
    private Button   btnDone;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private PostSessionSummaryViewModel viewModel;
    private String currentUserId;

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

        txtSummaryPartner   = view.findViewById(R.id.txtSummaryPartner);
        txtSummaryDistance  = view.findViewById(R.id.txtSummaryDistance);
        txtSummaryDuration  = view.findViewById(R.id.txtSummaryDuration);
        txtSummaryBadges    = view.findViewById(R.id.txtSummaryBadges);
        btnDone             = view.findViewById(R.id.btnDoneSummary);

        Bundle args      = getArguments();
        String sessionId   = args != null ? args.getString(ARG_SESSION_ID)   : null;
        String partnerName = args != null ? args.getString(ARG_PARTNER_NAME)  : null;
        String partnerId   = args != null ? args.getString(ARG_PARTNER_ID)    : null;

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().finish();
                    }
                });

        if (partnerName != null) txtSummaryPartner.setText("Walk with " + partnerName);

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
        viewModel.getBadges().observe(getViewLifecycleOwner(), badges -> {
            if (badges != null && !badges.isEmpty()) {
                StringBuilder sb = new StringBuilder("New badges: ");
                for (int i = 0; i < badges.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(badges.get(i).getBadgeName());
                }
                txtSummaryBadges.setText(sb.toString());
                txtSummaryBadges.setVisibility(View.VISIBLE);
            }
        });

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> requireActivity().finish());
        }

        if (sessionId != null) {
            viewModel.loadSummary(sessionId);
        }
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderSummary(SessionSummary summary) {
        if (summary == null) return;

        com.walkmate.domain.walksession.ParticipantSummary caller =
                summary.getCallerParticipant(currentUserId);
        if (caller != null) {
            txtSummaryDistance.setText(String.format(Locale.getDefault(),
                    "%.2f km", caller.getDistanceKm()));
            txtSummaryDuration.setText(caller.getDurationMinutes() + " min");
        }
    }
}
