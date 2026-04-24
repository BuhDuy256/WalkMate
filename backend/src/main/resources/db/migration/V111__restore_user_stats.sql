-- ==============================================================================
-- V103: Restore user stats columns and fix trust score defaults
--
-- Rationale:
--   V102 dropped completed_sessions and total_distance_km from user_account and
--   moved total_distance_km to the trust_score satellite table. However, no Java
--   code was ever written to read/write that satellite table, causing silent data
--   loss on every session completion reward. This migration reverses that decision
--   and canonically stores all stats on user_account (the single source of truth
--   the application actually reads). The orphaned trust_score table is dropped.
-- ==============================================================================

-- 1. Drop the orphaned trust_score satellite table (never written to by app code)
DROP TABLE IF EXISTS public.trust_score;

-- 2. Restore completed_sessions to user_account
ALTER TABLE public.user_account
    ADD COLUMN IF NOT EXISTS completed_sessions INTEGER NOT NULL DEFAULT 0;

-- 3. Restore total_distance_km to user_account
ALTER TABLE public.user_account
    ADD COLUMN IF NOT EXISTS total_distance_km NUMERIC(10, 2) NOT NULL DEFAULT 0;

-- 4. Set the new default starting trust score to 500 for future registrations
ALTER TABLE public.user_account
    ALTER COLUMN trust_score SET DEFAULT 500;

-- 5. Migrate existing users: zero trust scores are legacy un-initialized values;
--    bump them to the new baseline so existing accounts aren't penalised.
UPDATE public.user_account
SET trust_score = 500
WHERE trust_score = 0;

-- 6. Add ANY to the gender enum (used for "flexible / prefer not to say" onboarding)
ALTER TYPE gender ADD VALUE IF NOT EXISTS 'ANY';
