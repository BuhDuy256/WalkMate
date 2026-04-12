package com.walkmate.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P0-6 contract smoke tests — verifies the API response envelope shape is correct
 * across all three response categories that tests will encounter.
 *
 * <h3>Three categories verified</h3>
 * <ol>
 *   <li><b>HTTP 401 (unauthenticated)</b> — Spring Security blocks the request before
 *       any controller runs. No DomainException is thrown; the body may be empty.</li>
 *   <li><b>HTTP 400 (DomainException)</b> — business-rule violation handled by
 *       {@code GlobalExceptionHandler}. Body MUST follow
 *       {@code { success: false, data: null, error: { code, message }, timestamp }}.</li>
 *   <li><b>HTTP 422 (validation error)</b> — {@code @Valid} / {@code MethodArgumentNotValidException}.
 *       Body follows the same envelope. {@code error.message} MUST be a single
 *       comma-separated string like {@code "field: reason, field2: reason2"} — not a JSON array.</li>
 * </ol>
 *
 * <p>These assertions act as a contract guard: if the envelope shape changes, these
 * tests break immediately before any phase-specific test is written.
 */
class P06ContractSmokeTest extends AbstractIntegrationTest {

    // ── 1. HTTP 401 — Spring Security, unauthenticated ─────────────────────────

    @Test
    void unauthenticated_request_returns401() throws Exception {
        // No Authorization header — Spring Security should reject before any controller runs
        mockMvc.perform(get("/api/v1/profile/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── 2. HTTP 400 — DomainException envelope ────────────────────────────────

    @Test
    void domainException_returns400_withCorrectEnvelopeShape() throws Exception {
        // Trigger USER_INVALID_CREDENTIALS by logging in with a non-existent user
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "email",    "nobody@example.com",
                "password", "WrongPassword1!",
                "deviceId", "test-device"
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())                     // HTTP 400
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())           // null serialised as absent
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").isString())
                .andExpect(jsonPath("$.error.code").isNotEmpty())
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // ── 3. HTTP 422 — Validation error envelope + comma-separated message ─────

    @Test
    void validationError_returns422_withCorrectEnvelopeShape() throws Exception {
        // Blank fullname triggers @NotBlank on RegisterRequest
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "fullname", "",
                "email",    "valid@example.com",
                "password", "Password1!",
                "deviceId", "test-device"
        ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())            // HTTP 422
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").isString())      // must be String, not array
                .andExpect(jsonPath("$.error.message").value(containsString(":"))) // "field: reason" format
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void validationError_multipleFields_messageIsCommaSeparatedString() throws Exception {
        // Missing email and password both trigger violations — message must be ONE string
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "fullname", "Valid Name",
                "email",    "",          // blank → violation
                "password", "",          // blank → violation
                "deviceId", "test-device"
        ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                // error.message must be a JSON string — if it were an array this matcher would fail
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.message").value(containsString(",")));  // comma-separated
    }
}
