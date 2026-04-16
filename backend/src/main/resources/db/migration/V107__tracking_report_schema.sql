-- ============================================================
-- V107 — GPS chunk per-user tracking + report uniqueness
-- Closes: G-1 (session_point_chunks), G-8 (session_report)
-- ============================================================

-- ── Step 0.1 — Add user_id to session_point_chunks (G-1) ─────

-- Step 1: Add the column as nullable first (required for non-empty tables).
ALTER TABLE public.session_point_chunks
    ADD COLUMN user_id uuid;

-- Step 2: Delete any pre-existing rows that predate the user_id column.
--         On a fresh dev DB this is a no-op. On a production deployment these
--         rows have no associated user and cannot be reconstructed; they must
--         be discarded before the NOT NULL constraint can be applied.
DELETE FROM public.session_point_chunks WHERE user_id IS NULL;

-- Step 3: Enforce NOT NULL now that all rows have a valid user_id.
ALTER TABLE public.session_point_chunks
    ALTER COLUMN user_id SET NOT NULL;

-- Step 4: Add FK constraint to user_account.
--         ON DELETE CASCADE: GPS chunks are disposable trace data; they
--         follow the user lifetime (in practice a user cannot be deleted while
--         walk_session rows exist due to the RESTRICT on walk_session.user_id_a/b).
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE;

-- Step 5: Drop old session-scoped unique constraint.
ALTER TABLE public.session_point_chunks
    DROP CONSTRAINT session_point_chunks_unique;

-- Step 6: Add per-user unique constraint (session_id, user_id, chunk_index).
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_unique
        UNIQUE (session_id, user_id, chunk_index);

-- Step 7: Replace the covering index with a user-scoped variant.
DROP INDEX IF EXISTS public.idx_chunks_session_order;
CREATE INDEX idx_chunks_session_user_order
    ON public.session_point_chunks (session_id, user_id, chunk_index ASC);

-- ── Step 0.2 — Add unique constraint to session_report (G-8) ─
-- Mirrors the walk_review pattern: one report per (session, reporter).
ALTER TABLE public.session_report
    ADD CONSTRAINT session_report_unique UNIQUE (session_id, reporter_id);
