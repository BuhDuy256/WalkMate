-- ============================================================
-- V1: Create hotspot table
-- Hotspot is a WalkMate concept (named walking locations like parks).
-- active_walker_count is NOT stored here — it is computed at
-- query time by counting OPEN walk_intent rows per hotspot.
-- ============================================================

CREATE TABLE hotspot (
    id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(255) NOT NULL,
    lat     DOUBLE PRECISION NOT NULL,
    lng     DOUBLE PRECISION NOT NULL
);
