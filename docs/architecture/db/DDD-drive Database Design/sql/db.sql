-- =====================================================
-- WalkMate Database Schema
-- DDD-Driven Design with Invariant Enforcement
-- Architecture: Scheduled Discovery (Intent-Intent Matching)
-- =====================================================

-- Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- =====================================================
-- IDENTITY & ACCESS CONTEXT
-- =====================================================

CREATE TYPE auth_provider AS ENUM ('GOOGLE', 'LOCAL', 'PHONE');
CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'DELETED');

CREATE TABLE user_account (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE,
    password_hash TEXT,
    provider auth_provider NOT NULL DEFAULT 'LOCAL',
    status account_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    
    CONSTRAINT valid_email CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT password_required CHECK (
        (provider = 'LOCAL' AND password_hash IS NOT NULL) OR 
        (provider != 'LOCAL')
    )
);

CREATE INDEX idx_user_account_email ON user_account(email);
CREATE INDEX idx_user_account_phone ON user_account(phone);
CREATE INDEX idx_user_account_status ON user_account(status);

-- =====================================================
-- USER PROFILE CONTEXT
-- =====================================================

CREATE TYPE gender AS ENUM ('MALE', 'FEMALE', 'OTHER');
CREATE TYPE tag_type AS ENUM ('HAS_PET', 'QUIET', 'MUSIC', 'EXERCISE', 'RELAX');

CREATE TABLE user_profile (
    user_id UUID PRIMARY KEY REFERENCES user_account(user_id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    gender gender,
    date_of_birth DATE,
    avatar_url TEXT,
    bio TEXT,
    search_radius INTEGER DEFAULT 5000, -- meters
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_search_radius CHECK (search_radius > 0 AND search_radius <= 50000),
    CONSTRAINT valid_age CHECK (date_of_birth IS NULL OR date_of_birth < CURRENT_DATE - INTERVAL '13 years')
);

CREATE TABLE profile_tag (
    tag_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profile(user_id) ON DELETE CASCADE,
    tag_type tag_type NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_user_tag UNIQUE (user_id, tag_type)
);

CREATE INDEX idx_profile_tag_user ON profile_tag(user_id);

-- =====================================================
-- SOCIAL GRAPH CONTEXT
-- =====================================================

CREATE TYPE friendship_status AS ENUM ('ACTIVE', 'BLOCKED');

CREATE TABLE friendship (
    friendship_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user1_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    user2_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    status friendship_status NOT NULL DEFAULT 'ACTIVE',
    favorite_user1 BOOLEAN DEFAULT FALSE,
    favorite_user2 BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT different_users CHECK (user1_id != user2_id),
    CONSTRAINT ordered_users CHECK (user1_id < user2_id),
    CONSTRAINT unique_friendship UNIQUE (user1_id, user2_id)
);

CREATE INDEX idx_friendship_user1 ON friendship(user1_id);
CREATE INDEX idx_friendship_user2 ON friendship(user2_id);
CREATE INDEX idx_friendship_status ON friendship(status);

-- =====================================================
-- WALK COORDINATION CONTEXT (Intent-Intent Matching)
-- =====================================================

CREATE TYPE intent_status AS ENUM ('OPEN', 'MATCHED', 'CONFIRMED', 'EXPIRED', 'CANCELLED');
CREATE TYPE walk_purpose AS ENUM ('EXERCISE', 'RELAX', 'PET', 'SIGHTSEEING', 'CHAT', 'OTHER');

CREATE TABLE walk_intent (
    intent_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    location_lat DOUBLE PRECISION NOT NULL,
    location_lng DOUBLE PRECISION NOT NULL,
    time_window_start TIMESTAMP NOT NULL,
    time_window_end TIMESTAMP NOT NULL,
    purpose walk_purpose NOT NULL,
    match_filter JSONB, -- {min_age, max_age, gender_preference, tags_preference}
    status intent_status NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    
    CONSTRAINT valid_time_window CHECK (time_window_end > time_window_start),
    CONSTRAINT valid_expiry CHECK (expires_at > created_at),
    CONSTRAINT future_time_window CHECK (time_window_start > created_at),
    CONSTRAINT valid_coordinates CHECK (
        location_lat >= -90 AND location_lat <= 90 AND
        location_lng >= -180 AND location_lng <= 180
    )
);

-- 🔴 INVARIANT 2: No overlapping OPEN intents for same user
-- Note: This is enforced at application layer due to complexity of overlap detection
-- Application must check: EXISTS (SELECT 1 FROM walk_intent WHERE user_id = ? AND status = 'OPEN' 
--   AND time_window_start < ? AND time_window_end > ?)

CREATE INDEX idx_walk_intent_user ON walk_intent(user_id);
CREATE INDEX idx_walk_intent_status ON walk_intent(status);
CREATE INDEX idx_walk_intent_time_window ON walk_intent(time_window_start, time_window_end);
CREATE INDEX idx_walk_intent_location ON walk_intent USING GIST(location);
CREATE INDEX idx_walk_intent_active ON walk_intent(user_id, status) WHERE status = 'OPEN';

-- =====================================================
-- MATCH PROPOSAL
-- =====================================================

CREATE TYPE proposal_status AS ENUM (
    'PENDING', 
    'ACCEPTED_BY_A', 
    'ACCEPTED_BY_B', 
    'CONFIRMED', 
    'REJECTED', 
    'EXPIRED'
);

CREATE TABLE match_proposal (
    proposal_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    intent_id_a UUID NOT NULL REFERENCES walk_intent(intent_id) ON DELETE CASCADE,
    intent_id_b UUID NOT NULL REFERENCES walk_intent(intent_id) ON DELETE CASCADE,
    proposed_start_time TIMESTAMP NOT NULL,
    proposed_end_time TIMESTAMP NOT NULL,
    proposed_location GEOGRAPHY(POINT, 4326) NOT NULL,
    proposed_lat DOUBLE PRECISION NOT NULL,
    proposed_lng DOUBLE PRECISION NOT NULL,
    status proposal_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    
    CONSTRAINT different_intents CHECK (intent_id_a != intent_id_b),
    CONSTRAINT valid_proposed_time CHECK (proposed_end_time > proposed_start_time),
    CONSTRAINT valid_proposed_coords CHECK (
        proposed_lat >= -90 AND proposed_lat <= 90 AND
        proposed_lng >= -180 AND proposed_lng <= 180
    )
);

-- 🔴 INVARIANT 3: One PENDING proposal per intent at a time
CREATE UNIQUE INDEX idx_proposal_pending_intent_a 
    ON match_proposal(intent_id_a) 
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX idx_proposal_pending_intent_b 
    ON match_proposal(intent_id_b) 
    WHERE status = 'PENDING';

CREATE INDEX idx_match_proposal_intent_a ON match_proposal(intent_id_a);
CREATE INDEX idx_match_proposal_intent_b ON match_proposal(intent_id_b);
CREATE INDEX idx_match_proposal_status ON match_proposal(status);
CREATE INDEX idx_match_proposal_expires ON match_proposal(expires_at) WHERE status = 'PENDING';

-- =====================================================
-- WALK LIFECYCLE CONTEXT
-- =====================================================

CREATE TYPE session_status AS ENUM ('PENDING', 'ACTIVE', 'COMPLETED', 'NO_SHOW', 'CANCELLED');

CREATE TABLE walk_session (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user1_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE RESTRICT,
    user2_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE RESTRICT,
    scheduled_start_time TIMESTAMP NOT NULL,
    scheduled_end_time TIMESTAMP NOT NULL,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    status session_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_intent_id_a UUID REFERENCES walk_intent(intent_id),
    source_intent_id_b UUID REFERENCES walk_intent(intent_id),
    
    CONSTRAINT different_users CHECK (user1_id != user2_id),
    CONSTRAINT valid_scheduled_time CHECK (scheduled_end_time > scheduled_start_time),
    CONSTRAINT valid_actual_time CHECK (
        actual_end_time IS NULL OR 
        actual_start_time IS NULL OR 
        actual_end_time > actual_start_time
    ),
    CONSTRAINT terminal_immutable CHECK (
        -- Terminal states cannot be modified (application enforced)
        status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'NO_SHOW', 'CANCELLED')
    )
);

-- 🔴 INVARIANT 1: No overlapping sessions for same user with status in (PENDING, ACTIVE)
-- Note: Complex overlap detection - enforced at application layer
-- Application must validate BEFORE insert:
-- - No existing session for user1_id with overlap in [scheduled_start, scheduled_end]
-- - No existing session for user2_id with overlap in [scheduled_start, scheduled_end]
-- - Both sessions must have status IN ('PENDING', 'ACTIVE')

CREATE INDEX idx_walk_session_user1 ON walk_session(user1_id);
CREATE INDEX idx_walk_session_user2 ON walk_session(user2_id);
CREATE INDEX idx_walk_session_status ON walk_session(status);
CREATE INDEX idx_walk_session_scheduled_time ON walk_session(scheduled_start_time, scheduled_end_time);
CREATE INDEX idx_walk_session_active ON walk_session(user1_id, user2_id, status) 
    WHERE status IN ('PENDING', 'ACTIVE');

-- 🔴 INVARIANT 4: Session Creation Guard
-- Helper function to check overlapping sessions
CREATE OR REPLACE FUNCTION check_session_overlap(
    p_user_id UUID,
    p_start_time TIMESTAMP,
    p_end_time TIMESTAMP,
    p_exclude_session_id UUID DEFAULT NULL
) RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM walk_session
        WHERE (user1_id = p_user_id OR user2_id = p_user_id)
        AND status IN ('PENDING', 'ACTIVE')
        AND session_id != COALESCE(p_exclude_session_id, '00000000-0000-0000-0000-000000000000'::UUID)
        AND (
            -- Overlap detection
            (scheduled_start_time < p_end_time AND scheduled_end_time > p_start_time)
        )
    );
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- CHAT ROOM & MESSAGES
-- =====================================================

CREATE TYPE chat_status AS ENUM ('OPEN', 'CLOSED');

CREATE TABLE chat_room (
    chat_room_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL UNIQUE REFERENCES walk_session(session_id) ON DELETE CASCADE,
    open_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_at TIMESTAMP,
    status chat_status NOT NULL DEFAULT 'OPEN',
    
    CONSTRAINT valid_chat_time CHECK (close_at IS NULL OR close_at > open_at)
);

CREATE INDEX idx_chat_room_session ON chat_room(session_id);
CREATE INDEX idx_chat_room_status ON chat_room(status);

CREATE TABLE chat_message (
    message_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_room_id UUID NOT NULL REFERENCES chat_room(chat_room_id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    
    CONSTRAINT non_empty_content CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_chat_message_room ON chat_message(chat_room_id, created_at);
CREATE INDEX idx_chat_message_sender ON chat_message(sender_id);

-- =====================================================
-- TRUST & REPUTATION CONTEXT
-- =====================================================

CREATE TABLE walk_review (
    review_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES walk_session(session_id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    reviewee_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    rating_stars INTEGER NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_rating CHECK (rating_stars >= 1 AND rating_stars <= 5),
    CONSTRAINT different_reviewer_reviewee CHECK (reviewer_id != reviewee_id),
    CONSTRAINT one_review_per_user_per_session UNIQUE (session_id, reviewer_id)
);

CREATE INDEX idx_walk_review_session ON walk_review(session_id);
CREATE INDEX idx_walk_review_reviewer ON walk_review(reviewer_id);
CREATE INDEX idx_walk_review_reviewee ON walk_review(reviewee_id);

CREATE TYPE review_tag_type AS ENUM (
    'FRIENDLY', 
    'PUNCTUAL', 
    'GOOD_CONVERSATION', 
    'RESPECTFUL', 
    'LATE', 
    'RUDE', 
    'UNCOMFORTABLE'
);

CREATE TABLE review_tag (
    tag_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    review_id UUID NOT NULL REFERENCES walk_review(review_id) ON DELETE CASCADE,
    tag_type review_tag_type NOT NULL,
    
    CONSTRAINT unique_review_tag UNIQUE (review_id, tag_type)
);

CREATE INDEX idx_review_tag_review ON review_tag(review_id);

-- =====================================================
-- TRUST SCORE
-- =====================================================

CREATE TABLE trust_score (
    user_id UUID PRIMARY KEY REFERENCES user_account(user_id) ON DELETE CASCADE,
    score INTEGER NOT NULL DEFAULT 100,
    total_sessions INTEGER DEFAULT 0,
    completed_sessions INTEGER DEFAULT 0,
    cancelled_sessions INTEGER DEFAULT 0,
    no_show_sessions INTEGER DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_score CHECK (score >= 0 AND score <= 1000),
    CONSTRAINT valid_counts CHECK (
        total_sessions >= 0 AND
        completed_sessions >= 0 AND
        cancelled_sessions >= 0 AND
        no_show_sessions >= 0 AND
        completed_sessions + cancelled_sessions + no_show_sessions <= total_sessions
    )
);

CREATE INDEX idx_trust_score_score ON trust_score(score DESC);

-- =====================================================
-- BADGES
-- =====================================================

CREATE TYPE badge_condition_type AS ENUM (
    'TOTAL_SESSIONS',
    'CONSECUTIVE_SESSIONS',
    'PERFECT_RATING',
    'EARLY_ADOPTER'
);

CREATE TABLE badge (
    badge_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon_url TEXT,
    condition_type badge_condition_type NOT NULL,
    condition_value INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_badge (
    user_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    badge_id UUID NOT NULL REFERENCES badge(badge_id) ON DELETE CASCADE,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (user_id, badge_id)
);

CREATE INDEX idx_user_badge_user ON user_badge(user_id);
CREATE INDEX idx_user_badge_earned ON user_badge(earned_at DESC);

-- =====================================================
-- MODERATION CONTEXT
-- =====================================================

CREATE TYPE report_status AS ENUM ('OPEN', 'RESOLVED', 'REJECTED');
CREATE TYPE report_reason AS ENUM (
    'HARASSMENT',
    'INAPPROPRIATE_BEHAVIOR',
    'NO_SHOW',
    'SAFETY_CONCERN',
    'SPAM',
    'OTHER'
);

CREATE TABLE report (
    report_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    reported_user_id UUID NOT NULL REFERENCES user_account(user_id) ON DELETE CASCADE,
    session_id UUID REFERENCES walk_session(session_id) ON DELETE SET NULL,
    reason report_reason NOT NULL,
    description TEXT NOT NULL,
    evidence_url TEXT,
    status report_status NOT NULL DEFAULT 'OPEN',
    admin_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    
    CONSTRAINT different_reporter_reported CHECK (reporter_id != reported_user_id),
    CONSTRAINT non_empty_description CHECK (length(trim(description)) > 0)
);

CREATE INDEX idx_report_reporter ON report(reporter_id);
CREATE INDEX idx_report_reported ON report(reported_user_id);
CREATE INDEX idx_report_session ON report(session_id);
CREATE INDEX idx_report_status ON report(status);

-- =====================================================
-- AI PERSONALIZATION CONTEXT
-- =====================================================

CREATE TABLE user_embedding (
    user_id UUID PRIMARY KEY REFERENCES user_account(user_id) ON DELETE CASCADE,
    vector_data FLOAT[] NOT NULL, -- Store as array, can use pgvector extension for similarity search
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_vector CHECK (array_length(vector_data, 1) > 0)
);

CREATE INDEX idx_user_embedding_updated ON user_embedding(last_updated);

-- =====================================================
-- AUDIT & LOGGING (Optional but Recommended)
-- =====================================================

CREATE TABLE session_state_change_log (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES walk_session(session_id) ON DELETE CASCADE,
    from_status session_status,
    to_status session_status NOT NULL,
    changed_by UUID REFERENCES user_account(user_id),
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_log_session ON session_state_change_log(session_id, created_at);

-- =====================================================
-- VIEWS FOR COMMON QUERIES
-- =====================================================

-- Active sessions per user
CREATE VIEW v_user_active_sessions AS
SELECT 
    user_id,
    COUNT(*) as active_session_count
FROM (
    SELECT user1_id as user_id, session_id FROM walk_session WHERE status IN ('PENDING', 'ACTIVE')
    UNION ALL
    SELECT user2_id as user_id, session_id FROM walk_session WHERE status IN ('PENDING', 'ACTIVE')
) active_sessions
GROUP BY user_id;

-- User reputation summary
CREATE VIEW v_user_reputation AS
SELECT 
    ua.user_id,
    up.full_name,
    ts.score as trust_score,
    ts.completed_sessions,
    ts.total_sessions,
    COALESCE(AVG(wr.rating_stars), 0) as average_rating,
    COUNT(ub.badge_id) as total_badges
FROM user_account ua
LEFT JOIN user_profile up ON ua.user_id = up.user_id
LEFT JOIN trust_score ts ON ua.user_id = ts.user_id
LEFT JOIN walk_review wr ON ua.user_id = wr.reviewee_id
LEFT JOIN user_badge ub ON ua.user_id = ub.user_id
GROUP BY ua.user_id, up.full_name, ts.score, ts.completed_sessions, ts.total_sessions;

-- =====================================================
-- INITIAL DATA (Optional)
-- =====================================================

-- Insert default badges
INSERT INTO badge (badge_id, name, description, condition_type, condition_value) VALUES
    (uuid_generate_v4(), 'First Steps', 'Complete your first walk session', 'TOTAL_SESSIONS', 1),
    (uuid_generate_v4(), 'Social Butterfly', 'Complete 10 walk sessions', 'TOTAL_SESSIONS', 10),
    (uuid_generate_v4(), 'Walking Champion', 'Complete 50 walk sessions', 'TOTAL_SESSIONS', 50),
    (uuid_generate_v4(), 'Perfect Score', 'Receive 10 consecutive 5-star ratings', 'PERFECT_RATING', 10),
    (uuid_generate_v4(), 'Early Adopter', 'Join during launch month', 'EARLY_ADOPTER', 1);

-- =====================================================
-- TRIGGERS FOR AUTOMATION
-- =====================================================

-- Auto-create trust score on user creation
CREATE OR REPLACE FUNCTION create_trust_score_for_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO trust_score (user_id) VALUES (NEW.user_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_create_trust_score
AFTER INSERT ON user_account
FOR EACH ROW
EXECUTE FUNCTION create_trust_score_for_new_user();

-- Update timestamp on profile update
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_profile_updated_at
BEFORE UPDATE ON user_profile
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_friendship_updated_at
BEFORE UPDATE ON friendship
FOR EACH ROW
EXECUTE FUNCTION update_updated_at();

-- Log session state changes
CREATE OR REPLACE FUNCTION log_session_state_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO session_state_change_log (session_id, from_status, to_status)
        VALUES (NEW.session_id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_log_session_state
AFTER UPDATE ON walk_session
FOR EACH ROW
EXECUTE FUNCTION log_session_state_change();

-- =====================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================

COMMENT ON TABLE walk_session IS 'Core aggregate for walk lifecycle. Enforces Invariant #1: No overlapping sessions per user.';
COMMENT ON TABLE walk_intent IS 'Discovery phase aggregate. Enforces Invariant #2: No overlapping OPEN intents per user.';
COMMENT ON TABLE match_proposal IS 'Coordination aggregate. Enforces Invariant #3: One PENDING proposal per intent.';
COMMENT ON FUNCTION check_session_overlap IS 'Helper function for Invariant #4: Session creation guard - validates no time window overlap.';

-- =====================================================
-- PERFORMANCE NOTES
-- =====================================================

-- For production, consider:
-- 1. Partitioning walk_session by created_at (monthly/yearly)
-- 2. Archiving old COMPLETED/CANCELLED sessions
-- 3. Using pgvector extension for user_embedding similarity search
-- 4. Adding materialized views for complex analytics
-- 5. Implementing connection pooling (PgBouncer)
-- 6. Setting up read replicas for reporting queries

-- =====================================================
-- END OF SCHEMA
-- =====================================================
