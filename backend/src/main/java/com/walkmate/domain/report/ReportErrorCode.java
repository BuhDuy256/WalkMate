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
            "You cannot report yourself");

    private final String code;
    private final String message;

    ReportErrorCode(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    @Override public String getCode()    { return code; }
    @Override public String getMessage() { return message; }
}
