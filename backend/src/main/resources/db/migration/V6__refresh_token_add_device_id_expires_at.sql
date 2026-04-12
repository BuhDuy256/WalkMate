-- ============================================================
-- V6: refresh_token — Step 1 of 2-step migration (Risk 1)
--
-- Adds device_id and expires_at as NULLABLE columns first.
-- After Phase 3 code is deployed (all new logins write these fields),
-- run V7 to TRUNCATE old rows and promote columns to NOT NULL.
-- ============================================================

ALTER TABLE public.refresh_token
    ADD COLUMN IF NOT EXISTS device_id  VARCHAR NULL;

ALTER TABLE public.refresh_token
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NULL;
