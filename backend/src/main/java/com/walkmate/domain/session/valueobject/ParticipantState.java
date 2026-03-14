package com.walkmate.domain.session.valueobject;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class ParticipantState {
    private UUID userId;
    private Instant activatedAt;

    protected ParticipantState() {} // JPA requires protected no-args

    public ParticipantState(UUID userId) {
        this.userId = userId;
        this.activatedAt = null;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public boolean isActivated() {
        return activatedAt != null;
    }

    public void activate(Instant time) {
        this.activatedAt = time;
    }
}
