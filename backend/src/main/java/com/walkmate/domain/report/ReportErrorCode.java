package com.walkmate.domain.report;

import com.walkmate.domain.shared.exception.ErrorCode;

public enum ReportErrorCode implements ErrorCode {

    REPORT_SESSION_INVALID_STATUS("REPORT_SESSION_INVALID_STATUS",
            "This session cannot be reported in its current status"),
    REPORT_WINDOW_EXPIRED("REPORT_WINDOW_EXPIRED",
            "The reporting window for this session has expired"),
    REPORT_ALREADY_SUBMITTED("REPORT_ALREADY_SUBMITTED",
            "You have already submitted a report for this session"),
    REPORT_SELF_NOT_ALLOWED("REPORT_SELF_NOT_ALLOWED",
            "You cannot report yourself"),

    // ── New: reporter eligibility ──────────────────────────────────────────────
    REPORT_REPORTER_NO_SHOW("REPORT_REPORTER_NO_SHOW",
            "You cannot file a report when you did not attend the session"),

    // ── New: admin dispute resolution ─────────────────────────────────────────
    REPORT_NOT_FOUND("REPORT_NOT_FOUND",
            "Report not found"),
    REPORT_ALREADY_RESOLVED("REPORT_ALREADY_RESOLVED",
            "This report has already been resolved"),
    REPORT_INVALID_RESOLUTION("REPORT_INVALID_RESOLUTION",
            "Resolution must be APPROVED or REJECTED");

    private final String code;
    private final String message;

    ReportErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
