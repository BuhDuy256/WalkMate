-- ============================================================
-- V126 — GPS v2: persistent streams, per-point sequence numbers
--         and server-enforced no-overlap guarantee
--
-- Implements the sync contract upgrade (design:
-- buhduy/implementation_plan_gps_walkmate.md, §4.4):
--
--   * One GPS stream per (walk session, user).  The mobile client
--     mints a UUID per stream; the server stores it here and uses
--     the row as the lock target for every batch write and
--     arrival-cursor allocation.
--   * Every GPS point carries a monotonically increasing sequence
--     number assigned by the device (starting at 1).  Chunks store
--     the inclusive range [seq_start, seq_end] of their points so
--     out-of-order batch arrival can be reordered at read time and
--     the database can reject two chunks whose ranges overlap
--     within one stream.
--   * chunk_index stays the *arrival* cursor for the live
--     partner-poll API; for v2 it is allocated from the stream's
--     next_arrival_index counter inside the stream write
--     transaction instead of MAX(chunk_index)+1.
--   * Legacy chunks (stream_id IS NULL) keep working untouched;
--     every v2 constraint below is conditional on stream_id.
--
-- The no-overlap guarantee uses a GiST EXCLUDE constraint, which
-- requires the btree_gist extension (standard PostgreSQL contrib;
-- verified available in the pgvector/pgvector:pg16 test image).
-- ============================================================

-- ── 1. btree_gist (required for the GiST EXCLUDE constraint) ──
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ── 2. GPS stream: one per (session, user) ─────────────────────
-- sync_state lifecycle:
--   OPEN          — stream open, batches may be ingested
--   SEALED        — client declared final_sequence; late batches
--                    accepted until upload_deadline_at
--   COMPLETE      — full coverage [1, expected_final_sequence]
--                    satisfied (read-only)
--   EXPIRED       — upload deadline elapsed without full coverage
CREATE TABLE public.tracking_streams (
    stream_id               UUID         NOT NULL DEFAULT uuid_generate_v4(),
    session_id              UUID         NOT NULL,
    user_id                 UUID         NOT NULL,
    expected_final_sequence BIGINT,
    recorded_ended_at       TIMESTAMPTZ,
    upload_deadline_at      TIMESTAMPTZ,
    sync_state              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    next_arrival_index      INTEGER      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT tracking_streams_pkey
        PRIMARY KEY (stream_id),
    CONSTRAINT tracking_streams_unique
        UNIQUE (session_id, user_id),
    CONSTRAINT tracking_streams_session_id_fkey
        FOREIGN KEY (session_id)
        REFERENCES public.walk_session (session_id)
        ON DELETE CASCADE,
    CONSTRAINT tracking_streams_user_id_fkey
        FOREIGN KEY (user_id)
        REFERENCES public.user_account (user_id)
        ON DELETE CASCADE,
    CONSTRAINT tracking_streams_sync_state_chk
        CHECK (sync_state IN ('OPEN', 'SEALED', 'COMPLETE', 'EXPIRED')),
    CONSTRAINT tracking_streams_final_seq_chk
        CHECK (expected_final_sequence IS NULL
               OR expected_final_sequence >= 0)
);

CREATE INDEX idx_tracking_streams_session
    ON public.tracking_streams (session_id);

-- ── 3. Chunk columns: stream linkage + sequence range + hash ──
-- All new columns are nullable so pre-existing rows are unaffected
-- (legacy chunks keep stream_id NULL and keep working in the
-- existing read paths).
ALTER TABLE public.session_point_chunks
    ADD COLUMN stream_id    UUID,
    ADD COLUMN seq_start    BIGINT,
    ADD COLUMN seq_end      BIGINT,
    ADD COLUMN payload_hash CHAR(64),
    ADD COLUMN received_at  TIMESTAMPTZ NOT NULL DEFAULT now();

-- Sequence-range integrity (v2 chunks must be self-consistent).
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_seq_range_chk
        CHECK (seq_start IS NULL
               OR (seq_start >= 1
                   AND seq_end >= seq_start
                   AND (seq_end - seq_start + 1)::bigint = point_count));

ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_stream_id_fkey
        FOREIGN KEY (stream_id)
        REFERENCES public.tracking_streams (stream_id)
        ON DELETE CASCADE;

-- Hashed streams must carry a hash (bytea of SHA-256 computed by the
-- server over the canonical payload encoding).
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT session_point_chunks_v2_hash_chk
        CHECK (stream_id IS NULL OR payload_hash IS NOT NULL);

-- ── 4. Lookup indexes for the v2 write path ────────────────────
-- sync_request_id: batch replay/verification lookup.
CREATE INDEX idx_chunks_sync_request_id
    ON public.session_point_chunks (sync_request_id);

-- History read path: v2 polylines ordered by route sequence.
CREATE INDEX idx_chunks_v2_route_order
    ON public.session_point_chunks (session_id, user_id, seq_start)
    WHERE stream_id IS NOT NULL;

-- Chunk-to-stream verification query.
CREATE INDEX idx_chunks_stream_lookup
    ON public.session_point_chunks (stream_id);

-- ── 5. No two overlapping sequence ranges in the same stream ───
-- This is the DB-level enforcement of the v2 invariant that a
-- (stream, sequence) pair is assigned at most once.  Rejects both
-- concurrent racing batches and sloppy client logic.  Legacy rows
-- (stream_id NULL) are excluded from the constraint.
ALTER TABLE public.session_point_chunks
    ADD CONSTRAINT gps_v2_no_seq_overlap
    EXCLUDE USING gist (
        stream_id        WITH =,
        int8range(seq_start, seq_end, '[]') WITH &&
    )
    WHERE (stream_id IS NOT NULL);
