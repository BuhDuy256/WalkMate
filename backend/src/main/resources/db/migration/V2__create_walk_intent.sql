-- ============================================================
-- V2: Create walk_intent table
--
-- Aligned with the actual WalkMate schema with one adaptation:
-- we use hotspot_id (FK → hotspot) instead of location_lat/lng
-- directly, because the MVP selects a named hotspot from the map.
--
-- matching_constraints (JSONB) holds all soft/hard filter criteria
-- (age_min, age_max, gender_preference, etc.) in an extensible
-- format — no column changes needed when adding new filter types.
--
-- time_window_start / time_window_end are full TIMESTAMP values
-- so that intents can be scheduled for a specific date+time,
-- not just a time-of-day.
--
-- expires_at: intent automatically expires if unmatched.
-- version: optimistic-locking sentinel (increment on every update).
-- ============================================================

CREATE TYPE intent_status AS ENUM ('OPEN', 'MATCHED', 'CANCELLED', 'EXPIRED');

CREATE TABLE walk_intent (
    intent_id           UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    hotspot_id          UUID            NOT NULL REFERENCES hotspot(id),
    user_id             UUID            NOT NULL REFERENCES user_account(user_id),
    time_window_start   TIMESTAMP       NOT NULL,
    time_window_end     TIMESTAMP       NOT NULL,
    matching_constraints JSONB          NOT NULL DEFAULT '{}',
    status              intent_status   NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP       NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0 CHECK (version >= 0),

    CONSTRAINT walk_intent_time_valid CHECK (time_window_end > time_window_start)
);

-- Primary lookup for the candidate-search query
CREATE INDEX idx_walk_intent_hotspot_status
    ON walk_intent (hotspot_id, status);

-- Ownership and cancellation lookups
CREATE INDEX idx_walk_intent_user_id
    ON walk_intent (user_id);

-- GIN index for JSONB constraint queries
CREATE INDEX idx_walk_intent_constraints
    ON walk_intent USING GIN (matching_constraints);
