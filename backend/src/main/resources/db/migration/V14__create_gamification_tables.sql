-- ============================================================
-- V14: Gamification — points, distance, sessions counters on
--      user_account, plus the user_badge awards table.
-- ============================================================

-- Denormalized counters on the user profile (updated at session completion)
ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS total_points       INT            NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_distance_km  DECIMAL(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS completed_sessions INT            NOT NULL DEFAULT 0;

-- Badge awards table
-- Unique on (user_id, badge_name): a badge is awarded exactly once per user.
CREATE TABLE IF NOT EXISTS user_badge (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    badge_name  VARCHAR(64)  NOT NULL,
    awarded_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, badge_name)
);

CREATE INDEX IF NOT EXISTS idx_user_badge_user_id ON user_badge (user_id);
