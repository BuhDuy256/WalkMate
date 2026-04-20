package com.walkmate.core.util;

import com.walkmate.R;

/**
 * Maps backend UserErrorCode strings to a displayable string resource ID and
 * an ActionType that tells the UI how to present the error.
 *
 * Usage:
 *   UserErrorMessageMapper.ErrorResult result = UserErrorMessageMapper.map(errorCode);
 *   String msg = context.getString(result.messageResId);
 *   // then branch on result.actionType
 */
public class UserErrorMessageMapper {

    public enum ActionType {
        /** Show as a Toast or snackbar. */
        TOAST,
        /** Show inline under the relevant input field. */
        FIELD_ERROR,
        /** Clear session and redirect to AuthActivity. */
        FORCE_LOGOUT,
        /** No visible feedback needed (e.g. idempotent state already matches). */
        SILENT
    }

    public static class ErrorResult {
        public final int messageResId;
        public final ActionType actionType;

        public ErrorResult(int messageResId, ActionType actionType) {
            this.messageResId = messageResId;
            this.actionType = actionType;
        }
    }

    public static ErrorResult map(String errorCode) {
        if (errorCode == null) {
            return new ErrorResult(R.string.error_generic, ActionType.TOAST);
        }
        switch (errorCode) {
            // ── Forced logout ─────────────────────────────────────────────────
            case "USER_ACCOUNT_SUSPENDED":
                return new ErrorResult(R.string.error_user_account_suspended, ActionType.FORCE_LOGOUT);

            // ── Silent — idempotent state already matches ──────────────────────
            case "USER_ALREADY_PRIVATE":
                return new ErrorResult(R.string.error_user_already_private, ActionType.SILENT);
            case "USER_ALREADY_PUBLIC":
                return new ErrorResult(R.string.error_user_already_public, ActionType.SILENT);

            // ── Field-level errors (show inline) ──────────────────────────────
            case "USER_INVALID_CREDENTIALS":
                return new ErrorResult(R.string.error_user_invalid_credentials, ActionType.FIELD_ERROR);
            case "USER_EMAIL_ALREADY_EXISTS":
                return new ErrorResult(R.string.error_user_email_already_exists, ActionType.FIELD_ERROR);
            case "USER_INVALID_EMAIL_FORMAT":
                return new ErrorResult(R.string.error_user_invalid_email_format, ActionType.FIELD_ERROR);

            // ── Toast errors ──────────────────────────────────────────────────
            case "GOOGLE_LOGIN_FAILED":
                return new ErrorResult(R.string.error_google_login_failed, ActionType.TOAST);
            case "USER_NOT_FOUND":
                return new ErrorResult(R.string.error_user_not_found, ActionType.TOAST);
            case "INVALID_USER_DATA":
                return new ErrorResult(R.string.error_invalid_user_data, ActionType.TOAST);
            case "USER_PROVIDER_CONFLICT":
                return new ErrorResult(R.string.error_user_provider_conflict, ActionType.TOAST);

            default:
                return new ErrorResult(R.string.error_generic, ActionType.TOAST);
        }
    }
}
