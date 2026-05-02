package com.walkmate.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.walksession.AbortReason;

/**
 * Report Incident screen — standalone full-page Fragment.
 *
 * Launched from the Session History card when:
 *   GlobalStatus == COMPLETED AND CurrentUser == COMPLETED AND Partner == NO_SHOW.
 *
 * Arguments: SESSION_ID, REPORTED_UID, SESSION_TERMINAL_AT_MS.
 * The reporting window is fixed at 72 h after the session ended.
 */
public class ReportIncidentFragment extends Fragment {

    public static final String TAG                      = "ReportIncidentFragment";
    public static final String ARG_SESSION_ID           = "SESSION_ID";
    public static final String ARG_REPORTED_UID         = "REPORTED_UID";
    /** Epoch-ms when the session reached a terminal state. 0 means "just happened". */
    public static final String ARG_SESSION_TERMINAL_AT_MS = "SESSION_TERMINAL_AT_MS";

    public static ReportIncidentFragment newInstance(String sessionId, String reportedUserId,
                                                      long terminalAtMs) {
        ReportIncidentFragment f = new ReportIncidentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,           sessionId);
        args.putString(ARG_REPORTED_UID,         reportedUserId);
        args.putLong(ARG_SESSION_TERMINAL_AT_MS, terminalAtMs);
        f.setArguments(args);
        return f;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar progressReport;
    private View        scrollViewReport;
    private RadioGroup  rgReason;
    private EditText    etEvidenceUrl;
    private Button      btnSubmitReport;
    private TextView    txtAlreadyReported;
    private View        btnBack;
    private TextView    txtWindowClosedBanner;

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

        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        progressReport      = view.findViewById(R.id.progressReport);
        scrollViewReport    = view.findViewById(R.id.scrollViewReport);
        rgReason            = view.findViewById(R.id.rgReportReason);
        etEvidenceUrl       = view.findViewById(R.id.etEvidenceUrl);
        btnSubmitReport     = view.findViewById(R.id.btnSubmitReport);
        txtAlreadyReported  = view.findViewById(R.id.txtAlreadyReported);
        btnBack             = view.findViewById(R.id.btnSubPageBack);
        ((TextView) view.findViewById(R.id.txtSubPageTitle)).setText("Report Incident");
        txtWindowClosedBanner = view.findViewById(R.id.txtWindowClosedBanner);

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        Bundle args           = getArguments();
        String sessionId      = args != null ? args.getString(ARG_SESSION_ID)             : null;
        String reportedUserId = args != null ? args.getString(ARG_REPORTED_UID)           : null;
        long terminalAtMs     = args != null ? args.getLong(ARG_SESSION_TERMINAL_AT_MS, 0L) : 0L;

        // ── Reporting window guard (72 h after session ended) ──────────────────
        if (terminalAtMs > 0) {
            long elapsed = System.currentTimeMillis() - terminalAtMs;
            if (elapsed > 72L * 60L * 60L * 1000L) {
                progressReport.setVisibility(View.GONE);
                scrollViewReport.setVisibility(View.VISIBLE);
                showWindowClosedBanner("The reporting window for this session has closed.");
                disableForm();
                return;
            }
        }

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new ReportIncidentViewModelFactory(app.getWalkSessionRepository()))
                .get(ReportIncidentViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            boolean formReady = state.kind == ReportIncidentUiState.Kind.IDLE
                    || state.kind == ReportIncidentUiState.Kind.ALREADY_REPORTED
                    || state.kind == ReportIncidentUiState.Kind.SUBMITTED
                    || state.kind == ReportIncidentUiState.Kind.ERROR;

            progressReport.setVisibility(formReady ? View.GONE : View.VISIBLE);
            scrollViewReport.setVisibility(formReady ? View.VISIBLE : View.GONE);

            if (!formReady) return;

            switch (state.kind) {
                case IDLE:
                    btnSubmitReport.setEnabled(true);
                    txtAlreadyReported.setVisibility(View.GONE);
                    break;
                case ALREADY_REPORTED:
                    rgReason.setEnabled(false);
                    for (int i = 0; i < rgReason.getChildCount(); i++) {
                        rgReason.getChildAt(i).setEnabled(false);
                    }
                    etEvidenceUrl.setEnabled(false);
                    btnSubmitReport.setEnabled(false);
                    btnSubmitReport.setBackground(
                            ContextCompat.getDrawable(requireContext(), R.drawable.bg_btn_submit_success));
                    btnSubmitReport.setText("Report Submitted");
                    txtAlreadyReported.setVisibility(View.VISIBLE);
                    break;
                case SUBMITTED:
                    btnSubmitReport.setEnabled(false);
                    btnSubmitReport.setBackground(
                            ContextCompat.getDrawable(requireContext(), R.drawable.bg_btn_submit_success));
                    btnSubmitReport.setText("Report Submitted");
                    btnSubmitReport.postDelayed(() -> {
                        if (isAdded()) {
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }, 900);
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

        if (sessionId != null) {
            viewModel.loadReportState(sessionId);
        }
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
        if (rgReason != null) {
            for (int i = 0; i < rgReason.getChildCount(); i++) {
                rgReason.getChildAt(i).setEnabled(false);
            }
        }
    }

    @Nullable
    private String selectedReason() {
        int selectedId = rgReason.getCheckedRadioButtonId();
        if (selectedId == View.NO_ID) return null;

        RadioButton rb = requireView().findViewById(selectedId);
        if (rb == null) return null;

        String label = rb.getText().toString();
        if (label.contains("Safety"))     return AbortReason.SAFETY_CONCERN.toApiValue();
        if (label.contains("Emergency"))  return AbortReason.EMERGENCY.toApiValue();
        if (label.contains("Misconduct")) return AbortReason.PARTNER_MISCONDUCT.toApiValue();
        return AbortReason.OTHER.toApiValue();
    }
}
