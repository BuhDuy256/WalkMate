package com.walkmate.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-test helper that drives the real Auth + Profile HTTP stack to
 * create users and obtain Bearer tokens.
 *
 * <p>This is a plain utility class (not a Spring bean). Instantiate it once per
 * test via {@link AbstractIntegrationTest#authFactory}.
 *
 * <p>All methods assert the expected HTTP status internally — a test failure
 * here means the setup precondition failed, not the system under test.
 */
public class AuthTokenFactory {

    /** Device ID used for all factory-created sessions. */
    public static final String DEFAULT_DEVICE_ID = "test-device-001";

    private final MockMvc      mockMvc;
    private final ObjectMapper objectMapper;

    public AuthTokenFactory(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc      = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user and returns a ready-to-use {@code "Bearer <token>"} string.
     *
     * <p>Covers UC-01 + UC-02 as a side effect.
     *
     * @param email    unique e-mail for this user (caller must ensure uniqueness per test)
     * @param password plaintext password (min 8 chars)
     */
    public String createAndLoginUser(String email, String password) throws Exception {
        register(email, password, "Test User");
        return login(email, password);
    }

    /**
     * Registers a user, logs in, and seeds basic profile data via
     * {@code PUT /api/v1/profile/me}.
     *
     * <p>Use this when a test requires a user whose profile fields
     * (name, bio, tags) are meaningful for the scenario under test.
     *
     * @param email       unique e-mail
     * @param password    plaintext password (min 8 chars)
     * @param fullName    display name written to the profile
     * @param bio         short bio (max 500 chars, nullable)
     * @param tags        interest tags (max 10 items, nullable)
     */
    public String createAndLoginUserWithProfile(
            String email,
            String password,
            String fullName,
            String bio,
            List<String> tags) throws Exception {

        register(email, password, fullName);
        String token = login(email, password);
        updateProfile(token, fullName, bio, tags);
        return token;
    }

    /**
     * Logs in an already-registered user and returns a Bearer token.
     * Useful when a user was registered in a previous step and a fresh token is needed.
     */
    public String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email",    email,
                "password", password,
                "deviceId", DEFAULT_DEVICE_ID
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return "Bearer " + extractAccessToken(result);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void register(String email, String password, String fullname) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "fullname", fullname,
                "email",    email,
                "password", password,
                "deviceId", DEFAULT_DEVICE_ID
        ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void updateProfile(String bearerToken,
                               String fullName,
                               String bio,
                               List<String> tags) throws Exception {
        Map<String, Object> profilePayload = new java.util.HashMap<>();
        profilePayload.put("fullName",     fullName);
        profilePayload.put("bio",          bio);
        profilePayload.put("tags",         tags != null ? tags : List.of());

        String body = objectMapper.writeValueAsString(profilePayload);

        mockMvc.perform(put("/api/v1/profile/me")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody)
                .at("/data/accessToken")
                .asText();
    }
}
