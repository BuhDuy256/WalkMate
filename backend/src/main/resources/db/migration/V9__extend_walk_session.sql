-- ============================================================
-- V9: Extend walk_session with Phase 5 lifecycle fields.
--   - user_a_activated_at / user_b_activated_at : per-user arrival timestamps
--   - cancellation_reason / cancelled_by        : cancellation metadata
--   - abort_reason                               : mid-walk abort reason
--   - version                                    : optimistic lock counter
-- ============================================================

ALTER TABLE walk_session
    ADD COLUMN IF NOT EXISTS user_a_activated_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS user_b_activated_at  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancellation_reason  TEXT,
    ADD COLUMN IF NOT EXISTS cancelled_by         UUID REFERENCES user_account(user_id),
    ADD COLUMN IF NOT EXISTS abort_reason         TEXT,
    ADD COLUMN IF NOT EXISTS version              BIGINT NOT NULL DEFAULT 0;
