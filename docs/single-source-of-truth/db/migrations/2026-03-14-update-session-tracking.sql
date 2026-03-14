-- Migration: Add session tracking summary + GPS route points
-- Date: 2026-03-14
-- Purpose:
--   1) Keep session summary metrics in walk_session
--   2) Keep GPS route points in a dedicated table session_points

BEGIN;

-- 1) Add summary columns to walk_session (non-destructive)
ALTER TABLE walk_session
    ADD COLUMN IF NOT EXISTS total_distance NUMERIC(10,3) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_duration BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'valid_total_distance'
          AND conrelid = 'walk_session'::regclass
    ) THEN
        ALTER TABLE walk_session
            ADD CONSTRAINT valid_total_distance CHECK (total_distance >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'valid_total_duration'
          AND conrelid = 'walk_session'::regclass
    ) THEN
        ALTER TABLE walk_session
            ADD CONSTRAINT valid_total_duration CHECK (total_duration >= 0);
    END IF;
END
$$;

-- 2) Create session_points table for route points
CREATE TABLE IF NOT EXISTS session_points (
    point_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES walk_session(session_id) ON DELETE CASCADE,
    point_order INTEGER NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    time BIGINT NOT NULL,
    location GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
    ) STORED,

    CONSTRAINT valid_session_point_order CHECK (point_order >= 0),
    CONSTRAINT valid_session_point_coords CHECK (
        latitude >= -90 AND latitude <= 90 AND
        longitude >= -180 AND longitude <= 180
    ),
    CONSTRAINT unique_session_point_order UNIQUE (session_id, point_order)
);

-- 3) Create trigger function to validate route point time window against session window
CREATE OR REPLACE FUNCTION check_session_points_time_window()
RETURNS TRIGGER AS $$
DECLARE
    v_start_time TIMESTAMP;
    v_end_time TIMESTAMP;
    v_point_time TIMESTAMPTZ;
BEGIN
    SELECT COALESCE(actual_start_time, scheduled_start_time),
           COALESCE(actual_end_time, scheduled_end_time)
    INTO v_start_time, v_end_time
    FROM walk_session
    WHERE session_id = NEW.session_id;

    IF v_start_time IS NULL OR v_end_time IS NULL THEN
        RAISE EXCEPTION 'Session % does not exist or has invalid time range.', NEW.session_id;
    END IF;

    v_point_time := to_timestamp(NEW.time);

    IF v_point_time < v_start_time OR v_point_time > v_end_time THEN
        RAISE EXCEPTION 'Route point time % is outside session window [% - %].', NEW.time, v_start_time, v_end_time;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_check_session_points_time_window ON session_points;

CREATE TRIGGER trg_check_session_points_time_window
BEFORE INSERT OR UPDATE ON session_points
FOR EACH ROW
EXECUTE FUNCTION check_session_points_time_window();

-- 4) Indexes for session_points
CREATE INDEX IF NOT EXISTS idx_session_points_session ON session_points(session_id);
CREATE INDEX IF NOT EXISTS idx_session_points_session_order ON session_points(session_id, point_order);
CREATE INDEX IF NOT EXISTS idx_session_points_time ON session_points(time);
CREATE INDEX IF NOT EXISTS idx_session_points_location ON session_points USING GIST(location);

COMMIT;
