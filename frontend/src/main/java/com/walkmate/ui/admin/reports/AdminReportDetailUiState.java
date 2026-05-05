package com.walkmate.ui.admin.reports;

import com.walkmate.domain.report.AdminReport;

public class AdminReportDetailUiState {

    private final boolean     isLoading;
    private final boolean     isProcessing;
    private final AdminReport report;
    private final String      error;
    private final boolean     isResolved;

    public AdminReportDetailUiState(boolean isLoading, boolean isProcessing,
                                    AdminReport report, String error, boolean isResolved) {
        this.isLoading    = isLoading;
        this.isProcessing = isProcessing;
        this.report       = report;
        this.error        = error;
        this.isResolved   = isResolved;
    }

    public static AdminReportDetailUiState loading() {
        return new AdminReportDetailUiState(true, false, null, null, false);
    }

    public static AdminReportDetailUiState loaded(AdminReport report) {
        return new AdminReportDetailUiState(false, false, report, null, false);
    }

    public static AdminReportDetailUiState processing(AdminReport report) {
        return new AdminReportDetailUiState(false, true, report, null, false);
    }

    public static AdminReportDetailUiState resolved(AdminReport report) {
        return new AdminReportDetailUiState(false, false, report, null, true);
    }

    public static AdminReportDetailUiState error(String message) {
        return new AdminReportDetailUiState(false, false, null, message, false);
    }

    public boolean isLoading()     { return isLoading; }
    public boolean isProcessing()  { return isProcessing; }
    public AdminReport getReport() { return report; }
    public String getError()       { return error; }
    public boolean isResolved()    { return isResolved; }
}
