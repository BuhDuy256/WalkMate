package com.walkmate.ui.report;

/**
 * Immutable snapshot of the Report Incident screen state.
 */
public class ReportIncidentUiState {

    public enum Kind { IDLE, LOADING, SUBMITTED, ERROR }

    public final Kind   kind;
    public final String error; // non-null when ERROR

    private ReportIncidentUiState(Kind kind, String error) {
        this.kind  = kind;
        this.error = error;
    }

    public static ReportIncidentUiState idle()             { return new ReportIncidentUiState(Kind.IDLE,      null); }
    public static ReportIncidentUiState loading()           { return new ReportIncidentUiState(Kind.LOADING,   null); }
    public static ReportIncidentUiState submitted()         { return new ReportIncidentUiState(Kind.SUBMITTED, null); }
    public static ReportIncidentUiState error(String msg)   { return new ReportIncidentUiState(Kind.ERROR,     msg);  }
}
