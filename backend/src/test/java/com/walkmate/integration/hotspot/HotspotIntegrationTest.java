package com.walkmate.integration.hotspot;

import com.walkmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for hotspot discovery use cases.
 *
 * <h3>Use cases covered</h3>
 * <ul>
 *   <li><b>UC-14 Browse Hotspot Map</b> — T14-1 (public list), T14-2 (not found)</li>
 * </ul>
 *
 * <h3>Key observations</h3>
 * <ul>
 *   <li>{@code GET /api/v1/hotspots} is a public endpoint — no {@code Authorization}
 *       header required. Spring Security must permit it without a token.</li>
 *   <li>Each {@link com.walkmate.presentation.dto.response.hotspot.HotspotResponse}
 *       includes {@code openIntentCount} — populated from a JOIN/query on the
 *       walk_intent table. For freshly seeded hotspots with no intents, the value is 0.</li>
 *   <li>{@code GET /api/v1/hotspots/{id}} returns HTTP 400 with
 *       {@code error.code = HOTSPOT_NOT_FOUND} when the UUID does not exist
 *       (DomainException → GlobalExceptionHandler).</li>
 * </ul>
 */
class HotspotIntegrationTest extends AbstractIntegrationTest {

    private static final String HOTSPOTS_URL = "/api/v1/hotspots";

    // ── T14-1: Browse Hotspot Map — Unauthenticated (Public Endpoint) ─────────

    @Test
    void t14_1_getAllHotspots_noAuth_returns200_withHotspotList() throws Exception {
        // Arrange — seed two hotspots directly via JDBC (public data, no user required)
        dataSeeder.seedHotspot("Landmark 81",    10.7950, 106.7218);
        dataSeeder.seedHotspot("Tao Dan Park",   10.7769, 106.6903);

        // Act + Assert — public endpoint, no Authorization header
        mockMvc.perform(get(HOTSPOTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data[0].openIntentCount").exists());
    }

    // ── T14-2: Get Single Hotspot — Not Found ─────────────────────────────────

    @Test
    void t14_2_getHotspotById_nonExistent_returns400_HOTSPOT_NOT_FOUND() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        mockMvc.perform(get(HOTSPOTS_URL + "/" + nonExistentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("HOTSPOT_NOT_FOUND"));
    }
}
