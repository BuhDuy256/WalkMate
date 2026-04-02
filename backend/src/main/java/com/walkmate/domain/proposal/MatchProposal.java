package com.walkmate.domain.proposal;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class MatchProposal {

    private String proposalId;
    private String intentIdA;
    private String intentIdB;
    /** Derived via JOIN — not stored in match_proposal, loaded from walk_intent. */
    private String userIdA;
    private String userIdB;
    private double proposedLocationLat;
    private double proposedLocationLng;
    private Instant proposedStartTime;
    private Instant proposedEndTime;
    private boolean acceptedByA;
    private boolean acceptedByB;
    private ProposalStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant confirmedAt;

    protected MatchProposal() {
    }

    /** Rehydration constructor — called by the repository when loading from DB. */
    public MatchProposal(String proposalId, String intentIdA, String intentIdB,
                         String userIdA, String userIdB,
                         double proposedLocationLat, double proposedLocationLng,
                         Instant proposedStartTime, Instant proposedEndTime,
                         boolean acceptedByA, boolean acceptedByB,
                         ProposalStatus status,
                         Instant createdAt, Instant expiresAt, Instant confirmedAt) {
        this.proposalId = proposalId;
        this.intentIdA = intentIdA;
        this.intentIdB = intentIdB;
        this.userIdA = userIdA;
        this.userIdB = userIdB;
        this.proposedLocationLat = proposedLocationLat;
        this.proposedLocationLng = proposedLocationLng;
        this.proposedStartTime = proposedStartTime;
        this.proposedEndTime = proposedEndTime;
        this.acceptedByA = acceptedByA;
        this.acceptedByB = acceptedByB;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.confirmedAt = confirmedAt;
    }

    private MatchProposal(String intentIdA, String intentIdB,
                          String userIdA, String userIdB,
                          double proposedLocationLat, double proposedLocationLng,
                          Instant proposedStartTime, Instant proposedEndTime,
                          Instant expiresAt) {
        this.proposalId = UUID.randomUUID().toString();
        this.intentIdA = intentIdA;
        this.intentIdB = intentIdB;
        this.userIdA = userIdA;
        this.userIdB = userIdB;
        this.proposedLocationLat = proposedLocationLat;
        this.proposedLocationLng = proposedLocationLng;
        this.proposedStartTime = proposedStartTime;
        this.proposedEndTime = proposedEndTime;
        this.acceptedByA = false;
        this.acceptedByB = false;
        this.status = ProposalStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.confirmedAt = null;
    }

    public static MatchProposal create(String intentIdA, String intentIdB,
                                       String userIdA, String userIdB,
                                       double proposedLocationLat, double proposedLocationLng,
                                       Instant proposedStartTime, Instant proposedEndTime,
                                       Instant expiresAt) {
        return new MatchProposal(intentIdA, intentIdB, userIdA, userIdB,
                proposedLocationLat, proposedLocationLng,
                proposedStartTime, proposedEndTime, expiresAt);
    }

    // ── Rich behaviour ────────────────────────────────────────────────────────

    /**
     * Records this user's acceptance.
     *
     * @param callerIntentId the intentId that belongs to the accepting user
     * @return true when both parties have now accepted — caller must create session
     */
    public boolean recordAcceptance(String callerIntentId) {
        if (this.status != ProposalStatus.PENDING) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_ALREADY_TERMINAL);
        }
        if (callerIntentId.equals(this.intentIdA)) {
            this.acceptedByA = true;
        } else if (callerIntentId.equals(this.intentIdB)) {
            this.acceptedByB = true;
        } else {
            throw new DomainException(ProposalErrorCode.PROPOSAL_NOT_PARTICIPANT);
        }
        return this.acceptedByA && this.acceptedByB;
    }

    public void reject() {
        if (this.status != ProposalStatus.PENDING) {
            throw new DomainException(ProposalErrorCode.PROPOSAL_ALREADY_TERMINAL);
        }
        this.status = ProposalStatus.REJECTED;
    }

    public void confirm(Instant confirmedAt) {
        this.status = ProposalStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    /** Returns which intentId belongs to the given userId, or null if not a participant. */
    public String resolveIntentIdForUser(String userId) {
        if (userId.equals(this.userIdA)) return this.intentIdA;
        if (userId.equals(this.userIdB)) return this.intentIdB;
        return null;
    }
}
