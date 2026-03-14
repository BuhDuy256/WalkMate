-- Migration: Add abort_reason to walk_session
-- Date: 2026-03-14
-- Purpose:
--   Add abort reason for ABORTED session terminal state tracking.

BEGIN;

ALTER TABLE walk_session
    ADD COLUMN IF NOT EXISTS abort_reason VARCHAR(32);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'valid_walk_session_abort_reason'
          AND conrelid = 'walk_session'::regclass
    ) THEN
        ALTER TABLE walk_session
            ADD CONSTRAINT valid_walk_session_abort_reason CHECK (
                abort_reason IS NULL OR abort_reason IN ('INJURY', 'SAFETY', 'ENVIRONMENT', 'OTHER')
            );
    END IF;
END
$$;

COMMIT;
