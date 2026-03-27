-- =====================================================
-- Profile Flow Extension
-- =====================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'profile_mode') THEN
        CREATE TYPE profile_mode AS ENUM ('PUBLIC', 'PRIVATE');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'info_visibility_mode') THEN
        CREATE TYPE info_visibility_mode AS ENUM ('PUBLIC', 'PRIVATE');
    END IF;
END $$;

-- Extend existing profile schema
ALTER TABLE user_profile
    ADD COLUMN IF NOT EXISTS city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS profile_mode profile_mode NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS info_visibility info_visibility_mode NOT NULL DEFAULT 'PRIVATE';

-- Add tag values needed for Setup Profile screen
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tag_type') THEN
        -- Interests
        BEGIN
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'PET_WALKING';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'INDIE_MUSIC';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'PHOTOGRAPHY';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'NATURE_LOVER';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'COFFEE_WALKS';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'BOOK_CLUB';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'PODCAST_LISTENER';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'STREET_ART';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'FOODIE';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'YOGA_WELLNESS';

            -- Walk vibes
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'QUIET_WALK';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'CHATTY_SOCIAL';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'CHALLENGE_PACE';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'SLOW_SCENIC';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'CITY_EXPLORER';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'FOREST_TRAILS';

            -- Best time
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'MORNING_BIRD';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'MIDDAY_BREAK';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'GOLDEN_HOUR';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'NIGHT_OWL';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'WEEKENDS_ONLY';
            ALTER TYPE tag_type ADD VALUE IF NOT EXISTS 'FLEXIBLE';
        EXCEPTION
            WHEN duplicate_object THEN
                NULL;
        END;
    END IF;
END $$;
