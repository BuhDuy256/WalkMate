package com.walkmate.presentation.controller.hotspot;

import com.walkmate.application.hotspot.HotspotQueryService;
import com.walkmate.domain.hotspot.Hotspot;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.hotspot.HotspotResponse;
import com.walkmate.presentation.mapper.hotspot.HotspotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hotspots")
@RequiredArgsConstructor
public class HotspotController {

    private final HotspotQueryService hotspotQueryService;
    private final HotspotMapper hotspotMapper;

    /**
     * GET /api/v1/hotspots
     * Returns all available hotspots with their current active walker counts.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<HotspotResponse>>> getAllHotspots() {

        // [SERVICE CALL]
        // List<Hotspot> hotspots = hotspotQueryService.getAllHotspots();
        // return ResponseEntity.ok(ApiResponse.success(hotspotMapper.toResponseList(hotspots)));

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * GET /api/v1/hotspots/{id}
     * Returns a single hotspot by ID. Throws HOTSPOT_NOT_FOUND if it doesn't exist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotspotResponse>> getHotspotById(
            @PathVariable String id) {

        // [SERVICE CALL]
        // Hotspot hotspot = hotspotQueryService.getHotspotById(id);
        // return ResponseEntity.ok(ApiResponse.success(hotspotMapper.toResponse(hotspot)));

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
