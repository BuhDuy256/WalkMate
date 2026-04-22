package com.walkmate.presentation.controller.proposal;

import com.walkmate.application.proposal.MatchingCommandService;
import com.walkmate.application.user.UserPrincipal;
import com.walkmate.application.user.UserQueryService;
import com.walkmate.domain.proposal.MatchProposal;
import com.walkmate.domain.proposal.ProposalStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.presentation.dto.response.ApiResponse;
import com.walkmate.presentation.dto.response.proposal.WalkProposalResponse;
import com.walkmate.presentation.mapper.proposal.ProposalMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Proposals", description = "Match proposal accept / pass lifecycle")
@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final MatchingCommandService matchingCommandService;
    private final WalkSessionRepository  walkSessionRepository;
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
                    boolean callerIsA = principal.userId().equals(p.getUserIdA());
                    String partnerId = callerIsA ? p.getUserIdB() : p.getUserIdA();
                    return proposalMapper.toResponse(p, principal.userId(), null,
                            resolveDisplayName(partnerId));
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

        boolean callerIsA = principal.userId().equals(proposal.getUserIdA());
        String partnerId = callerIsA ? proposal.getUserIdB() : proposal.getUserIdA();

        return ResponseEntity.ok(ApiResponse.success(
                proposalMapper.toResponse(proposal, principal.userId(), sessionId,
                        resolveDisplayName(partnerId))));
    }

    /**
     * POST /api/v1/proposals/{proposalId}/pass
     * Rejects the proposal. Both intents remain OPEN for further matching.
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
    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveDisplayName(String userId) {
        try {
            return userQueryService.getDisplayName(UUID.fromString(userId));
        } catch (Exception e) {
            return null;
        }
    }

    @DeleteMapping("/{proposalId}")
    public ResponseEntity<ApiResponse<Void>> cancelProposal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String proposalId) {

        matchingCommandService.cancelProposal(proposalId, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
