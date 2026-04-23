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
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.ui.main.MainActivity;
import com.walkmate.ui.report.ReportIncidentFragment;
import com.walkmate.ui.review.SubmitReviewFragment;

import java.util.Locale;

/**
 * Post-Session Summary screen.
 *
 * Shows distance, duration, partner name, and newly earned badges.
 * Offers a "Leave a Review" button (always visible) and a
 * "Report Incident" button (only if the session was ABORTED).
 *
 * Entry point:
 *   TrackingScreenActivity observes WalkState.FINISHED → adds this Fragment
 *   over android.R.id.content, passing:
 *     - ARG_SESSION_ID   — String
 *     - ARG_PARTNER_NAME — String
 *     - ARG_IS_ABORTED   — boolean
 */
public class PostSessionSummaryFragment extends Fragment {

    public static final String TAG              = "PostSessionSummary";
    public static final String ARG_SESSION_ID   = "SESSION_ID";
    public static final String ARG_PARTNER_NAME = "PARTNER_NAME";
    public static final String ARG_PARTNER_ID   = "PARTNER_ID";
    public static final String ARG_IS_ABORTED   = "IS_ABORTED";

    public static PostSessionSummaryFragment newInstance(String sessionId,
                                                          String partnerName,
                                                          boolean isAborted) {
        PostSessionSummaryFragment f = new PostSessionSummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,   sessionId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        args.putBoolean(ARG_IS_ABORTED,  isAborted);
        f.setArguments(args);
        return f;
    }

    public static PostSessionSummaryFragment newInstance(String sessionId,
                                                          String partnerName,
                                                          String partnerId,
                                                          boolean isAborted) {
        PostSessionSummaryFragment f = new PostSessionSummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,   sessionId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        args.putString(ARG_PARTNER_ID,   partnerId);
        args.putBoolean(ARG_IS_ABORTED,  isAborted);
        f.setArguments(args);
        return f;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView txtSummaryPartner;
    private TextView txtSummaryDistance;
    private TextView txtSummaryDuration;
    private TextView txtSummaryBadges;
    private Button   btnLeaveReview;
    private Button   btnReportIncident;
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
        btnLeaveReview      = view.findViewById(R.id.btnLeaveReview);
        btnReportIncident   = view.findViewById(R.id.btnReportIncident);
        btnDone             = view.findViewById(R.id.btnDoneSummary);

        Bundle args = getArguments();
        String sessionId   = args != null ? args.getString(ARG_SESSION_ID)   : null;
        String partnerName = args != null ? args.getString(ARG_PARTNER_NAME)  : null;
        String partnerId   = args != null ? args.getString(ARG_PARTNER_ID)    : null;
        boolean isAborted  = args != null && args.getBoolean(ARG_IS_ABORTED, false);

        // Intercept back press — finish the (dead) TrackingScreenActivity entirely
        // rather than popping this Fragment and leaving the user on a finished walk screen.
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requireActivity().finish();
                    }
                });

        // Immediately show partner name from args — available before any API call.
        if (partnerName != null) txtSummaryPartner.setText("Walk with " + partnerName);

        // Tap on partner name → navigate to public profile (requires partnerId).
        final String resolvedPartnerId = partnerId;
        if (resolvedPartnerId != null) {
            txtSummaryPartner.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_NAVIGATE_USER_ID, resolvedPartnerId);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        // Incident Report button only visible for aborted sessions.
        btnReportIncident.setVisibility(isAborted ? View.VISIBLE : View.GONE);

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

        // Launch review screen.
        btnLeaveReview.setOnClickListener(v -> {
            if (sessionId == null) return;
            SubmitReviewFragment reviewFragment =
                    SubmitReviewFragment.newInstance(sessionId);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, reviewFragment, SubmitReviewFragment.TAG)
                    .addToBackStack(null)
                    .commit();
        });

        // Launch incident report screen.
        // Status is ABORTED (isAborted=true) or COMPLETED; terminalAtMs=0 means "just happened".
        final String reportStatus = isAborted ? "ABORTED" : "COMPLETED";
        btnReportIncident.setOnClickListener(v -> {
            if (sessionId == null) return;
            ReportIncidentFragment reportFragment =
                    ReportIncidentFragment.newInstance(sessionId, partnerId, reportStatus, 0L);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, reportFragment, ReportIncidentFragment.TAG)
                    .addToBackStack(null)
                    .commit();
        });

        // "Done" — finish the TrackingScreenActivity and return to Home.
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

        // "Leave a Review" is disabled if already reviewed.
        btnLeaveReview.setEnabled(!summary.isReviewed());
        if (summary.isReviewed()) {
            btnLeaveReview.setText("Already Reviewed");
        }
    }
}
