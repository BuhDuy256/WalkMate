package com.walkmate.integration.profile;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for user profile and account settings use cases.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-03 View My Profile</b>       — T03-1 (happy path), T03-2 (unauthenticated)</li>
 *   <li><b>UC-04 Edit My Profile</b>        — T04-1 (happy path), T04-2 (validation errors)</li>
 *   <li><b>UC-05 Upload Avatar</b>          — T05-1 (happy path)</li>
 *   <li><b>UC-06 Register FCM Token</b>     — T06-1 (happy path)</li>
 *   <li><b>UC-13 Set Profile Visibility</b> — T13-1 (toggle PRIVATE ↔ PUBLIC)</li>
 * </ul>
 *
 * <h3>Key observations</h3>
 * <ul>
 *   <li>{@code UserProfile.createForLocal()} initialises {@code searchRadius = 5000}
 *       and {@code fullName} from the registration payload.</li>
 *   <li>{@code searchRadius} is a primitive {@code int} with {@code @Min(1)} — must always
 *       be provided in {@code PUT /profile/me} payloads.</li>
 *   <li>{@code visibility_mode} lives in {@code user_account} table, column {@code visibility_mode}.</li>
 * </ul>
 */
class ProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String PROFILE_ME_URL    = "/api/v1/profile/me";
    private static final String AVATAR_URL         = "/api/v1/profile/avatar";
    private static final String FCM_TOKEN_URL      = "/api/v1/users/me/fcm-token";
    private static final String VISIBILITY_URL     = "/api/v1/users/me/visibility";

    // ── T03-1: View My Profile — Authenticated ────────────────────────────────

    @Test
    void t03_1_getMyProfile_authenticated_returns200_withProfileFields() throws Exception {
        String token = authFactory.createAndLoginUser("profile.view@example.com", "Password1!");

        mockMvc.perform(get(PROFILE_ME_URL)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.searchRadius").value(5000));
    }

    // ── T03-2: View My Profile — Unauthenticated ──────────────────────────────

    @Test
    void t03_2_getMyProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PROFILE_ME_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── T04-1: Edit My Profile — Happy Path ───────────────────────────────────

    @Test
    void t04_1_updateProfile_validPayload_returns200_withUpdatedFields() throws Exception {
        String token = authFactory.createAndLoginUser("profile.edit@example.com", "Password1!");

        Map<String, Object> payload = Map.of(
                "fullName",     "Updated Name",
                "bio",          "A short bio",
                "searchRadius", 3000,
                "tags",         List.of("hiking", "cycling")
        );

        mockMvc.perform(put(PROFILE_ME_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.bio").value("A short bio"))
                .andExpect(jsonPath("$.data.searchRadius").value(3000))
                .andExpect(jsonPath("$.data.tags[0]").exists());
    }

    // ── T04-2: Edit My Profile — Validation Errors ────────────────────────────

    @Test
    void t04_2a_updateProfile_bioTooLong_returns422() throws Exception {
        String token = authFactory.createAndLoginUser("profile.bio@example.com", "Password1!");
        String longBio = "x".repeat(501);

        Map<String, Object> payload = Map.of(
                "bio",          longBio,
                "searchRadius", 5000,
                "tags",         List.of()
        );

        mockMvc.perform(put(PROFILE_ME_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void t04_2b_updateProfile_tooManyTags_returns422() throws Exception {
        String token = authFactory.createAndLoginUser("profile.tags@example.com", "Password1!");
        List<String> elevenTags = Collections.nCopies(11, "tag");

        Map<String, Object> payload = Map.of(
                "searchRadius", 5000,
                "tags",         elevenTags
        );

        mockMvc.perform(put(PROFILE_ME_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ── T05-1: Upload Avatar — Happy Path ─────────────────────────────────────

    @Test
    void t05_1_uploadAvatar_validImage_returns200_withNonNullAvatarUrl() throws Exception {
        String token = authFactory.createAndLoginUser("profile.avatar@example.com", "Password1!");

        // Minimal valid JPEG header bytes (not a real image, but passes extension detection)
        byte[] fakeImageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, fakeImageBytes);

        mockMvc.perform(multipart(AVATAR_URL)
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty());
    }

    // ── T06-1: Register FCM Token — Happy Path ────────────────────────────────

    @Test
    void t06_1_updateFcmToken_validToken_returns200() throws Exception {
        String token = authFactory.createAndLoginUser("profile.fcm@example.com", "Password1!");

        mockMvc.perform(patch(FCM_TOKEN_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("fcmToken", "fcm-device-token-abc123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── T13-1: Set Profile Visibility ─────────────────────────────────────────

    @Test
    void t13_1_setVisibility_togglePrivateThenPublic_returnsOk_andDbReflectsChange() throws Exception {
        String email = "profile.visibility@example.com";
        String token = authFactory.createAndLoginUser(email, "Password1!");

        UUID userId = getUserIdByEmail(email);

        // Set to PRIVATE
        MvcResult privateResult = mockMvc.perform(patch(VISIBILITY_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "PRIVATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visibilityMode").value("PRIVATE"))
                .andReturn();

        assertThat(queryVisibilityMode(userId)).isEqualTo("PRIVATE");

        // Toggle back to PUBLIC
        mockMvc.perform(patch(VISIBILITY_URL)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "PUBLIC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityMode").value("PUBLIC"));

        assertThat(queryVisibilityMode(userId)).isEqualTo("PUBLIC");
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private UUID getUserIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM public.user_account WHERE email = ?",
                UUID.class, email);
    }

    private String queryVisibilityMode(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT visibility_mode FROM public.user_account WHERE user_id = ?",
                String.class, userId);
    }
}
