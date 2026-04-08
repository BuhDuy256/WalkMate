-- ============================================================
-- V107 — GPS chunk per-user tracking + report uniqueness
-- Closes: G-1 (session_point_chunks), G-8 (session_report)
-- ============================================================

-- ── Step 0.1 — Add user_id to session_point_chunks (G-1) ─────
-- Step 1: Add the column (nullable first to allow the ALTER on non-empty tables).
ALTER TABLE public.session_point_chunks
    ADD COLUMN user_id uuid;

-- Step 2: Enforce NOT NULL (dev branch has no live rows; prod must backfill before this runs).
ALTER TABLE public.session_point_chunks
    ALTER COLUMN user_id SET NOT NULL;

-- Step 3: Drop old session-scoped unique constraint.
ALTER TABLE public.session_point_chunks
    DROP CONSTRAINT session_point_chunks_unique;

-- Step 4: Add per-user unique constraint (session_id, user_id, chunk_index).
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_unique
        UNIQUE (session_id, user_id, chunk_index);

-- Step 5: Replace the covering index with a user-scoped variant.
DROP INDEX IF EXISTS public.idx_chunks_session_order;
CREATE INDEX idx_chunks_session_user_order
    ON public.session_point_chunks (session_id, user_id, chunk_index ASC);

-- ── Step 0.2 — Add unique constraint to session_report (G-8) ─
-- Mirrors the walk_review pattern: one report per (session, reporter).
ALTER TABLE public.session_report
    ADD CONSTRAINT session_report_unique UNIQUE (session_id, reporter_id);
