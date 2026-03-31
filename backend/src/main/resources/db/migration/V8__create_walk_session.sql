-- ============================================================
-- V8: Create walk_session table.
-- A session is created when both users accept a MatchProposal.
-- Phase 0 canonical SessionStatus: PENDING, ACTIVE, COMPLETED,
-- NO_SHOW, CANCELLED, ABORTED.
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'walk_session_status') THEN
        CREATE TYPE walk_session_status AS ENUM (
            'PENDING',
            'ACTIVE',
            'COMPLETED',
            'NO_SHOW',
            'CANCELLED',
            'ABORTED'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS walk_session (
    session_id          UUID                 PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id         UUID                 NOT NULL REFERENCES match_proposal(proposal_id),
    user_id_a           UUID                 NOT NULL REFERENCES user_account(user_id),
    user_id_b           UUID                 NOT NULL REFERENCES user_account(user_id),
    meeting_point_lat   DOUBLE PRECISION     NOT NULL,
    meeting_point_lng   DOUBLE PRECISION     NOT NULL,
    scheduled_start     TIMESTAMP            NOT NULL,
    scheduled_end       TIMESTAMP            NOT NULL,
    status              walk_session_status  NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP            NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMP,
    ended_at            TIMESTAMP
);

-- Each confirmed proposal produces exactly one session
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'walk_session'
          AND column_name = 'proposal_id'
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS idx_walk_session_proposal ON walk_session (proposal_id)';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'walk_session'
          AND column_name = 'user_id_a'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'walk_session'
          AND column_name = 'status'
    ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_walk_session_user_a ON walk_session (user_id_a, status)';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'walk_session'
          AND column_name = 'user_id_b'
    )
    AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'walk_session'
          AND column_name = 'status'
    ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_walk_session_user_b ON walk_session (user_id_b, status)';
    END IF;
END $$;
