package com.walkmate.integration.auth;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for email/password authentication.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-01 Register Account</b> — T01-1 (happy path), T01-2 (duplicate email),
 *       T01-3 (validation errors)</li>
 *   <li><b>UC-02 Login</b> — T02-1 (happy path), T02-2 (wrong credentials)</li>
 * </ul>
 *
 * <h3>Implementation notes vs. use-case doc</h3>
 * <ul>
 *   <li>{@code POST /register} returns {@code 201} with a full token pair
 *       ({@code accessToken}, {@code refreshToken}, {@code tokenType}), not just
 *       {@code { email }}.  The controller maps the result via
 *       {@code UserMapper.toLoginUserResponse}.</li>
 *   <li>Duplicate-email error code is {@code USER_EMAIL_ALREADY_EXISTS}
 *       (not {@code USER_ALREADY_EXISTS}).</li>
 * </ul>
 */
class EmailAuthIntegrationTest extends AbstractIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL    = "/api/v1/auth/login";

    private static final String EMAIL    = "alice@example.com";
    private static final String PASSWORD = "Password1!";
    private static final String FULLNAME = "Alice";
    private static final String DEVICE   = "test-device-email";

    // ── T01-1: Register — Happy Path ──────────────────────────────────────────

    @Test
    void t01_1_register_validPayload_returns201WithTokenPair() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FULLNAME, EMAIL, PASSWORD, DEVICE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber());
    }

    // ── T01-2: Register — Duplicate Email ─────────────────────────────────────

    @Test
    void t01_2_register_duplicateEmail_returns400_USER_EMAIL_ALREADY_EXISTS() throws Exception {
        // First registration succeeds
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FULLNAME, EMAIL, PASSWORD, DEVICE)))
                .andExpect(status().isCreated());

        // Second registration with the same email must fail
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Bob", EMAIL, "OtherPass1!", "device-002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_EMAIL_ALREADY_EXISTS"));
    }

    // ── T01-3: Register — Validation Errors ───────────────────────────────────

    @Test
    void t01_3a_register_blankFullname_returns422_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("", EMAIL, PASSWORD, DEVICE)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                // error.message must be a String, not a JSON array (Appendix A contract)
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.message").value(containsString(":")));
    }

    @Test
    void t01_3b_register_multipleBlankFields_errorMessageIsCommaSeparatedString() throws Exception {
        // Blank email + blank password both trigger violations.
        // Per Appendix A: error.message is ONE comma-separated String, never a JSON array.
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FULLNAME, "", "", DEVICE)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.message").value(containsString(",")));
    }

    // ── T02-1: Login — Happy Path ─────────────────────────────────────────────

    @Test
    void t02_1_login_validCredentials_returns200WithTokenPair() throws Exception {
        // Arrange — user must exist before login
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FULLNAME, EMAIL, PASSWORD, DEVICE)))
                .andExpect(status().isCreated());

        // Act + Assert
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD, DEVICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber());
    }

    // ── T02-2: Login — Wrong Credentials ──────────────────────────────────────

    @Test
    void t02_2_login_wrongPassword_returns400_USER_INVALID_CREDENTIALS() throws Exception {
        // Arrange — user must exist so the failure is a credential mismatch, not "not found"
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(FULLNAME, EMAIL, PASSWORD, DEVICE)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "WrongPassword99!", DEVICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_INVALID_CREDENTIALS"));
    }

    // ── Payload builders ───────────────────────────────────────────────────────

    private String registerBody(String fullname, String email,
                                String password, String deviceId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "fullname", fullname,
                "email",    email,
                "password", password,
                "deviceId", deviceId
        ));
    }

    private String loginBody(String email, String password, String deviceId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email",    email,
                "password", password,
                "deviceId", deviceId
        ));
    }
}
