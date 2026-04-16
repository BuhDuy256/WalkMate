package com.walkmate.core.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the {@code error.message} string returned by HTTP 422 (Jakarta
 * {@code @Valid}) responses into a field-keyed error map.
 *
 * <h3>Backend contract</h3>
 * The backend serialises all field violations into a single comma-separated
 * string of {@code "field: reason"} pairs, e.g.:
 * <pre>
 *   "fullName: must not be blank, email: must be a valid email address"
 * </pre>
 * Splitting on {@code ", "} and then on {@code ": "} (limit 2) recovers each
 * field name and its human-readable reason.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * if ("VALIDATION_ERROR".equals(apiError.getCode())) {
 *     Map<String, String> fieldErrors =
 *             ValidationHelper.parse(apiError.getMessage());
 *     String nameError  = fieldErrors.get("fullName");  // "must not be blank"
 *     String emailError = fieldErrors.get("email");     // "must be a valid email address"
 * }
 * }</pre>
 */
public final class ValidationHelper {

    /** Utility class — no instances. */
    private ValidationHelper() {}

    /**
     * Parses a comma-separated {@code "field: reason"} string into a
     * {@link Map} of {@code fieldName → reason}.
     *
     * <p>Edge cases handled:
     * <ul>
     *   <li>Null or blank input → empty map.</li>
     *   <li>A token with no {@code ":"} separator → key is the whole token,
     *       value is an empty string.</li>
     *   <li>Leading/trailing whitespace on keys and values is trimmed.</li>
     *   <li>Insertion order is preserved via {@link LinkedHashMap}.</li>
     * </ul>
     *
     * @param errorMessage The raw {@code error.message} string from the API.
     * @return             An unmodifiable map of field names to error reasons.
     */
    public static Map<String, String> parse(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();

        // The backend joins violations with ", " — split on that delimiter.
        String[] tokens = errorMessage.split(",\\s*");

        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;

            // Split "fieldName: reason text that may contain colons" into at most 2 parts.
            String[] parts = token.split(":\\s*", 2);
            String fieldName = parts[0].trim();
            String reason    = parts.length > 1 ? parts[1].trim() : "";

            if (!fieldName.isEmpty()) {
                result.put(fieldName, reason);
            }
        }

        return Collections.unmodifiableMap(result);
    }
}
