package com.walkmate.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Utility for extracting a structured {@link ApiError} from any non-2xx
 * Retrofit response.
 *
 * <h3>Why this exists</h3>
 * For HTTP 400 and 422 responses, Retrofit sets {@code response.body()} to
 * {@code null}. The actual JSON error payload is in {@code response.errorBody()}.
 * Repository classes that read {@code body} on error therefore always receive
 * {@code null} and can never see {@code error.code} or {@code error.message}.
 * This parser reads {@code errorBody} instead.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * if (!resp.isSuccessful()) {
 *     ApiError apiError = ErrorParser.extractApiError(resp, "MY_FALLBACK_CODE");
 *     callback.onError(new Exception(apiError.getCode()));
 * }
 * }</pre>
 */
public final class ErrorParser {

    private static final Gson GSON = new Gson();

    /** Utility class — no instances. */
    private ErrorParser() {}

    /**
     * Reads {@code response.errorBody()}, deserializes it as an
     * {@link ApiResponse} envelope, and returns the inner {@link ApiError}.
     *
     * <p>Returns a synthetic {@link ApiError} with {@code fallbackCode} if:
     * <ul>
     *   <li>the response is successful (guard against misuse),</li>
     *   <li>{@code errorBody} is null or empty,</li>
     *   <li>the body cannot be parsed as JSON, or</li>
     *   <li>the parsed envelope has no {@code error} object.</li>
     * </ul>
     *
     * @param response     The Retrofit {@link Response} whose error body to read.
     * @param fallbackCode Code used in the returned {@link ApiError} when parsing fails.
     * @return             A non-null {@link ApiError}.
     */
    @SuppressWarnings("rawtypes")
    public static ApiError extractApiError(Response<?> response, String fallbackCode) {
        if (response.isSuccessful()) {
            return new ApiError(fallbackCode, null);
        }

        ResponseBody errorBody = response.errorBody();
        if (errorBody == null) {
            return new ApiError(fallbackCode, null);
        }

        try {
            String json = errorBody.string();
            if (json == null || json.isEmpty()) {
                return new ApiError(fallbackCode, null);
            }

            // Use raw ApiResponse so Gson does not need a TypeToken for the
            // generic <T> — we only care about the error field, not data.
            ApiResponse envelope = GSON.fromJson(json, ApiResponse.class);
            if (envelope != null && envelope.getError() != null) {
                return envelope.getError();
            }
        } catch (IOException | JsonSyntaxException e) {
            // Fall through to fallback below.
        }

        return new ApiError(fallbackCode, null);
    }
}
