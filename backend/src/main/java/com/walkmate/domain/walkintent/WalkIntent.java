package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class WalkIntent {

    /** Minimum overlap required for a valid walk session (15 minutes). */
    public static final Duration MIN_WALK_DURATION = Duration.ofMinutes(15);

    private String id;
    private String hotspotId;
    private String userId;
    private Instant timeWindowStart;
    private Instant timeWindowEnd;
    private MatchingConstraints matchingConstraints;
    private IntentStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private long version;
    private boolean isPrivate;
    private String invitedFriendId;
    private String description;
    private List<UUID> excludedUserIds;

    protected WalkIntent() {
    }

    /** Rehydration constructor — called by the repository when loading from DB. */
    public WalkIntent(String id, String hotspotId, String userId,
                      Instant timeWindowStart, Instant timeWindowEnd,
                      MatchingConstraints matchingConstraints,
                      IntentStatus status,
                      Instant createdAt, Instant expiresAt,
                      long version,
                      boolean isPrivate, String invitedFriendId, String description,
                      List<UUID> excludedUserIds) {
        this.id = id;
        this.hotspotId = hotspotId;
        this.userId = userId;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.matchingConstraints = matchingConstraints;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.version = version;
        this.isPrivate = isPrivate;
        this.invitedFriendId = invitedFriendId;
        this.description = description;
        this.excludedUserIds = excludedUserIds != null ? excludedUserIds : new ArrayList<>();
    }

    private WalkIntent(String hotspotId, String userId,
                       Instant timeWindowStart, Instant timeWindowEnd,
                       MatchingConstraints matchingConstraints,
                       boolean isPrivate, String invitedFriendId, String description) {
        requireText(hotspotId, "Hotspot ID is required");
        requireText(userId, "User ID is required");
        if (!timeWindowEnd.isAfter(timeWindowStart)) {
            throw new DomainException(WalkIntentErrorCode.INVALID_TIME_RANGE);
        }
        if (Duration.between(timeWindowStart, timeWindowEnd).compareTo(MIN_WALK_DURATION) < 0) {
            throw new DomainException(WalkIntentErrorCode.INVALID_TIME_RANGE,
                    "Time window must be at least " + MIN_WALK_DURATION.toMinutes() + " minutes");
        }

        this.id = UUID.randomUUID().toString();
        this.hotspotId = hotspotId;
        this.userId = userId;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.matchingConstraints = matchingConstraints;
        this.status = IntentStatus.OPEN;
        this.createdAt = Instant.now();
        this.expiresAt = timeWindowEnd;
        this.version = 0;
        this.isPrivate = isPrivate;
        this.invitedFriendId = invitedFriendId;
        this.description = description;
        this.excludedUserIds = new ArrayList<>();
    }

    public static WalkIntent create(String hotspotId, String userId,
                                    Instant timeWindowStart, Instant timeWindowEnd,
                                    MatchingConstraints matchingConstraints,
                                    boolean isPrivate, String invitedFriendId, String description) {
        return new WalkIntent(hotspotId, userId, timeWindowStart, timeWindowEnd, matchingConstraints,
                isPrivate, invitedFriendId, description);
    }

    /** Transitions OPEN → MATCHING when a MatchProposal is created for this intent. */
    public void lock() {
        if (this.status != IntentStatus.OPEN) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_OPEN);
        }
        this.status = IntentStatus.MATCHING;
        this.version++;
    }

    /** Transitions MATCHING → OPEN when a MatchProposal is REJECTED or EXPIRED. */
    public void unlock() {
        if (this.status != IntentStatus.MATCHING) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_MATCHING);
        }
        this.status = IntentStatus.OPEN;
        this.version++;
    }

    /** Cancel is allowed from OPEN or MATCHING — the user may withdraw at any point. */
    public void cancel() {
        if (this.status == IntentStatus.CANCELLED) {
            throw new DomainException(WalkIntentErrorCode.INTENT_ALREADY_CANCELLED);
        }
        if (this.status == IntentStatus.CONSUMED) {
            throw new DomainException(WalkIntentErrorCode.INTENT_ALREADY_CONSUMED);
        }
        this.status = IntentStatus.CANCELLED;
        this.version++;
    }

    /** Transitions MATCHING → CONSUMED when the MatchProposal is CONFIRMED (I-3). */
    public void consume() {
        if (this.status != IntentStatus.MATCHING) {
            throw new DomainException(WalkIntentErrorCode.INTENT_NOT_MATCHING);
        }
        this.status = IntentStatus.CONSUMED;
        this.version++;
    }

    /** Transitions OPEN or MATCHING → EXPIRED (I-5, I-6, GAP-13, GAP-14). */
    public void expire() {
        if (this.status != IntentStatus.OPEN && this.status != IntentStatus.MATCHING) {
            throw new DomainException(WalkIntentErrorCode.INTENT_ALREADY_TERMINAL);
        }
        this.status = IntentStatus.EXPIRED;
        this.version++;
    }

    /**
     * Adds the given userId to this intent's per-intent exclude list (X-3, GAP-11).
     * The matching engine will not pair this intent with the excluded user again.
     */
    public void excludeUser(UUID userId) {
        if (!this.excludedUserIds.contains(userId)) {
            this.excludedUserIds.add(userId);
        }
        this.version++;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA, message);
        }
    }
}
