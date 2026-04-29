package com.walkmate.presentation.controller.walkintent;

import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.walkintent.CreateIntentResult;
import com.walkmate.application.walkintent.CreateWalkIntentCommand;
import com.walkmate.application.walkintent.WalkIntentCommandService;
import com.walkmate.application.walkintent.WalkIntentQueryService;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.shared.exception.DomainException;
import com.walkmate.domain.user.UserProfile;
import com.walkmate.domain.walkintent.WalkIntentErrorCode;
import com.walkmate.presentation.dto.request.walkintent.CreateWalkIntentRequest;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.proposal.WalkProposalResponse;
import com.walkmate.presentation.dto.response.walkintent.CreateIntentResponse;
import com.walkmate.presentation.dto.response.walkintent.WalkIntentResponse;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.presentation.mapper.proposal.ProposalMapper;
import com.walkmate.presentation.mapper.walkintent.WalkIntentMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// Pre-defined interest tags surfaced during onboarding.
// Stored here so the Android client can request the canonical list without
// a dedicated endpoint and so the backend can validate against them.
// Expanding this list is a non-breaking change.

@Tag(name = "WalkIntent", description = "Create, list, and cancel walk intents")
@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
public class WalkIntentController {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Canonical onboarding tags presented as selection options on the Android client. */
    public static final List<String> DEFAULT_TAGS = List.of(
            "Strolling",
            "Power Walking",
            "Talkative",
            "Scenic & Quiet",
            "Pet Friendly"
    );

    private final WalkIntentCommandService walkIntentCommandService;
    private final WalkIntentQueryService   walkIntentQueryService;
    private final MatchingCommandService   matchingCommandService;
    private final WalkIntentMapper         walkIntentMapper;
    private final ProposalMapper           proposalMapper;
    private final UserQueryService         userQueryService;
    private final WalkIntentRepository     walkIntentRepository;

    /**
     * POST /api/v1/intents
     * Creates a new walk intent for the authenticated user at a given hotspot.
     * userId is resolved from the JWT — never trusted from the request body.
     * date + timeStart/timeEnd floats are converted to Instant using VN timezone.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateIntentResponse>> createIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWalkIntentRequest request) {

        // Onboarding gate: gender and at least one interest tag are mandatory before a
        // user can participate in public matching.  Missing these produces a 403 so the
        // Android client knows to navigate to the onboarding screen rather than show
        // a generic error.  Gender ANY is valid — only null is rejected.
        UUID callerId = UUID.fromString(principal.userId());
        UserProfile profile = userQueryService.getMyProfile(callerId);
        List<String> tags   = userQueryService.getTagsByUserId(callerId);
        if (profile.getGender() == null || tags.isEmpty()) {
            throw new DomainException(WalkIntentErrorCode.PROFILE_INCOMPLETE_FOR_MATCHING);
        }

        Instant start = toInstant(request.date(), request.timeStart());
        Instant end   = toInstant(request.date(), request.timeEnd());

        CreateIntentResult result = walkIntentCommandService.createIntent(
                new CreateWalkIntentCommand(
                        request.hotspotId(),
                        principal.userId(),
                        start,
                        end,
                        request.ageMin(),
                        request.ageMax(),
                        request.preferredGender() != null ? request.preferredGender() : "ANY",
                        request.isPrivate(),
                        request.invitedFriendId(),
                        request.description()
                )
        );

        WalkIntentResponse intentResp = walkIntentMapper.toResponse(result.intent());
        WalkProposalResponse proposalResp = null;
        if (result.proposal() != null) {
            MatchProposal p = result.proposal();
            boolean callerIsA = principal.userId().equals(p.getUserIdA());
            String partnerId = callerIsA ? p.getUserIdB() : p.getUserIdA();
            proposalResp = proposalMapper.toResponse(p, principal.userId(), null,
                    resolveDisplayName(partnerId),
                    resolveAvatarUrl(partnerId),
                    resolveMatchedUserAge(partnerId),
                    resolveMatchedUserTrustScore(partnerId),
                    resolveOverlappingTags(principal.userId(), partnerId),
                    request.isPrivate());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new CreateIntentResponse(intentResp, proposalResp)));
    }

    /**
     * GET /api/v1/intents
     * Returns all OPEN intents for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WalkIntentResponse>>> listActiveIntents(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<WalkIntentResponse> responses = walkIntentQueryService
                .listActiveIntents(principal.userId().toString())
                .stream()
                .map(walkIntentMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * POST /api/v1/intents/{intentId}/match
     * Triggers the matching engine for the given intent.
     * Returns the proposal if a candidate was found (or an existing PENDING proposal).
     * Returns 204 No Content if no candidate is available yet.
     *
     * POST is correct here: every call may mutate state (create a MatchProposal,
     * transition the intent from OPEN → MATCHING). GET is reserved for safe,
     * idempotent reads.
     */
    @PostMapping("/{intentId}/match")
    public ResponseEntity<ApiResponse<WalkProposalResponse>> triggerMatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String intentId) {
        MatchProposal proposal = matchingCommandService
                .findOrCreateProposal(intentId, principal.userId())
                .orElse(null);
        if (proposal == null) {
            return ResponseEntity.noContent().build();
        }
        boolean callerIsA = principal.userId().equals(proposal.getUserIdA());
        String partnerId = callerIsA ? proposal.getUserIdB() : proposal.getUserIdA();
        return ResponseEntity.ok(ApiResponse.success(
                proposalMapper.toResponse(proposal, principal.userId(), null,
                        resolveDisplayName(partnerId),
                        resolveAvatarUrl(partnerId),
                        resolveMatchedUserAge(partnerId),
                        resolveMatchedUserTrustScore(partnerId),
                        resolveOverlappingTags(principal.userId(), partnerId),
                        resolveIsPrivate(intentId))));
    }

    /**
     * DELETE /api/v1/intents/{intentId}
     * Cancels an intent. Only the owner can cancel their own intent.
     */
    @DeleteMapping("/{intentId}")
    public ResponseEntity<ApiResponse<Void>> cancelIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String intentId) {
        walkIntentCommandService.cancelIntent(intentId, principal.userId().toString());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveDisplayName(String userId) {
        try {
            return userQueryService.getDisplayName(UUID.fromString(userId));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveAvatarUrl(String userId) {
        try {
            return userQueryService.getProfile(UUID.fromString(userId)).getAvatarUrl();
        } catch (Exception e) {
            return null;
        }
    }

    private int resolveMatchedUserAge(String userId) {
        try {
            LocalDate dob = userQueryService.getProfile(UUID.fromString(userId)).getDateOfBirth();
            return dob != null ? Period.between(dob, LocalDate.now()).getYears() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int resolveMatchedUserTrustScore(String userId) {
        try {
            return userQueryService.getUser(UUID.fromString(userId)).getTrustScore();
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> resolveOverlappingTags(String callerUserId, String partnerUserId) {
        try {
            List<String> callerTags  = userQueryService.getTagsByUserId(UUID.fromString(callerUserId));
            List<String> partnerTags = userQueryService.getTagsByUserId(UUID.fromString(partnerUserId));
            List<String> intersection = new ArrayList<>(callerTags);
            intersection.retainAll(partnerTags);
            return intersection;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean resolveIsPrivate(String intentId) {
        try {
            return walkIntentRepository.findById(intentId)
                    .map(i -> i.isPrivate())
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts a yyyy-MM-dd date string and a fractional-hour float
     * (e.g. 17.5 = 17:30, 8.25 = 08:15) to an Instant in the VN timezone.
     */
    private Instant toInstant(String date, float hourFloat) {
        int totalMinutes = Math.round(hourFloat * 60);
        LocalDate localDate = LocalDate.parse(date);
        LocalTime localTime = LocalTime.of(totalMinutes / 60, totalMinutes % 60);
        return LocalDateTime.of(localDate, localTime).atZone(VN_ZONE).toInstant();
    }

}
