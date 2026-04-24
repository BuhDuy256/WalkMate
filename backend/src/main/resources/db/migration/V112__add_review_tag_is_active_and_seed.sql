-- ==============================================================================
-- V112: Add is_active column to review_tag_master + seed canonical tag vocabulary
--
-- Rationale:
--   V105 created review_tag_master without the is_active flag needed by the
--   ReviewTagJdbcRepository.findAllActive() query. This migration adds the column
--   and inserts the six canonical tags used by the structured-feedback chip group.
-- ==============================================================================

-- 1. Add is_active column (idempotent — skipped if the column already exists)
ALTER TABLE public.review_tag_master
    ADD COLUMN IF NOT EXISTS is_active boolean NOT NULL DEFAULT true;

-- 2. Seed the canonical tag vocabulary (ON CONFLICT ensures idempotency)
INSERT INTO public.review_tag_master (tag_name, tag_type) VALUES
    ('Enjoyable chat',       'POSITIVE_INTEREST'),
    ('Matched walking pace', 'POSITIVE_BEHAVIOR'),
    ('Punctual',             'POSITIVE_BEHAVIOR'),
    ('Different interests',  'NEGATIVE_INTEREST'),
    ('Pace too fast/slow',   'NEGATIVE_BEHAVIOR'),
    ('Arrived late',         'NEGATIVE_BEHAVIOR')
ON CONFLICT (tag_name) DO NOTHING;
