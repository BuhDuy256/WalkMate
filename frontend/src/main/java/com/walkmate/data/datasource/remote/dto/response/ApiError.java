package com.walkmate.data.datasource.remote.dto.response;

/**
 * Represents the {@code error} envelope returned by the backend on any
 * non-2xx response:
 * <pre>
 * {
 *   "success": false,
 *   "data": null,
 *   "error": { "code": "SOME_ERROR_CODE", "message": "Human-readable description." }
 * }
 * </pre>
 *
 * For HTTP 422 (Jakarta validation), {@code message} is a comma-separated
 * string of {@code "field: reason"} pairs. Use
 * {@link com.walkmate.core.util.ValidationHelper#parse(String)} to split it.
 */
public class ApiError {

    private String code;
    private String message;

    /** Empty constructor for Gson. */
    public ApiError() {}

    public ApiError(String code, String message) {
        this.code    = code;
        this.message = message;
    }

    public String getCode()    { return code; }
    public String getMessage() { return message; }
}
