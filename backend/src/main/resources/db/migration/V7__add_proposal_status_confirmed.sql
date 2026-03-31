-- ============================================================
-- V7: Apply Phase 0 canonical ProposalStatus: ACCEPTED → CONFIRMED.
-- Also adds a partial unique index to prevent duplicate PENDING proposals
-- for the same pair of intents (concurrency guard for the matching engine).
-- ============================================================

-- Additive enum change (ACCEPTED is kept but will never be written again)
ALTER TYPE proposal_status ADD VALUE IF NOT EXISTS 'CONFIRMED';

-- Migrate any legacy ACCEPTED rows
UPDATE match_proposal
SET status = 'CONFIRMED'
WHERE status::text = 'ACCEPTED';

-- Prevent two simultaneous findMatch calls from creating duplicate PENDING
-- proposals for the same pair. Uses LEAST/GREATEST to normalise A/B order.
CREATE UNIQUE INDEX IF NOT EXISTS idx_match_proposal_unique_pending_pair
    ON match_proposal (
        LEAST(intent_id_a::text,    intent_id_b::text),
        GREATEST(intent_id_a::text, intent_id_b::text)
    )
    WHERE status = 'PENDING';
