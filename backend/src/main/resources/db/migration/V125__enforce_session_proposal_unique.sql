-- V125: Enforce at-most-one walk_session per proposal (I-04).
--
-- A proposal must produce at most one session. The previous non-unique index
-- idx_walk_session_proposal_id allowed duplicates under a concurrent double-accept,
-- which the application layer now prevents via per-user + row locking. This unique
-- index is the database-level backstop.

-- Preflight: fail loudly if duplicates already exist so they can be resolved first.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.walk_session
        GROUP BY proposal_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'walk_session contains duplicate proposal_id rows; resolve duplicates before applying V125';
    END IF;
END $$;

DROP INDEX IF EXISTS idx_walk_session_proposal_id;

CREATE UNIQUE INDEX uq_walk_session_proposal_id
    ON public.walk_session (proposal_id);
