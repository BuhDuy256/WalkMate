package com.walkmate.ui.admin.reports;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.util.WindowInsetUtils;
import com.walkmate.domain.report.AdminReport;

public class AdminReportDetailFragment extends Fragment {

    public static final String ARG_REPORT_ID = "REPORT_ID";

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar progressBar;
    private View        contentRoot;
    private View        btnSubPageBack;

    // Header
    private TextView txtSubPageTitle;
    private TextView btnHeaderAction;

    // Evidence card
    private TextView txtEvidenceReportId;
    private TextView txtReportedUser;
    private TextView txtReporter;
    private TextView txtReason;
    private TextView txtEvidenceLink;
    private TextView txtTrustImpact;

    // Resolution card — pending state
    private View     layoutResolutionPending;
    private EditText etResolutionNote;
    private Button   btnApprove;
    private Button   btnReject;

    // Resolution card — resolved state
    private View     layoutResolutionResolved;
    private TextView txtDecision;
    private TextView txtDecidedOn;
    private TextView txtResolutionNote;

    // Footer
    private TextView txtFiledDate;

    // ── MVVM ──────────────────────────────────────────────────────────────────

    private AdminReportDetailViewModel viewModel;
    private String reportId;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_report_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        WindowInsetUtils.applyStatusBarPadding(view.findViewById(R.id.subPageHeader));

        Bundle args = getArguments();
        reportId = args != null ? args.getString(ARG_REPORT_ID) : null;

        bindViews(view);
        setupViewModel();

        btnSubPageBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        if (reportId != null) viewModel.loadReport(reportId);
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        progressBar          = root.findViewById(R.id.progressAdminDetail);
        contentRoot          = root.findViewById(R.id.contentAdminDetail);
        btnSubPageBack       = root.findViewById(R.id.btnSubPageBack);

        txtSubPageTitle      = root.findViewById(R.id.txtSubPageTitle);
        btnHeaderAction      = root.findViewById(R.id.btnHeaderAction);

        txtEvidenceReportId  = root.findViewById(R.id.txtEvidenceReportId);
        txtReportedUser      = root.findViewById(R.id.txtEvidenceReportedUser);
        txtReporter          = root.findViewById(R.id.txtEvidenceReporter);
        txtReason            = root.findViewById(R.id.txtEvidenceReason);
        txtEvidenceLink      = root.findViewById(R.id.txtEvidenceLink);
        txtTrustImpact       = root.findViewById(R.id.txtEvidenceTrustImpact);

        layoutResolutionPending  = root.findViewById(R.id.layoutResolutionPending);
        etResolutionNote         = root.findViewById(R.id.etResolutionNote);
        btnApprove               = root.findViewById(R.id.btnApproveReport);
        btnReject                = root.findViewById(R.id.btnRejectReport);

        layoutResolutionResolved = root.findViewById(R.id.layoutResolutionResolved);
        txtDecision              = root.findViewById(R.id.txtResolutionDecision);
        txtDecidedOn             = root.findViewById(R.id.txtResolutionDecidedOn);
        txtResolutionNote        = root.findViewById(R.id.txtResolutionNote);

        txtFiledDate             = root.findViewById(R.id.txtFiledDate);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new AdminReportDetailViewModelFactory(app.getAdminReportRepository()))
                .get(AdminReportDetailViewModel.class);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderState(AdminReportDetailUiState state) {
        if (state.isLoading()) {
            progressBar.setVisibility(View.VISIBLE);
            contentRoot.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.GONE);

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            viewModel.consumeError();
            // Show content if we have a report, otherwise keep content hidden
            if (state.getReport() != null) contentRoot.setVisibility(View.VISIBLE);
            return;
        }

        if (state.isResolved() && state.getReport() != null) {
            contentRoot.setVisibility(View.VISIBLE);
            bindReport(state.getReport(), false);
            Toast.makeText(requireContext(), "Report resolved successfully", Toast.LENGTH_SHORT).show();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        AdminReport report = state.getReport();
        if (report == null) return;

        contentRoot.setVisibility(View.VISIBLE);
        bindReport(report, state.isProcessing());
    }

    private void bindReport(AdminReport report, boolean isProcessing) {
        // Header
        String shortId = report.getReportId().length() >= 8
                ? "#" + report.getReportId().substring(0, 8).toUpperCase()
                : report.getReportId();
        txtSubPageTitle.setText(shortId + " · Review");

        btnHeaderAction.setVisibility(View.VISIBLE);
        switch (report.getStatus()) {
            case PENDING:
                btnHeaderAction.setText("PENDING");
                btnHeaderAction.setBackgroundResource(R.drawable.bg_status_badge_pending);
                btnHeaderAction.setTextColor(requireContext().getColor(R.color.white));
                break;
            case APPROVED:
                btnHeaderAction.setText("APPROVED");
                btnHeaderAction.setBackgroundResource(R.drawable.bg_status_badge_approved);
                btnHeaderAction.setTextColor(requireContext().getColor(R.color.white));
                break;
            case REJECTED:
                btnHeaderAction.setText("REJECTED");
                btnHeaderAction.setBackgroundResource(R.drawable.bg_status_badge_rejected);
                btnHeaderAction.setTextColor(requireContext().getColor(R.color.white));
                break;
        }

        // Evidence card
        txtEvidenceReportId.setText(report.getReportId());
        txtReportedUser.setText(report.getReportedUserName());
        txtReporter.setText(report.getReporterName());
        txtReason.setText(reasonLabel(report.getReason()));

        if (report.getEvidenceUrl() != null && !report.getEvidenceUrl().isBlank()) {
            txtEvidenceLink.setText(report.getEvidenceUrl());
            txtEvidenceLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(report.getEvidenceUrl())));
                } catch (Exception ignored) {}
            });
        } else {
            txtEvidenceLink.setText("No evidence provided");
            txtEvidenceLink.setOnClickListener(null);
        }

        int delta = report.getAppliedTrustDelta();
        txtTrustImpact.setText(delta != 0 ? delta + " pts" : "No penalty");

        // Filed date footer
        txtFiledDate.setText("Filed " + formatDate(report.getCreatedAt()));

        // Resolution card
        if (report.getStatus() == AdminReport.Status.PENDING) {
            layoutResolutionPending.setVisibility(View.VISIBLE);
            layoutResolutionResolved.setVisibility(View.GONE);

            btnApprove.setEnabled(!isProcessing);
            btnReject.setEnabled(!isProcessing);

            btnApprove.setOnClickListener(v -> showConfirmDialog("APPROVED"));
            btnReject.setOnClickListener(v -> showConfirmDialog("REJECTED"));
        } else {
            layoutResolutionPending.setVisibility(View.GONE);
            layoutResolutionResolved.setVisibility(View.VISIBLE);

            String decision = report.getStatus() == AdminReport.Status.APPROVED
                    ? "Approved" : "Rejected";
            txtDecision.setText(decision);
            txtDecidedOn.setText(formatDate(report.getResolvedAt()));
            txtResolutionNote.setText(
                    report.getResolutionNote() != null ? report.getResolutionNote() : "—");
        }
    }

    private void showConfirmDialog(String resolution) {
        String title   = "APPROVED".equals(resolution) ? "Approve Report?" : "Reject Report?";
        String message = "APPROVED".equals(resolution)
                ? "Approve this report? The trust penalty will remain applied."
                : "Reject this report? The trust penalty will be reversed.";

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    String note = etResolutionNote != null
                            ? etResolutionNote.getText().toString().trim() : "";
                    viewModel.resolveReport(reportId, resolution, note);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String reasonLabel(AdminReport.Reason reason) {
        if (reason == null) return "Other";
        switch (reason) {
            case SAFETY_CONCERN: return "Safety Concern";
            case MISCONDUCT:     return "Misconduct";
            case EMERGENCY:      return "Emergency";
            default:             return "Other";
        }
    }

    private String formatDate(String iso) {
        if (iso == null || iso.length() < 10) return "—";
        return iso.substring(0, 10);
    }
}
