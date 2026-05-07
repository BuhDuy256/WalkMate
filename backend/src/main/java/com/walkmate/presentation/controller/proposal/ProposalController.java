package com.walkmate.presentation.controller.proposal;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.ProposalStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.walkintent.WalkIntentRepository;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.proposal.WalkProposalResponse;
import com.walkmate.presentation.mapper.proposal.ProposalMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Tag(name = "Proposals", description = "Match proposal accept / pass lifecycle")
@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final MatchingCommandService matchingCommandService;
    private final WalkSessionRepository  walkSessionRepository;
    private final WalkIntentRepository   walkIntentRepository;
    private final ProposalMapper         proposalMapper;
    private final UserQueryService       userQueryService;

    /**
     * GET /api/v1/proposals
     * Returns all PENDING proposals for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WalkProposalResponse>>> getProposals(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<WalkProposalResponse> responses = matchingCommandService
                .getPendingProposals(principal.userId())
                .stream()
                .map(p -> {
                    boolean callerIsA     = principal.userId().equals(p.getUserIdA());
                    String partnerId      = callerIsA ? p.getUserIdB() : p.getUserIdA();
                    String matchedIntentId = callerIsA ? p.getIntentIdB() : p.getIntentIdA();
                    return proposalMapper.toResponse(
                            p, principal.userId(), null,
                            resolveDisplayName(partnerId),
                            resolveAvatarUrl(partnerId),
                            resolveMatchedUserAge(partnerId),
                            resolveMatchedUserTrustScore(partnerId),
                            resolveOverlappingTags(principal.userId(), partnerId),
                            resolveIsPrivate(matchedIntentId));
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * POST /api/v1/proposals/{proposalId}/accept
     *
     * Records this user's acceptance.
     * - Partial (only one user): returns the updated proposal with status=PENDING and sessionId=null.
     * - Both accepted: executes P-3 protocol, returns proposal with status=CONFIRMED and sessionId set.
     */
    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<ApiResponse<WalkProposalResponse>> acceptProposal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String proposalId) {

        MatchProposal proposal = matchingCommandService.acceptProposal(proposalId, principal.userId());

        String sessionId = null;
        if (proposal.getStatus() == ProposalStatus.CONFIRMED) {
            sessionId = walkSessionRepository.findByProposalId(proposal.getProposalId())
                    .map(WalkSession::getSessionId)
                    .orElse(null);
        }

        boolean callerIsA      = principal.userId().equals(proposal.getUserIdA());
        String partnerId       = callerIsA ? proposal.getUserIdB() : proposal.getUserIdA();
        String matchedIntentId = callerIsA ? proposal.getIntentIdB() : proposal.getIntentIdA();

        return ResponseEntity.ok(ApiResponse.success(
                proposalMapper.toResponse(
                        proposal, principal.userId(), sessionId,
                        resolveDisplayName(partnerId),
                        resolveAvatarUrl(partnerId),
                        resolveMatchedUserAge(partnerId),
                        resolveMatchedUserTrustScore(partnerId),
                        resolveOverlappingTags(principal.userId(), partnerId),
                        resolveIsPrivate(matchedIntentId))));
    }

    /**
     * POST /api/v1/proposals/{proposalId}/pass
     * Rejects the proposal.
     * Public: both intents revert to OPEN and re-enter the matching pool.
     * Private: both intents transition to CANCELLED — the private invite is closed
     * and must not be publicised (SSOT lifecycle §1).
     */
    @PostMapping("/{proposalId}/pass")
    public ResponseEntity<ApiResponse<Void>> passProposal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String proposalId) {

        matchingCommandService.passProposal(proposalId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * DELETE /api/v1/proposals/{proposalId}
     * Hard-cancels the proposal and closes the caller's intent.
     * The partner's intent remains OPEN for further matching.
     */
    @DeleteMapping("/{proposalId}")
    public ResponseEntity<ApiResponse<Void>> cancelProposal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String proposalId) {

        matchingCommandService.cancelProposal(proposalId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveDisplayName(String userId) {
        try {
            return userQueryService.getDisplayName(UUID.fromString(userId));
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

    private String resolveAvatarUrl(String userId) {
        try {
            return userQueryService.getProfile(UUID.fromString(userId)).getAvatarUrl();
        } catch (Exception e) {
            return null;
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
}
