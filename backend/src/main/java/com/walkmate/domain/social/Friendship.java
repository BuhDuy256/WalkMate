package com.walkmate.domain.social;

import com.walkmate.domain.shared.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

public class Friendship {

    private final String  friendshipId;
    private final UUID    requesterId;
    private final UUID    addresseeId;
    private       String  status;
    private       long    version;
    private final Instant createdAt;
    private       Instant updatedAt;

    public Friendship(String friendshipId, UUID requesterId, UUID addresseeId,
                      String status, long version, Instant createdAt, Instant updatedAt) {
        this.friendshipId = friendshipId;
        this.requesterId  = requesterId;
        this.addresseeId  = addresseeId;
        this.status       = status;
        this.version      = version;
        this.createdAt    = createdAt;
        this.updatedAt    = updatedAt;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Friendship create(UUID requesterId, UUID addresseeId) {
        return new Friendship(UUID.randomUUID().toString(), requesterId, addresseeId,
                "PENDING", 0L, Instant.now(), Instant.now());
    }

    // ── State transitions ─────────────────────────────────────────────────────

    public void accept() {
        guardPending();
        this.status    = "ACCEPTED";
        this.updatedAt = Instant.now();
    }

    public void decline() {
        guardPending();
        this.status    = "DECLINED";
        this.updatedAt = Instant.now();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isPending()  { return "PENDING".equals(status); }
    public boolean isAccepted() { return "ACCEPTED".equals(status); }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getFriendshipId() { return friendshipId; }
    public UUID    getRequesterId()  { return requesterId; }
    public UUID    getAddresseeId()  { return addresseeId; }
    public String  getStatus()       { return status; }
    public long    getVersion()      { return version; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    // ── Guards ────────────────────────────────────────────────────────────────

    private void guardPending() {
        if (!"PENDING".equals(status))
            throw new DomainException(FriendshipErrorCode.FRIEND_REQUEST_ALREADY_RESOLVED);
    }
}
