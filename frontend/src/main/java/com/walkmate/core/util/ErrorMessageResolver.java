package com.walkmate.core.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Central mapper from raw backend error codes to English user-facing messages.
 *
 * All ViewModels must call {@link #resolve(String)} before placing an error string
 * into any UiState field or LiveData event.  Fragments and Activities must never
 * call this directly — they only display what the ViewModel already resolved.
 *
 * Handles three special input shapes:
 *   - null                        → generic fallback
 *   - "VALIDATION_ERROR|<msg>"    → strips prefix, returns backend message as-is
 *   - IOException message text    → detected by substring, maps to NETWORK_ERROR message
 *   - "SESSION_TERMINAL|<detail>" → ignored detail, generic fallback
 *   - any recognised UPPER_SNAKE code → mapped message
 *   - anything else               → generic fallback
 */
public final class ErrorMessageResolver {

    public static final String MSG_NETWORK = "No connection. Please check your internet and try again.";
    public static final String MSG_UNKNOWN = "Something went wrong. Please try again.";

    private static final Map<String, String> CODE_MAP = new HashMap<>();

    static {
        // ── Intent / Walk scheduling ──────────────────────────────────────────
        CODE_MAP.put("OVERLAP_INTENT",
                "You already have a walk scheduled during this time. Please choose another time.");
        CODE_MAP.put("WALK_INTENT_OVERLAP",
                "You already have a walk scheduled during this time. Please choose another time.");
        CODE_MAP.put("INTENT_NOT_FOUND",
                "We couldn't find this walk. It may have been cancelled or expired.");
        CODE_MAP.put("INTENT_ALREADY_MATCHING",
                "This walk is already being matched and can't be edited right now.");
        CODE_MAP.put("INTENT_EXPIRED",
                "This walk has expired. Please create a new one.");
        CODE_MAP.put("INTENT_CANCEL_FAILED",
                "Could not cancel the walk. Please try again.");
        CODE_MAP.put("INTENT_FETCH_FAILED",
                "Could not load your walks. Pull down to retry.");
        CODE_MAP.put("INTENT_CREATE_FAILED",
                "Could not create your walk request. Please try again.");
        CODE_MAP.put("PROFILE_INCOMPLETE_FOR_MATCHING",
                "Please complete your profile before scheduling a walk.");

        // ── Match proposal ────────────────────────────────────────────────────
        CODE_MAP.put("MATCH_PROPOSAL_EXPIRED",
                "This match has expired. You can look for another walking partner.");
        CODE_MAP.put("MATCH_PROPOSAL_REJECTED",
                "This match was declined.");
        CODE_MAP.put("MATCH_NOT_FOUND",
                "No match found yet.");
        CODE_MAP.put("PROPOSALS_FETCH_FAILED",
                "Could not load your match offers. Pull down to retry.");
        CODE_MAP.put("PROPOSAL_ACCEPT_FAILED",
                "Could not accept this match. Please try again.");
        CODE_MAP.put("PROPOSAL_PASS_FAILED",
                "Could not pass on this match. Please try again.");
        CODE_MAP.put("PROPOSAL_CANCEL_FAILED",
                "Could not cancel the match. Please try again.");
        CODE_MAP.put("PROPOSAL_ALREADY_TERMINAL",
                "This match offer is no longer active.");
        CODE_MAP.put("PROPOSAL_NOT_FOUND",
                "Match offer not found.");
        CODE_MAP.put("PROPOSAL_NOT_PARTICIPANT",
                "You are not part of this match.");
        CODE_MAP.put("PROPOSAL_CONCURRENT_MODIFICATION",
                "A conflict occurred. Please refresh and try again.");
        CODE_MAP.put("PROPOSAL_INTENT_NO_LONGER_OPEN",
                "Could not confirm — one of the walks is no longer available.");

        // ── Walk session ──────────────────────────────────────────────────────
        CODE_MAP.put("SESSION_NOT_FOUND",
                "We couldn't find this walk session.");
        CODE_MAP.put("SESSION_ALREADY_COMPLETED",
                "This walk has already been completed.");
        CODE_MAP.put("SESSION_NOT_ACTIVE",
                "This walk hasn't started yet.");
        CODE_MAP.put("SESSION_ACTIVATE_FAILED",
                "Could not confirm your arrival. Please try again.");
        CODE_MAP.put("SESSION_ACTIVATION_WINDOW_CLOSED",
                "Check-in time has ended. Please wait for the walk status to update.");
        CODE_MAP.put("SESSION_CANCEL_FAILED",
                "Could not cancel the walk. Please try again.");
        CODE_MAP.put("SESSION_COMPLETE_FAILED",
                "Could not finish the walk. Please try again.");
        CODE_MAP.put("SESSION_REPORT_FAILED",
                "Could not submit your report. Please try again.");
        CODE_MAP.put("SESSIONS_FETCH_FAILED",
                "Could not load your sessions. Pull down to retry.");
        CODE_MAP.put("SESSION_HISTORY_FAILED",
                "Could not load your walk history.");
        CODE_MAP.put("SESSION_SUMMARY_FAILED",
                "Could not load the walk summary.");
        CODE_MAP.put("SESSION_ROUTE_FAILED",
                "Could not load the route data.");
        CODE_MAP.put("QR_TOKEN_FETCH_FAILED",
                "Could not generate the QR code. Please try again.");
        CODE_MAP.put("QR_VERIFY_FAILED",
                "QR verification failed. Please try again.");

        // ── User / Auth ───────────────────────────────────────────────────────
        CODE_MAP.put("USER_NOT_FOUND",
                "We couldn't find this user.");
        CODE_MAP.put("USER_INVALID_CREDENTIALS",
                "Incorrect email or password.");
        CODE_MAP.put("INVALID_CREDENTIALS",
                "Incorrect email or password.");
        CODE_MAP.put("INVALID_USER_DATA",
                "Invalid information provided.");
        CODE_MAP.put("USER_EMAIL_ALREADY_EXISTS",
                "This email is already registered.");
        CODE_MAP.put("USER_EMAIL_GOOGLE_ONLY",
                "This email is linked to a Google account. Please sign in with Google.");
        CODE_MAP.put("USER_INVALID_EMAIL_FORMAT",
                "Please enter a valid email address.");
        CODE_MAP.put("USER_ACCOUNT_SUSPENDED",
                "Your account has been suspended.");
        CODE_MAP.put("USER_OTP_EXPIRED",
                "This code has expired. Please request a new one.");
        CODE_MAP.put("USER_OTP_INVALID",
                "Incorrect code. Please try again.");
        CODE_MAP.put("USER_OTP_ALREADY_USED",
                "This code has already been used.");
        CODE_MAP.put("USER_OTP_ATTEMPTS_EXCEEDED",
                "Too many incorrect attempts. Please request a new code.");
        CODE_MAP.put("USER_PASSWORD_TOO_WEAK",
                "Password must be at least 8 characters with one uppercase letter and one number.");
        CODE_MAP.put("USER_RESET_TOKEN_INVALID",
                "This reset link is invalid or has expired. Please start over.");
        CODE_MAP.put("USER_PASSWORD_RESET_NOT_ALLOWED",
                "Password reset is not available for this account type.");
        CODE_MAP.put("USER_PROVIDER_CONFLICT",
                "This Google account is linked to a different WalkMate account.");
        CODE_MAP.put("GOOGLE_LOGIN_FAILED",
                "Google Sign-In failed. Please try again.");
        CODE_MAP.put("REGISTER_FAILED",
                "Registration failed. Please try again.");
        CODE_MAP.put("SECURITY_INFO_FAILED",
                "Could not load account security info.");
        CODE_MAP.put("SET_VISIBILITY_FAILED",
                "Could not update visibility settings. Please try again.");
        CODE_MAP.put("SET_PASSWORD_FAILED",
                "Could not update password. Please try again.");
        CODE_MAP.put("CONFIRM_RESET_FAILED",
                "Could not reset your password. Please try again.");
        CODE_MAP.put("REQUEST_RESET_FAILED",
                "Could not send the reset email. Please try again.");
        CODE_MAP.put("VERIFY_OTP_FAILED",
                "OTP verification failed. Please try again.");

        // ── Review ────────────────────────────────────────────────────────────
        CODE_MAP.put("REVIEW_ALREADY_EXISTS",
                "You have already reviewed this walk.");
        CODE_MAP.put("REVIEW_FETCH_FAILED",
                "Could not load reviews.");
        CODE_MAP.put("REVIEW_SUBMIT_FAILED",
                "Could not submit your review. Please try again.");

        // ── Profile / Social ──────────────────────────────────────────────────
        CODE_MAP.put("FRIENDS_FETCH_FAILED",
                "Could not load your friends list.");
        CODE_MAP.put("BLOCKED_USERS_FETCH_FAILED",
                "Could not load blocked users.");
        CODE_MAP.put("PROFILE_FETCH_FAILED",
                "Could not load this profile.");
        CODE_MAP.put("HOTSPOT_FETCH_FAILED",
                "Could not load nearby hotspots.");
        CODE_MAP.put("BADGE_FETCH_FAILED",
                "Could not load your badges.");

        // ── Admin ─────────────────────────────────────────────────────────────
        CODE_MAP.put("REPORTS_FETCH_FAILED",
                "Could not load reports.");
        CODE_MAP.put("REPORT_FETCH_FAILED",
                "Could not load this report.");
        CODE_MAP.put("REPORT_RESOLVE_FAILED",
                "Could not resolve the report. Please try again.");
    }

    private ErrorMessageResolver() {}

    /**
     * Translates a raw error code (or exception message) to a user-facing English string.
     *
     * @param errorCode The raw code from {@code Exception.getMessage()}, which originates
     *                  from {@code ApiError.getCode()} in the data layer.
     * @return          A non-null, user-facing English string safe to display directly.
     */
    public static String resolve(String errorCode) {
        if (errorCode == null) return MSG_UNKNOWN;

        // VALIDATION_ERROR|<backend field message> — strip prefix, return message as-is
        if (errorCode.startsWith("VALIDATION_ERROR|")) {
            return errorCode.substring("VALIDATION_ERROR|".length());
        }

        // SESSION_TERMINAL|<detail> — terminal state, ignore detail
        if (errorCode.startsWith("SESSION_TERMINAL|")) {
            return MSG_UNKNOWN;
        }

        // IOException / network failure patterns
        String lower = errorCode.toLowerCase();
        if (lower.contains("unable to resolve host")
                || lower.contains("failed to connect")
                || lower.contains("etimedout")
                || lower.contains("connection refused")
                || lower.contains("network is unreachable")
                || lower.contains("connect timed out")) {
            return MSG_NETWORK;
        }

        String msg = CODE_MAP.get(errorCode);
        return msg != null ? msg : MSG_UNKNOWN;
    }
}
