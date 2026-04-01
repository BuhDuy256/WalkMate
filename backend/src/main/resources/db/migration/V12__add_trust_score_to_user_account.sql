-- ============================================================
-- V12: Add trust_score to user_account.
-- Starts at 0 for all existing users; bounded [0, 1000] by
-- application policy (TrustScorePolicy).
-- ============================================================

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS trust_score INT NOT NULL DEFAULT 0;
