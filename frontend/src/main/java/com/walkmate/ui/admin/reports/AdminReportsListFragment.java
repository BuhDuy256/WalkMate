package com.walkmate.ui.admin.reports;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.report.AdminReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminReportsListFragment extends Fragment {

    public static final String TAG = "AdminReportsListFragment";

    // ── Views ─────────────────────────────────────────────────────────────────

    private ProgressBar progressBar;
    private View        contentRoot;
    private View        emptyState;
    private RecyclerView recyclerView;
    private EditText    etSearch;
    private View        btnSearchToggle;
    private TextView    txtStatTotal;
    private TextView    txtStatPending;
    private TextView    txtStatApproved;
    private TextView    txtStatRejected;
    private TextView    txtHeaderPendingBadge;

    private LinearLayout tabAll;
    private LinearLayout tabPending;
    private LinearLayout tabResolved;

    private View btnBack;

    // ── State ─────────────────────────────────────────────────────────────────

    private AdminReportAdapter adapter;
    private AdminReportsListViewModel viewModel;
    private String currentFilter = "ALL";  // ALL | PENDING | RESOLVED
    private String searchQuery   = "";

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_reports_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupRecyclerView();
        setupViewModel();
        setupClickListeners();

        viewModel.loadReports();

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        viewModel.getNavigateToDetailEvent().observe(getViewLifecycleOwner(), reportId -> {
            if (reportId == null) return;
            viewModel.consumeNavigateToDetail();
            Bundle args = new Bundle();
            args.putString("REPORT_ID", reportId);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_adminReportsList_to_adminReportDetail, args);
        });
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void bindViews(View root) {
        progressBar           = root.findViewById(R.id.progressAdminReports);
        contentRoot           = root.findViewById(R.id.contentAdminReports);
        emptyState            = root.findViewById(R.id.emptyStateAdminReports);
        recyclerView          = root.findViewById(R.id.recyclerAdminReports);
        etSearch              = root.findViewById(R.id.etAdminSearch);
        btnSearchToggle       = root.findViewById(R.id.btnAdminSearchToggle);
        txtStatTotal          = root.findViewById(R.id.txtStatTotal);
        txtStatPending        = root.findViewById(R.id.txtStatPending);
        txtStatApproved       = root.findViewById(R.id.txtStatApproved);
        txtStatRejected       = root.findViewById(R.id.txtStatRejected);
        txtHeaderPendingBadge = root.findViewById(R.id.txtHeaderPendingBadge);
        tabAll                = root.findViewById(R.id.tabAll);
        tabPending            = root.findViewById(R.id.tabPending);
        tabResolved           = root.findViewById(R.id.tabResolved);
        btnBack               = root.findViewById(R.id.btnAdminBack);
    }

    private void setupRecyclerView() {
        adapter = new AdminReportAdapter(reportId -> viewModel.onReportClicked(reportId));
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {
        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new AdminReportsListViewModelFactory(app.getAdminReportRepository()))
                .get(AdminReportsListViewModel.class);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(v -> {
                if (etSearch.getVisibility() == View.VISIBLE) {
                    etSearch.setVisibility(View.GONE);
                    etSearch.setText("");
                    searchQuery = "";
                    applyFilter();
                } else {
                    etSearch.setVisibility(View.VISIBLE);
                    etSearch.requestFocus();
                }
            });
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                    applyFilter();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (tabAll != null)     tabAll.setOnClickListener(v     -> setFilter("ALL"));
        if (tabPending != null) tabPending.setOnClickListener(v -> setFilter("PENDING"));
        if (tabResolved != null) tabResolved.setOnClickListener(v -> setFilter("RESOLVED"));
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderState(AdminReportsListUiState state) {
        if (state.isLoading()) {
            progressBar.setVisibility(View.VISIBLE);
            contentRoot.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.GONE);
        contentRoot.setVisibility(View.VISIBLE);

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Stats grid
        txtStatTotal.setText(String.valueOf(state.getTotalCount()));
        txtStatPending.setText(String.valueOf(state.getPendingCount()));
        txtStatApproved.setText(String.valueOf(state.getApprovedCount()));
        txtStatRejected.setText(String.valueOf(state.getRejectedCount()));

        if (txtHeaderPendingBadge != null) {
            int pending = state.getPendingCount();
            txtHeaderPendingBadge.setText(String.valueOf(pending));
            txtHeaderPendingBadge.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);
        }

        applyFilter();
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        applyFilter();
    }

    private void applyFilter() {
        AdminReportsListUiState state = viewModel.getUiState().getValue();
        if (state == null || state.isLoading()) return;

        List<AdminReport> all = state.getReports();
        List<AdminReport> filtered = new ArrayList<>();

        for (AdminReport r : all) {
            if (!matchesFilter(r)) continue;
            if (!searchQuery.isEmpty() && !matchesSearch(r)) continue;
            filtered.add(r);
        }

        adapter.setReports(filtered);

        if (emptyState != null) {
            emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean matchesFilter(AdminReport r) {
        switch (currentFilter) {
            case "PENDING":  return r.getStatus() == AdminReport.Status.PENDING;
            case "RESOLVED": return r.getStatus() == AdminReport.Status.APPROVED
                                 || r.getStatus() == AdminReport.Status.REJECTED;
            default:         return true;
        }
    }

    private boolean matchesSearch(AdminReport r) {
        return contains(r.getReportedUserName()) || contains(r.getReporterName())
                || contains(r.getReportId())
                || (r.getReason() != null && r.getReason().name().toLowerCase().contains(searchQuery));
    }

    private boolean contains(String field) {
        return field != null && field.toLowerCase(Locale.getDefault()).contains(searchQuery);
    }
}
