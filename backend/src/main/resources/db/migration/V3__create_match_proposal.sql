-- ============================================================
-- V3: Create match_proposal table
--
-- A match_proposal is created when the system finds two compatible
-- WalkIntents. Both users must accept before a walk_session begins.
-- If either rejects (or expires_at passes), the proposal closes
-- and the system tries the next ranked candidate.
-- ============================================================

CREATE TYPE proposal_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED');

CREATE TABLE match_proposal (
    proposal_id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id_a             UUID            NOT NULL REFERENCES walk_intent(intent_id),
    intent_id_b             UUID            NOT NULL REFERENCES walk_intent(intent_id),
    proposed_start_time     TIMESTAMP       NOT NULL,
    proposed_end_time       TIMESTAMP       NOT NULL,
    proposed_location_lat   DOUBLE PRECISION NOT NULL,
    proposed_location_lng   DOUBLE PRECISION NOT NULL,
    accepted_by_a           BOOLEAN         NOT NULL DEFAULT FALSE,
    accepted_by_b           BOOLEAN         NOT NULL DEFAULT FALSE,
    status                  proposal_status NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMP       NOT NULL,
    confirmed_at            TIMESTAMP
);

CREATE INDEX idx_match_proposal_intent_a ON match_proposal (intent_id_a);
CREATE INDEX idx_match_proposal_intent_b ON match_proposal (intent_id_b);
CREATE INDEX idx_match_proposal_status   ON match_proposal (status);
