-- ============================================================
-- V6: Apply Phase 0 canonical IntentStatus: MATCHED → CONSUMED.
-- PostgreSQL enums are append-only — MATCHED is left in the type
-- but will no longer be used by application code.
-- ============================================================

ALTER TYPE intent_status ADD VALUE IF NOT EXISTS 'CONSUMED';

-- Migrate any existing rows (safe no-op on empty dev databases)
UPDATE walk_intent
SET status = 'CONSUMED'
WHERE status::text = 'MATCHED';

DO $$
BEGIN
    ALTER TYPE intent_status ADD VALUE 'CONSUMED';
EXCEPTION
    WHEN duplicate_object THEN
        RAISE NOTICE 'enum label CONSUMED already exists, skipping';
END $$;