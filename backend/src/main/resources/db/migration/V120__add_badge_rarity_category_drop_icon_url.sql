-- V120: Add rarity + category to badge master table, drop icon_url.
--
-- icon_url is removed permanently (badges are text/colour-only in the UI).
-- rarity and category drive visual styling and grouping on the badge screen.

ALTER TABLE public.badge
    ADD COLUMN rarity   VARCHAR(20),
    ADD COLUMN category VARCHAR(50);

UPDATE public.badge SET rarity = 'common',    category = 'Walk Milestones'    WHERE name = 'FIRST_WALK';
UPDATE public.badge SET rarity = 'common',    category = 'Walk Milestones'    WHERE name = 'FIRST_FIVE';
UPDATE public.badge SET rarity = 'rare',      category = 'Walk Milestones'    WHERE name = 'CENTURY_STEPS';
UPDATE public.badge SET rarity = 'common',    category = 'Distance Covered'   WHERE name = 'FIRST_KM';
UPDATE public.badge SET rarity = 'rare',      category = 'Distance Covered'   WHERE name = 'TEN_KM_WALKER';
UPDATE public.badge SET rarity = 'epic',      category = 'Distance Covered'   WHERE name = 'FIFTY_KM_WALKER';
UPDATE public.badge SET rarity = 'common',    category = 'Trust & Community'  WHERE name = 'TRUSTED_WALKER';
UPDATE public.badge SET rarity = 'rare',      category = 'Trust & Community'  WHERE name = 'HIGHLY_TRUSTED';

ALTER TABLE public.badge ALTER COLUMN rarity   SET NOT NULL;
ALTER TABLE public.badge ALTER COLUMN category SET NOT NULL;

ALTER TABLE public.badge DROP COLUMN IF EXISTS icon_url;
