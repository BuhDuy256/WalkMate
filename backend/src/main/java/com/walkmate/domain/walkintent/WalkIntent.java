package com.walkmate.domain.walkintent;

import com.walkmate.domain.shared.exception.DomainException;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
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

    protected WalkIntent() {
    }

    /** Rehydration constructor — called by the repository when loading from DB. */
    public WalkIntent(String id, String hotspotId, String userId,
                      Instant timeWindowStart, Instant timeWindowEnd,
                      MatchingConstraints matchingConstraints,
                      IntentStatus status,
                      Instant createdAt, Instant expiresAt,
                      long version) {
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
    }

    private WalkIntent(String hotspotId, String userId,
                       Instant timeWindowStart, Instant timeWindowEnd,
                       MatchingConstraints matchingConstraints) {
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
    }

    public static WalkIntent create(String hotspotId, String userId,
                                    Instant timeWindowStart, Instant timeWindowEnd,
                                    MatchingConstraints matchingConstraints) {
        return new WalkIntent(hotspotId, userId, timeWindowStart, timeWindowEnd, matchingConstraints);
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

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WalkIntentErrorCode.INVALID_INTENT_DATA, message);
        }
    }
}
