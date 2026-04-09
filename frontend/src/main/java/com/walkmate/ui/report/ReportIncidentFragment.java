package com.walkmate.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

    public static final String TAG              = "ReportIncidentFragment";
    public static final String ARG_SESSION_ID   = "SESSION_ID";
    /**
     * The ID of the user being reported. In practice this is the partner.
     * PostSessionSummaryFragment should pass this; if absent the ViewModel
     * will report a validation error.
     */
    public static final String ARG_REPORTED_UID = "REPORTED_UID";

    public static ReportIncidentFragment newInstance(String sessionId) {
        ReportIncidentFragment f = new ReportIncidentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        f.setArguments(args);
        return f;
    }

    public static ReportIncidentFragment newInstance(String sessionId, String reportedUserId) {
        ReportIncidentFragment f = new ReportIncidentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,   sessionId);
        args.putString(ARG_REPORTED_UID, reportedUserId);
        f.setArguments(args);
        return f;
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    private RadioGroup rgReason;
    private EditText   etEvidenceUrl;
    private Button     btnSubmitReport;
    private View       btnBack;

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

        rgReason       = view.findViewById(R.id.rgReportReason);
        etEvidenceUrl  = view.findViewById(R.id.etEvidenceUrl);
        btnSubmitReport = view.findViewById(R.id.btnSubmitReport);
        btnBack        = view.findViewById(R.id.btnBackReport);

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        Bundle args          = getArguments();
        String sessionId     = args != null ? args.getString(ARG_SESSION_ID)   : null;
        String reportedUserId = args != null ? args.getString(ARG_REPORTED_UID) : null;

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
