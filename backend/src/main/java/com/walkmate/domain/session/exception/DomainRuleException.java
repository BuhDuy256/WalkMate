package com.walkmate.domain.session.exception;

import com.walkmate.infrastructure.exception.ErrorCode;

public class DomainRuleException extends RuntimeException {
    private final ErrorCode errorCode;

    public DomainRuleException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
