package com.walkmate.domain.intent;

import com.walkmate.infrastructure.exception.ErrorCode;

public class DomainException extends RuntimeException {
  private final ErrorCode errorCode;

  public DomainException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
