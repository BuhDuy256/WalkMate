-- ============================================================
-- V7: refresh_token — Step 2 of 2-step migration (Risk 1)
--
-- Adds the revoked flag used for reuse detection.
-- Truncates any old rows (pre-Phase 2) that have NULL device_id /
-- expires_at, then promotes those columns to NOT NULL as planned.
-- ============================================================

-- 1. Drop stale rows that predate Phase 3 (no client holds them — LoginResult
--    never returned a refresh token to clients before Phase 3).
DELETE FROM public.refresh_token WHERE device_id IS NULL OR expires_at IS NULL;

-- 2. Promote device_id and expires_at to NOT NULL (completing the Risk 1 plan).
ALTER TABLE public.refresh_token
    ALTER COLUMN device_id  SET NOT NULL,
    ALTER COLUMN expires_at SET NOT NULL;

-- 3. Add revoked flag for rotation-based reuse detection.
ALTER TABLE public.refresh_token
    ADD COLUMN IF NOT EXISTS revoked BOOLEAN NOT NULL DEFAULT false;

-- 4. Unique constraint: one active token per (user, device).
--    Partial index so it only enforces uniqueness for non-revoked tokens.
CREATE UNIQUE INDEX IF NOT EXISTS uidx_refresh_token_user_device_active
    ON public.refresh_token (user_id, device_id)
    WHERE revoked = false;
