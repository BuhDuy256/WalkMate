-- R2: Idempotent GPS sync — add deduplication key to session_point_chunks.
--
-- Each Android push attempt generates a fresh UUID (sync_request_id) that is
-- included in the POST /api/v1/tracking/sync request body.  The backend stores
-- it here and the UNIQUE constraint ensures a re-delivered (or double-pushed)
-- batch is rejected at the DB level, allowing the application layer to return
-- HTTP 200 idempotently rather than persisting duplicate chunk data.
--
-- The column is nullable so existing rows (inserted before this migration) are
-- unaffected.  All new inserts from the updated Android client will always
-- supply a non-null value.

ALTER TABLE session_point_chunks
    ADD COLUMN sync_request_id UUID;

ALTER TABLE session_point_chunks
    ADD CONSTRAINT uq_chunk_sync_request_id UNIQUE (sync_request_id);
