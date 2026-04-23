-- ============================================================
-- V110: remove_aborted_status
--
-- Removes the ABORTED value from the walk_session_status enum
-- and drops the now-unused abort_reason and aborted_sessions
-- columns.
--
-- Two tables use walk_session_status:
--   • walk_session          (status, user_a_status, user_b_status)
--   • session_state_change_log (from_status, to_status)
--
-- PostgreSQL does not support ALTER TYPE … DROP VALUE directly,
-- so the workaround is:
--   1. Drop column DEFAULTs that reference the old type name.
--   2. Create a replacement enum without ABORTED.
--   3. Cast every affected column to the new type.
--   4. Drop the old type and rename the replacement.
--   5. Restore the column DEFAULTs using the new type name.
-- ============================================================

-- 1. Drop column defaults before the type swap
--    (session_state_change_log columns have no defaults)
ALTER TABLE public.walk_session
    ALTER COLUMN status        DROP DEFAULT,
    ALTER COLUMN user_a_status DROP DEFAULT,
    ALTER COLUMN user_b_status DROP DEFAULT;

-- 2. Replacement enum (identical to walk_session_status minus ABORTED)
CREATE TYPE walk_session_status_v2 AS ENUM (
    'PENDING', 'ACTIVE', 'COMPLETED', 'NO_SHOW', 'CANCELLED'
);

-- 3. Migrate walk_session status columns to the new type
ALTER TABLE public.walk_session
    ALTER COLUMN status
        TYPE walk_session_status_v2
        USING status::text::walk_session_status_v2,
    ALTER COLUMN user_a_status
        TYPE walk_session_status_v2
        USING user_a_status::text::walk_session_status_v2,
    ALTER COLUMN user_b_status
        TYPE walk_session_status_v2
        USING user_b_status::text::walk_session_status_v2;

-- 4. Migrate session_state_change_log status columns to the new type
ALTER TABLE public.session_state_change_log
    ALTER COLUMN from_status
        TYPE walk_session_status_v2
        USING from_status::text::walk_session_status_v2,
    ALTER COLUMN to_status
        TYPE walk_session_status_v2
        USING to_status::text::walk_session_status_v2;

-- 5. Restore the canonical type name
DROP TYPE public.walk_session_status;
ALTER TYPE public.walk_session_status_v2 RENAME TO walk_session_status;

-- 6. Restore column defaults with the now-renamed type
ALTER TABLE public.walk_session
    ALTER COLUMN status        SET DEFAULT 'PENDING'::walk_session_status,
    ALTER COLUMN user_a_status SET DEFAULT 'PENDING'::walk_session_status,
    ALTER COLUMN user_b_status SET DEFAULT 'PENDING'::walk_session_status;

-- 7. Drop abort_reason column (no longer written by any code path)
ALTER TABLE public.walk_session
    DROP COLUMN IF EXISTS abort_reason;

-- 8. Drop aborted_sessions counter from trust_score (metric no longer tracked)
ALTER TABLE public.trust_score
    DROP COLUMN IF EXISTS aborted_sessions;
