-- ============================================================
-- V1.2: Create refresh_token table.
-- Depends on user_account (V1.1).
-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_token (
    token_id    UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID      NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    token_value TEXT      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON refresh_token (user_id);
