-- ==============================================================================
-- V104: Review Tag Master table + walk_review_tag_map junction + seed data
--
-- Rationale:
--   Replaces unstructured free-text comments with structured feedback via a
--   pre-defined tag vocabulary. Tags are split by sentiment prefix so the client
--   can show positive tags for high ratings and negative tags for low ratings.
--   The walk_review_tag_map junction table is the clean fuel for the future
--   AI Matching Preference Model.
-- ==============================================================================

-- 1. Master tag lookup table
CREATE TABLE public.review_tag_master (
    tag_id    uuid    NOT NULL DEFAULT uuid_generate_v4(),
    tag_name  varchar NOT NULL,
    tag_type  varchar NOT NULL,   -- POSITIVE_INTEREST | POSITIVE_BEHAVIOR | NEGATIVE_INTEREST | NEGATIVE_BEHAVIOR
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT review_tag_master_pkey     PRIMARY KEY (tag_id),
    CONSTRAINT review_tag_master_name_uq  UNIQUE (tag_name)
);

-- 2. Junction table — many-to-many between a submitted review and its selected tags
CREATE TABLE public.walk_review_tag_map (
    review_id uuid NOT NULL,
    tag_id    uuid NOT NULL,
    CONSTRAINT walk_review_tag_map_pkey PRIMARY KEY (review_id, tag_id),
    CONSTRAINT walk_review_tag_map_review_fkey
        FOREIGN KEY (review_id) REFERENCES public.walk_review       (review_id) ON DELETE CASCADE,
    CONSTRAINT walk_review_tag_map_tag_fkey
        FOREIGN KEY (tag_id)    REFERENCES public.review_tag_master (tag_id)    ON DELETE CASCADE
);

-- 3. Seed the canonical tag vocabulary
INSERT INTO public.review_tag_master (tag_name, tag_type) VALUES
    ('Enjoyable chat',       'POSITIVE_INTEREST'),
    ('Matched walking pace', 'POSITIVE_BEHAVIOR'),
    ('Punctual',             'POSITIVE_BEHAVIOR'),
    ('Different interests',  'NEGATIVE_INTEREST'),
    ('Pace too fast/slow',   'NEGATIVE_BEHAVIOR'),
    ('Arrived late',         'NEGATIVE_BEHAVIOR');
