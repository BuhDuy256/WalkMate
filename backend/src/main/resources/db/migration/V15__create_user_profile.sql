-- V15: User Profile (Phase 7)
-- Adds a user_profile table (1:1 with user_account) and a profile_tag table.
-- Gender is stored as VARCHAR rather than a PG enum so it can be extended
-- without a Flyway migration per new value.

CREATE TABLE user_profile (
    user_id       UUID        PRIMARY KEY
                              REFERENCES user_account(user_id) ON DELETE CASCADE,
    full_name     VARCHAR(100) NOT NULL DEFAULT '',
    gender        VARCHAR(30),
    date_of_birth DATE        CHECK (
                                  date_of_birth IS NULL
                                  OR date_of_birth <= CURRENT_DATE - INTERVAL '13 years'
                              ),
    avatar_url    VARCHAR(500),
    bio           VARCHAR(500),
    search_radius INT         NOT NULL DEFAULT 5000
                              CHECK (search_radius > 0 AND search_radius <= 50000),
    updated_at    TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE profile_tag (
    user_id  UUID        NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    tag_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, tag_name)
);
