-- ============================================================
-- V11: Create session_point_chunks for GPS route storage.
--
-- Each row holds one batch of GPS points for an active session,
-- encoded as a Google Encoded Polyline string (compact, fast to
-- decode on the server side) plus a parallel BYTEA of epoch-ms
-- timestamps (8 bytes per point, big-endian).
--
-- chunk_index starts at 0 and increments atomically so batches
-- can be reassembled in order even when they arrive out of order.
-- ============================================================

CREATE TABLE IF NOT EXISTS session_point_chunks (
    id           BIGSERIAL  PRIMARY KEY,
    session_id   UUID       NOT NULL REFERENCES walk_session(session_id) ON DELETE CASCADE,
    chunk_index  INT        NOT NULL,
    polyline     TEXT       NOT NULL,
    timestamps   BYTEA      NOT NULL,
    point_count  INT        NOT NULL CHECK (point_count > 0),
    created_at   TIMESTAMP  NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_chunks_session_id ON session_point_chunks (session_id);
