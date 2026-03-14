-- Migration: Add optimistic locking version to walk_intent
-- Date: 2026-03-14
-- Purpose:
--   Add version column for optimistic locking on concurrent intent updates,
--   protecting Invariant I-7 (Expiry Lock Safety) against race conditions.

BEGIN;

ALTER TABLE walk_intent
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'valid_walk_intent_version'
          AND conrelid = 'walk_intent'::regclass
    ) THEN
        ALTER TABLE walk_intent
            ADD CONSTRAINT valid_walk_intent_version CHECK (version >= 0);
    END IF;
END
$$;

COMMIT;
