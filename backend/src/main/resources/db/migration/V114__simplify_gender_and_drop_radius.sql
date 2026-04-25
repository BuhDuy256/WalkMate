-- V114: Drop search_radius and shrink gender ENUM to MALE | FEMALE only.

-- 1. Drop search_radius column (no longer used in matching).
ALTER TABLE user_profile DROP COLUMN IF EXISTS search_radius;

-- 2. Safely rebuild the gender ENUM.
--    PostgreSQL does not allow removing values from an existing enum, so we:
--    a) rename the old type,
--    b) create the new narrow type,
--    c) convert the column through VARCHAR,
--    d) null-out any rows whose value is no longer valid,
--    e) cast back to the new enum,
--    f) drop the old type.

ALTER TYPE gender RENAME TO gender_old;

CREATE TYPE gender AS ENUM ('MALE', 'FEMALE');

ALTER TABLE user_profile
    ALTER COLUMN gender TYPE VARCHAR
    USING gender::VARCHAR;

UPDATE user_profile
    SET gender = NULL
    WHERE gender NOT IN ('MALE', 'FEMALE');

ALTER TABLE user_profile
    ALTER COLUMN gender TYPE gender
    USING gender::gender;

DROP TYPE gender_old;
