-- ============================================================
-- V108 — Enforce NOT NULL + RESTRICT on session_report.session_id
-- ============================================================
-- Background:
--   V1 defined session_report.session_id as nullable with ON DELETE SET NULL.
--   This allowed reports to outlive their sessions (session_id silently set to
--   NULL on session deletion), but it created two problems:
--
--   1. The UNIQUE(session_id, reporter_id) constraint added in V107 is
--      ineffective for NULL session_ids — PostgreSQL treats each NULL as
--      distinct, so the one-report-per-session-per-reporter guarantee is
--      violated for any row where session_id is NULL.
--
--   2. Application code never creates a report without a session_id.
--      Nullable schema allowed an inconsistency that cannot occur in practice.
--
-- Fix:
--   - Delete any legacy NULL rows (should be zero on a fresh dev DB; see
--     deployment notes below for production handling).
--   - Drop the old FK (ON DELETE SET NULL).
--   - Enforce NOT NULL.
--   - Re-add FK with ON DELETE RESTRICT: a session with reports cannot be
--     deleted. This is correct — reports are moderation evidence and must
--     outlive the session lifecycle by being retained, not nullified.
--
-- ── Deployment notes / runbook (production) ────────────────────────────────
--
-- Step 1 — Audit NULL rows BEFORE deploying:
--   SELECT COUNT(*) FROM session_report WHERE session_id IS NULL;
--   If count > 0: escalate to product/tech lead — these rows represent reports
--   whose session was deleted. Decide: archive them, reassign them, or delete
--   them. Do NOT proceed with migration until this decision is made.
--
-- Step 2 — If count = 0 (expected): migration runs automatically at startup.
--
-- Step 3 — Verify after migration:
--   \d session_report   -- should show session_id NOT NULL
--                       -- and session_report_session_id_fkey with RESTRICT
-- ──────────────────────────────────────────────────────────────────────────

-- Step 1: Remove any NULL session_id rows (safe no-op on clean DB;
--         on production, run the audit above first).
DELETE FROM public.session_report WHERE session_id IS NULL;

-- Step 2: Drop the FK that carried ON DELETE SET NULL behaviour.
ALTER TABLE public.session_report
    DROP CONSTRAINT IF EXISTS session_report_session_id_fkey;

-- Step 3: Enforce NOT NULL now that all rows have a session_id.
ALTER TABLE public.session_report
    ALTER COLUMN session_id SET NOT NULL;

-- Step 4: Re-add FK with ON DELETE RESTRICT.
--         A session with associated reports cannot be hard-deleted;
--         the application must archive or resolve reports first.
ALTER TABLE public.session_report
    ADD CONSTRAINT session_report_session_id_fkey
        FOREIGN KEY (session_id) REFERENCES public.walk_session (session_id) ON DELETE RESTRICT;
