-- Phase 8: Follow & Block social relations
-- These are directional, binary relationships between two user_account rows.
--
-- Design notes:
--   * PRIMARY KEY enforces uniqueness with zero extra index overhead.
--   * CHECK constraints prevent self-follow / self-block at the DB level.
--   * Separate indexes on the "target" column make bidirectional lookups fast:
--       - "who follows user X?" → idx_follow_followee
--       - "who has user X blocked OR who blocked user X?" → idx_block_blocked
--   * block_relation: when the matching engine calls getBlockedAndBlockerIds(),
--     a single UNION query reads both indexes and returns the full exclusion set
--     in one round-trip (see OPTIMIZATION_DECISION_LOG.md).

CREATE TABLE IF NOT EXISTS follow_relation (
    follower_id   UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    followee_id   UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    followed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> followee_id)
);

CREATE INDEX IF NOT EXISTS idx_follow_follower ON follow_relation (follower_id);
CREATE INDEX IF NOT EXISTS idx_follow_followee ON follow_relation (followee_id);

CREATE TABLE IF NOT EXISTS block_relation (
    blocker_id  UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    blocked_id  UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    blocked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT chk_no_self_block CHECK (blocker_id <> blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_block_blocker ON block_relation (blocker_id);
CREATE INDEX IF NOT EXISTS idx_block_blocked ON block_relation (blocked_id);
