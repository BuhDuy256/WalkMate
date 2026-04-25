-- ============================================================
-- V113: Normalize profile tags to a master lookup table (SSOT)
-- ============================================================

-- 1. Master table: single source of truth for all valid profile tags
CREATE TABLE IF NOT EXISTS public.profile_tag_master (
    tag_id   uuid         NOT NULL DEFAULT gen_random_uuid(),
    tag_name varchar(100) NOT NULL,
    CONSTRAINT profile_tag_master_pkey        PRIMARY KEY (tag_id),
    CONSTRAINT profile_tag_master_name_unique UNIQUE (tag_name)
);

-- 2. Seed master tags (exact names from the onboarding UI)
INSERT INTO public.profile_tag_master (tag_name) VALUES
    ('Strolling'),
    ('Power Walking'),
    ('Talkative'),
    ('Scenic & Quiet'),
    ('Pet Friendly')
ON CONFLICT (tag_name) DO NOTHING;

-- 3. Junction table: N-N between user_profile and profile_tag_master
CREATE TABLE IF NOT EXISTS public.user_profile_tag_map (
    user_id uuid NOT NULL,
    tag_id  uuid NOT NULL,
    CONSTRAINT user_profile_tag_map_pkey      PRIMARY KEY (user_id, tag_id),
    CONSTRAINT user_profile_tag_map_user_fkey FOREIGN KEY (user_id)
        REFERENCES public.user_profile (user_id) ON DELETE CASCADE,
    CONSTRAINT user_profile_tag_map_tag_fkey  FOREIGN KEY (tag_id)
        REFERENCES public.profile_tag_master (tag_id) ON DELETE CASCADE
);

-- 4. Migrate existing profile_tag rows into the junction table (best-effort name match)
INSERT INTO public.user_profile_tag_map (user_id, tag_id)
SELECT pt.user_id, ptm.tag_id
FROM   public.profile_tag pt
JOIN   public.profile_tag_master ptm
       ON lower(trim(pt.tag_name)) = lower(trim(ptm.tag_name))
ON CONFLICT DO NOTHING;

-- 5. Drop the old flat table (its index is dropped automatically)
DROP TABLE IF EXISTS public.profile_tag CASCADE;

-- 6. Index for fast per-user tag lookups
CREATE INDEX idx_user_profile_tag_map_user_id
    ON public.user_profile_tag_map (user_id);
