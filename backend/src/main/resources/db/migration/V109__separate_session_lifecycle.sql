-- ============================================================
-- V109 — Separate WalkSession lifecycle per participant
-- ============================================================
-- Problem: the shared global status blocks User A from starting
-- their walk until User B also arrives.
--
-- Solution: each participant now owns an independent status,
-- individual timing columns, and individual walk metrics.
-- The global status is DERIVED from the two participant statuses
-- by the domain layer (not stored redundantly here — the existing
-- `status` column is kept as the authoritative global value written
-- by WalkSession.deriveGlobalStatus()).
-- ============================================================

-- ── 1. Per-participant status columns ────────────────────────
ALTER TABLE public.walk_session
    ADD COLUMN user_a_status walk_session_status NOT NULL DEFAULT 'PENDING',
    ADD COLUMN user_b_status walk_session_status NOT NULL DEFAULT 'PENDING';

-- Backfill: rows already in a terminal/active global state
-- should reflect that in their per-user statuses.
UPDATE public.walk_session
    SET user_a_status = status,
        user_b_status = status
    WHERE status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'ABORTED', 'NO_SHOW');

-- ── 2. Per-participant end timestamps ────────────────────────
ALTER TABLE public.walk_session
    ADD COLUMN user_a_ended_at timestamp,
    ADD COLUMN user_b_ended_at timestamp;

-- Backfill: global ended_at carries over to both users for existing rows.
UPDATE public.walk_session
    SET user_a_ended_at = ended_at,
        user_b_ended_at = ended_at
    WHERE ended_at IS NOT NULL;

-- ── 3. Rename shared metrics to user-A-scoped columns ────────
-- User A inherits all previously recorded data; user B starts at 0.
ALTER TABLE public.walk_session
    RENAME COLUMN total_distance_km      TO user_a_distance_km;
ALTER TABLE public.walk_session
    RENAME COLUMN total_duration_seconds TO user_a_duration_seconds;

-- ── 4. Add user-B metric columns ─────────────────────────────
ALTER TABLE public.walk_session
    ADD COLUMN user_b_distance_km      numeric(10, 3) NOT NULL DEFAULT 0
        CHECK (user_b_distance_km >= 0),
    ADD COLUMN user_b_duration_seconds bigint         NOT NULL DEFAULT 0
        CHECK (user_b_duration_seconds >= 0);
