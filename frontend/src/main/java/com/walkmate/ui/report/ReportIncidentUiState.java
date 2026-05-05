package com.walkmate.ui.report;

/**
 * Immutable snapshot of the Report Incident screen state.
 *
 * {@code reportSnapshot} is non-null in the {@link Kind#ALREADY_REPORTED} state and
 * contains the reason and evidence URL from the previously submitted report.
 */
public class ReportIncidentUiState {

    public enum Kind { IDLE, LOADING, SUBMITTED, ALREADY_REPORTED, ERROR }

    public final Kind           kind;
    public final String         error;          // non-null when ERROR
    public final ReportSnapshot reportSnapshot; // non-null when ALREADY_REPORTED

    private ReportIncidentUiState(Kind kind, String error, ReportSnapshot reportSnapshot) {
        this.kind           = kind;
        this.error          = error;
        this.reportSnapshot = reportSnapshot;
    }

    public static ReportIncidentUiState idle()            { return new ReportIncidentUiState(Kind.IDLE,            null, null); }
    public static ReportIncidentUiState loading()         { return new ReportIncidentUiState(Kind.LOADING,         null, null); }
    public static ReportIncidentUiState submitted()       { return new ReportIncidentUiState(Kind.SUBMITTED,       null, null); }
    public static ReportIncidentUiState alreadyReported() { return new ReportIncidentUiState(Kind.ALREADY_REPORTED, null, null); }

    public static ReportIncidentUiState alreadyReported(ReportSnapshot snap) {
        return new ReportIncidentUiState(Kind.ALREADY_REPORTED, null, snap);
    }

    public static ReportIncidentUiState error(String msg) { return new ReportIncidentUiState(Kind.ERROR, msg, null); }

    // ── Snapshot type ─────────────────────────────────────────────────────────

    public static class ReportSnapshot {
        public final String reason;
        public final String evidenceUrl;

        public ReportSnapshot(String reason, String evidenceUrl) {
            this.reason      = reason;
            this.evidenceUrl = evidenceUrl;
        }
    }
}
