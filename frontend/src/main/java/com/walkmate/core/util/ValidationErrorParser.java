package com.walkmate.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the comma-separated field-error string returned by the backend for
 * VALIDATION_ERROR responses into a per-field map that UI layers can display
 * inline next to the corresponding input field.
 *
 * <h3>Expected input format</h3>
 * The backend {@code error.message} for VALIDATION_ERROR is a comma-space
 * delimited list of {@code "fieldName: reason"} pairs, e.g.:
 * <pre>"email: must not be blank, password: size must be between 8 and 64"</pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In a ViewModel or Fragment, after receiving "VALIDATION_ERROR|<message>":
 * String rawMessage = error.getMessage().substring("VALIDATION_ERROR|".length());
 * Map<String, String> fieldErrors = ValidationErrorParser.parse(rawMessage);
 * String emailError = fieldErrors.get("email"); // "must not be blank"
 * }</pre>
 */
public final class ValidationErrorParser {

    /** Utility class — no instances. */
    private ValidationErrorParser() {}

    /**
     * Splits {@code message} on {@code ", "} (comma-space) and parses each
     * token as {@code "fieldName: reason"}, returning a map of field → reason.
     *
     * <p>Tokens that contain no colon are stored under their full trimmed text
     * as both key and value, so callers always get a non-null value.
     *
     * @param message The raw {@code error.message} string from the API envelope.
     *                May be {@code null} or empty — returns an empty map in that case.
     * @return A {@link LinkedHashMap} preserving insertion order of field errors.
     */
    public static Map<String, String> parse(String message) {
        Map<String, String> result = new LinkedHashMap<>();
        if (message == null || message.isEmpty()) return result;

        String[] parts = message.split(", ");
        for (String part : parts) {
            int colon = part.indexOf(':');
            if (colon > 0) {
                String field  = part.substring(0, colon).trim();
                String reason = part.substring(colon + 1).trim();
                result.put(field, reason);
            } else {
                String entry = part.trim();
                result.put(entry, entry);
            }
        }
        return result;
    }
}
