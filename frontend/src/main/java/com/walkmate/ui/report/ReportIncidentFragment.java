package com.walkmate.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.walksession.AbortReason;

/**
 * Report Incident screen.
 *
 * Shown from PostSessionSummaryFragment when the session was aborted.
 * The user selects a reason (mapped to {@link AbortReason} values) and
 * optionally provides an evidence URL.
 *
 * On successful submission, shows a Toast and pops back.
 */
public class ReportIncidentFragment extends Fragment {

    public static final String TAG                      = "ReportIncidentFragment";
    public static final String ARG_SESSION_ID           = "SESSION_ID";
    public static final String ARG_REPORTED_UID         = "REPORTED_UID";
    public static final String ARG_SESSION_STATUS       = "SESSION_STATUS";
    /** Epoch-ms when the session reached a terminal state. 0 means "just happened". */
    public static final String ARG_SESSION_TERMINAL_AT_MS = "SESSION_TERMINAL_AT_MS";

    /** Full constructor — passes all reporting-window context. */
    public static ReportIncidentFragment newInstance(String sessionId, String reportedUserId,
                                                      String sessionStatus, long terminalAtMs) {
        ReportIncidentFragment f = new ReportIncidentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,              sessionId);
        args.putString(ARG_REPORTED_UID,            reportedUserId);
        args.putString(ARG_SESSION_STATUS,          sessionStatus);
        args.putLong(ARG_SESSION_TERMINAL_AT_MS,    terminalAtMs);
        f.setArguments(args);
        return f;
    }

    /** Legacy 1-arg constructor — preserved for PostSessionSummaryFragment. */
    public static ReportIncidentFragment newInstance(String sessionId) {
        return newInstance(sessionId, null, null, 0L);
    }

    /** Legacy 2-arg constructor — preserved for PostSessionSummaryFragment. */
    public static ReportIncidentFragment newInstance(String sessionId, String reportedUserId) {
        return newInstance(sessionId, reportedUserId, null, 0L);
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private RadioGroup rgReason;
    private EditText   etEvidenceUrl;
    private Button     btnSubmitReport;
    private View       btnBack;
    private TextView   txtWindowClosedBanner;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private ReportIncidentViewModel viewModel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_incident, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rgReason            = view.findViewById(R.id.rgReportReason);
        etEvidenceUrl       = view.findViewById(R.id.etEvidenceUrl);
        btnSubmitReport     = view.findViewById(R.id.btnSubmitReport);
        btnBack             = view.findViewById(R.id.btnBackReport);
        txtWindowClosedBanner = view.findViewById(R.id.txtWindowClosedBanner);

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        Bundle args           = getArguments();
        String sessionId      = args != null ? args.getString(ARG_SESSION_ID)             : null;
        String reportedUserId = args != null ? args.getString(ARG_REPORTED_UID)           : null;
        String sessionStatus  = args != null ? args.getString(ARG_SESSION_STATUS)         : null;
        long terminalAtMs     = args != null ? args.getLong(ARG_SESSION_TERMINAL_AT_MS, 0L) : 0L;

        // ── Reporting window guard (UC-32) ─────────────────────────────────────
        // terminalAtMs == 0 → session just ended (PostSessionSummary path) → always open.
        boolean windowExpired = false;
        if (terminalAtMs > 0) {
            long nowMs = System.currentTimeMillis();
            long elapsed = nowMs - terminalAtMs;
            if ("COMPLETED".equals(sessionStatus)) {
                windowExpired = elapsed > 72L * 60L * 60L * 1000L;
            } else if ("ABORTED".equals(sessionStatus) || "NO_SHOW".equals(sessionStatus)) {
                windowExpired = elapsed > 24L * 60L * 60L * 1000L;
            }
            // ACTIVE sessions: no gate. CANCELLED: should not reach here.
        }
        if (windowExpired) {
            showWindowClosedBanner("The reporting window for this session has closed.");
            disableForm();
            return;
        }

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new ReportIncidentViewModelFactory(app.getWalkSessionRepository()))
                .get(ReportIncidentViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            switch (state.kind) {
                case IDLE:
                    btnSubmitReport.setEnabled(true);
                    break;
                case LOADING:
                    btnSubmitReport.setEnabled(false);
                    break;
                case SUBMITTED:
                    Toast.makeText(requireContext(),
                            "Report submitted. Thank you.", Toast.LENGTH_LONG).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    break;
                case ERROR:
                    btnSubmitReport.setEnabled(true);
                    Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        btnSubmitReport.setOnClickListener(v -> {
            if (sessionId == null) return;
            String reason      = selectedReason();
            String evidenceUrl = etEvidenceUrl.getText().toString().trim();
            viewModel.submitReport(sessionId, reportedUserId, reason, evidenceUrl);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showWindowClosedBanner(String message) {
        if (txtWindowClosedBanner != null) {
            txtWindowClosedBanner.setText(message);
            txtWindowClosedBanner.setVisibility(View.VISIBLE);
        }
    }

    private void disableForm() {
        if (rgReason       != null) rgReason.setEnabled(false);
        if (etEvidenceUrl  != null) etEvidenceUrl.setEnabled(false);
        if (btnSubmitReport != null) btnSubmitReport.setEnabled(false);
        // Disable each RadioButton individually
        if (rgReason != null) {
            for (int i = 0; i < rgReason.getChildCount(); i++) {
                rgReason.getChildAt(i).setEnabled(false);
            }
        }
    }

    /**
     * Maps the selected RadioButton to an {@link AbortReason} API value.
     * Returns null if no selection has been made (ViewModel will surface an error).
     */
    @Nullable
    private String selectedReason() {
        int selectedId = rgReason.getCheckedRadioButtonId();
        if (selectedId == View.NO_ID) return null;

        RadioButton rb = requireView().findViewById(selectedId);
        if (rb == null) return null;

        // Map label text to AbortReason enum values used by the backend.
        String label = rb.getText().toString();
        if (label.contains("Safety")) return AbortReason.SAFETY_CONCERN.toApiValue();
        if (label.contains("Emergency")) return AbortReason.EMERGENCY.toApiValue();
        if (label.contains("Misconduct")) return AbortReason.PARTNER_MISCONDUCT.toApiValue();
        return AbortReason.OTHER.toApiValue();
    }
}
