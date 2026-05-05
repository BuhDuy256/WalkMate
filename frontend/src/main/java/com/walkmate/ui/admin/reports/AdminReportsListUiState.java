package com.walkmate.ui.admin.reports;

import com.walkmate.domain.report.AdminReport;

import java.util.Collections;
import java.util.List;

public class AdminReportsListUiState {

    private final boolean          isLoading;
    private final List<AdminReport> reports;
    private final String           error;

    public AdminReportsListUiState(boolean isLoading, List<AdminReport> reports, String error) {
        this.isLoading = isLoading;
        this.reports   = reports != null ? reports : Collections.emptyList();
        this.error     = error;
    }

    public static AdminReportsListUiState loading() {
        return new AdminReportsListUiState(true, null, null);
    }

    public static AdminReportsListUiState success(List<AdminReport> reports) {
        return new AdminReportsListUiState(false, reports, null);
    }

    public static AdminReportsListUiState error(String message) {
        return new AdminReportsListUiState(false, null, message);
    }

    public boolean isLoading()          { return isLoading; }
    public List<AdminReport> getReports() { return reports; }
    public String getError()            { return error; }

    public int getTotalCount()    { return reports.size(); }

    public int getPendingCount() {
        int count = 0;
        for (AdminReport r : reports) if (r.getStatus() == AdminReport.Status.PENDING) count++;
        return count;
    }

    public int getApprovedCount() {
        int count = 0;
        for (AdminReport r : reports) if (r.getStatus() == AdminReport.Status.APPROVED) count++;
        return count;
    }

    public int getRejectedCount() {
        int count = 0;
        for (AdminReport r : reports) if (r.getStatus() == AdminReport.Status.REJECTED) count++;
        return count;
    }
}
