-- Migration: Add optimistic locking version to walk_session
-- Date: 2026-03-14
-- Purpose:
--   Add version column for optimistic locking on concurrent session updates.

BEGIN;

ALTER TABLE walk_session
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'valid_walk_session_version'
          AND conrelid = 'walk_session'::regclass
    ) THEN
        ALTER TABLE walk_session
            ADD CONSTRAINT valid_walk_session_version CHECK (version >= 0);
    END IF;
END
$$;

COMMIT;
