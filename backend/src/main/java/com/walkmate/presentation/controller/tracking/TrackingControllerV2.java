package com.walkmate.presentation.controller.tracking;

import com.walkmate.application.tracking.TrackingCommandServiceV2;
import com.walkmate.application.tracking.TrackingSealService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.presentation.dto.request.tracking.SealStreamRequest;
import com.walkmate.presentation.dto.request.tracking.TrackingBatchRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.tracking.SealStreamResponse;
import com.walkmate.presentation.dto.response.tracking.TrackingBatchResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tracking v2",
        description = "GPS route-point sync v2: immutable batched writes, sequence numbers, stream seal")
@RestController
@RequestMapping("/api/v2/tracking")
@RequiredArgsConstructor
public class TrackingControllerV2 {

    private final TrackingCommandServiceV2 commandService;
    private final TrackingSealService      sealService;

    /**
     * POST /api/v2/tracking/batches
     *
     * Pushes one immutable batch of GPS points for a (session, user) stream.
     * The batch id is the idempotency key: replays are verified against the
     * stored row and re-ACKed; a different payload carried by the same id is
     * rejected. Sequence ranges must be contiguous and non-overlapping —
     * overlaps are rejected by the database (GiST EXCLUDE) with 409.
     *
     * <p>The batch is only ACKed after its chunk row is committed inside the
     * same transaction that locked the stream row (commit-or-proof).</p>
     */
    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<TrackingBatchResponse>> pushBatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TrackingBatchRequest request) {
        TrackingBatchResponse response =
                commandService.pushBatch(request, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v2/tracking/streams/{streamId}/expire
     *
     * Closes the late-upload window for a sealed stream that never reached full
     * coverage. This is idempotent housekeeping and is intentionally a separate
     * transaction from {@code pushBatch}: the {@code EXPIRED} transition must
     * survive even though the rejected batch's own write is rolled back.
     * Calling it on a stream that is still within its deadline is a no-op.
     */
    @PostMapping("/streams/{streamId}/expire")
    public ResponseEntity<ApiResponse<SealStreamResponse>> expireStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("streamId") String streamId) {
        SealStreamResponse response = sealService.expire(streamId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v2/tracking/streams/{streamId}/seal
     *
     * Declares the client's final sequence. The server stamps the upload
     * deadline (now + late-upload window) and evaluates the stream's
     * terminal state. Retries are idempotent.
     */
    @PostMapping("/streams/{streamId}/seal")
    public ResponseEntity<ApiResponse<SealStreamResponse>> sealStream(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("streamId") String streamId,
            @Valid @RequestBody SealStreamRequest request) {
        SealStreamResponse response = sealService.seal(streamId, principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
