-- V116: Refactor badge master table to be the single source of UI metadata.
--
-- Before this migration the `badge` table existed but had no FK relationship
-- with `user_badge`, making it an orphaned structure. This migration:
--   1. Drops and re-creates `badge` with `name` (the enum string) as its natural PK.
--   2. Seeds all 8 badge definitions with display_name and placeholder icon URLs.
--   3. Enforces referential integrity by adding a FK from user_badge.badge_name → badge.name.
--
-- The seed rows must be inserted before the FK is added so that any existing
-- user_badge rows (which already store valid enum-name strings) satisfy the constraint.

-- ── 1. Drop old orphaned table ─────────────────────────────────────────────────
DROP TABLE IF EXISTS public.badge;

-- ── 2. Create metadata-driven badge master table ───────────────────────────────
CREATE TABLE public.badge (
    name         VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description  TEXT,
    icon_url     VARCHAR,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT badge_pkey PRIMARY KEY (name)
);

-- ── 3. Seed all 8 badge definitions ───────────────────────────────────────────
INSERT INTO public.badge (name, display_name, description, icon_url) VALUES
    ('FIRST_WALK',
     'First Walk',
     'Awarded for completing your very first walk session.',
     'https://cdn.walkmate.app/badges/first_walk.png'),

    ('FIRST_FIVE',
     'First Five',
     'Awarded for completing 5 walk sessions.',
     'https://cdn.walkmate.app/badges/first_five.png'),

    ('CENTURY_STEPS',
     'Century Steps',
     'Awarded for completing 10 walk sessions.',
     'https://cdn.walkmate.app/badges/century_steps.png'),

    ('FIRST_KM',
     'First Km',
     'Awarded for walking a cumulative distance of 1 km.',
     'https://cdn.walkmate.app/badges/first_km.png'),

    ('TEN_KM_WALKER',
     'Ten Km Walker',
     'Awarded for walking a cumulative distance of 10 km.',
     'https://cdn.walkmate.app/badges/ten_km_walker.png'),

    ('FIFTY_KM_WALKER',
     'Fifty Km Walker',
     'Awarded for walking a cumulative distance of 50 km.',
     'https://cdn.walkmate.app/badges/fifty_km_walker.png'),

    ('TRUSTED_WALKER',
     'Trusted Walker',
     'Awarded when your trust score reaches 100.',
     'https://cdn.walkmate.app/badges/trusted_walker.png'),

    ('HIGHLY_TRUSTED',
     'Highly Trusted',
     'Awarded when your trust score reaches 500.',
     'https://cdn.walkmate.app/badges/highly_trusted.png');

-- ── 4. Enforce referential integrity ──────────────────────────────────────────
ALTER TABLE public.user_badge
    ADD CONSTRAINT user_badge_badge_name_fkey
        FOREIGN KEY (badge_name) REFERENCES public.badge (name);
