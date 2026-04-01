-- ============================================================
-- V13: Create walk_review table.
-- Each participant may submit one review per completed session.
-- The unique constraint on (session_id, reviewer_id) enforces
-- the "no duplicate review" invariant at the database level.
-- ============================================================

CREATE TABLE IF NOT EXISTS walk_review (
    review_id    UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id   UUID      NOT NULL REFERENCES walk_session(session_id) ON DELETE CASCADE,
    reviewer_id  UUID      NOT NULL REFERENCES user_account(user_id),
    reviewee_id  UUID      NOT NULL REFERENCES user_account(user_id),
    rating_stars SMALLINT  NOT NULL CHECK (rating_stars BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS idx_walk_review_reviewee ON walk_review (reviewee_id);
CREATE INDEX IF NOT EXISTS idx_walk_review_session  ON walk_review (session_id);
