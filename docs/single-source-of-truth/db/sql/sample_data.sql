-- =====================================================
-- WalkMate Sample Data
-- Use this to populate database with test data
-- =====================================================

-- Clean existing data (in correct order to respect FK constraints)
TRUNCATE TABLE 
    session_state_change_log,
    chat_message,
    chat_room,
    review_tag,
    walk_review,
    user_badge,
    report,
    match_proposal,
    walk_intent,
    walk_session,
    user_embedding,
    profile_tag,
    friendship,
    user_profile,
    trust_score,
    user_account
CASCADE;

-- =====================================================
-- TEST USERS
-- =====================================================

INSERT INTO user_account (user_id, email, phone, password_hash, provider, status) VALUES
    ('11111111-1111-1111-1111-111111111111', 'alice@walkmate.com', '+84901234567', '$2a$10$placeholder', 'LOCAL', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222222', 'bob@walkmate.com', '+84901234568', '$2a$10$placeholder', 'LOCAL', 'ACTIVE'),
    ('33333333-3333-3333-3333-333333333333', 'charlie@walkmate.com', NULL, NULL, 'GOOGLE', 'ACTIVE'),
    ('44444444-4444-4444-4444-444444444444', 'diana@walkmate.com', '+84901234570', '$2a$10$placeholder', 'LOCAL', 'ACTIVE'),
    ('55555555-5555-5555-5555-555555555555', 'eve@walkmate.com', NULL, NULL, 'GOOGLE', 'ACTIVE');

-- =====================================================
-- USER PROFILES
-- =====================================================

INSERT INTO user_profile (user_id, full_name, gender, date_of_birth, avatar_url, bio, search_radius) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Alice Nguyen', 'FEMALE', '1995-05-15', 'https://i.pravatar.cc/150?u=alice', 'Love walking in the park with my dog 🐕', 3000),
    ('22222222-2222-2222-2222-222222222222', 'Bob Tran', 'MALE', '1992-08-22', 'https://i.pravatar.cc/150?u=bob', 'Morning walk enthusiast ☀️', 5000),
    ('33333333-3333-3333-3333-333333333333', 'Charlie Le', 'MALE', '1998-03-10', 'https://i.pravatar.cc/150?u=charlie', 'Always up for a walk and good conversation', 2000),
    ('44444444-4444-4444-4444-444444444444', 'Diana Pham', 'FEMALE', '1990-12-05', 'https://i.pravatar.cc/150?u=diana', 'Weekend hiker 🥾', 10000),
    ('55555555-5555-5555-5555-555555555555', 'Eve Vo', 'OTHER', '1996-07-18', 'https://i.pravatar.cc/150?u=eve', 'Nature lover and photographer 📸', 7000);

-- =====================================================
-- PROFILE TAGS
-- =====================================================

INSERT INTO profile_tag (user_id, tag_type) VALUES
    ('11111111-1111-1111-1111-111111111111', 'HAS_PET'),
    ('11111111-1111-1111-1111-111111111111', 'RELAX'),
    ('22222222-2222-2222-2222-222222222222', 'EXERCISE'),
    ('22222222-2222-2222-2222-222222222222', 'MUSIC'),
    ('33333333-3333-3333-3333-333333333333', 'QUIET'),
    ('44444444-4444-4444-4444-444444444444', 'EXERCISE'),
    ('55555555-5555-5555-5555-555555555555', 'RELAX');

-- =====================================================
-- FRIENDSHIPS
-- =====================================================

INSERT INTO friendship (user1_id, user2_id, status, favorite_user1, favorite_user2) VALUES
    ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'ACTIVE', TRUE, FALSE),
    ('11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'ACTIVE', FALSE, TRUE),
    ('22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444', 'ACTIVE', FALSE, FALSE);

-- =====================================================
-- WALK INTENTS (Scheduled Discovery)
-- =====================================================

-- Alice wants to walk tomorrow morning (OPEN)
INSERT INTO walk_intent (
    intent_id, user_id, location, location_lat, location_lng,
    time_window_start, time_window_end, purpose, status, expires_at
) VALUES (
    'aaaa1111-aaaa-1111-aaaa-111111111111',
    '11111111-1111-1111-1111-111111111111',
    ST_SetSRID(ST_MakePoint(106.7008, 10.7769), 4326)::geography, -- District 1, HCMC
    10.7769, 106.7008,
    (CURRENT_TIMESTAMP + INTERVAL '1 day')::timestamp,
    (CURRENT_TIMESTAMP + INTERVAL '1 day 1 hour')::timestamp,
    'RELAX', 'OPEN',
    (CURRENT_TIMESTAMP + INTERVAL '23 hours')::timestamp
);

-- Bob wants to walk tomorrow morning (OPEN) - can match with Alice
INSERT INTO walk_intent (
    intent_id, user_id, location, location_lat, location_lng,
    time_window_start, time_window_end, purpose, status, expires_at
) VALUES (
    'bbbb2222-bbbb-2222-bbbb-222222222222',
    '22222222-2222-2222-2222-222222222222',
    ST_SetSRID(ST_MakePoint(106.7010, 10.7770), 4326)::geography,
    10.7770, 106.7010,
    (CURRENT_TIMESTAMP + INTERVAL '1 day')::timestamp,
    (CURRENT_TIMESTAMP + INTERVAL '1 day 1 hour')::timestamp,
    'EXERCISE', 'OPEN',
    (CURRENT_TIMESTAMP + INTERVAL '23 hours')::timestamp
);

-- Charlie wants to walk tomorrow afternoon (OPEN) - different time
INSERT INTO walk_intent (
    intent_id, user_id, location, location_lat, location_lng,
    time_window_start, time_window_end, purpose, status, expires_at
) VALUES (
    'cccc3333-cccc-3333-cccc-333333333333',
    '33333333-3333-3333-3333-333333333333',
    ST_SetSRID(ST_MakePoint(106.7015, 10.7775), 4326)::geography,
    10.7775, 106.7015,
    (CURRENT_TIMESTAMP + INTERVAL '1 day 5 hours')::timestamp,
    (CURRENT_TIMESTAMP + INTERVAL '1 day 6 hours')::timestamp,
    'RELAX', 'OPEN',
    (CURRENT_TIMESTAMP + INTERVAL '1 day 4 hours')::timestamp
);

-- Eve's past intent (EXPIRED)
INSERT INTO walk_intent (
    intent_id, user_id, location, location_lat, location_lng,
    time_window_start, time_window_end, purpose, status, expires_at
) VALUES (
    'eeee5555-eeee-5555-eeee-555555555555',
    '55555555-5555-5555-5555-555555555555',
    ST_SetSRID(ST_MakePoint(106.7000, 10.7760), 4326)::geography,
    10.7760, 106.7000,
    (CURRENT_TIMESTAMP - INTERVAL '2 days')::timestamp,
    (CURRENT_TIMESTAMP - INTERVAL '2 days' + INTERVAL '1 hour')::timestamp,
    'PET', 'EXPIRED',
    (CURRENT_TIMESTAMP - INTERVAL '2 days 1 hour')::timestamp
);

-- =====================================================
-- MATCH PROPOSALS
-- =====================================================

-- Proposal between Alice and Bob (PENDING - waiting for confirmation)
INSERT INTO match_proposal (
    proposal_id, intent_id_a, intent_id_b,
    proposed_start_time, proposed_end_time,
    proposed_location, proposed_lat, proposed_lng,
    status, expires_at
) VALUES (
    'pppp1111-pppp-1111-pppp-111111111111',
    'aaaa1111-aaaa-1111-aaaa-111111111111',
    'bbbb2222-bbbb-2222-bbbb-222222222222',
    (CURRENT_TIMESTAMP + INTERVAL '1 day')::timestamp,
    (CURRENT_TIMESTAMP + INTERVAL '1 day 1 hour')::timestamp,
    ST_SetSRID(ST_MakePoint(106.7009, 10.7770), 4326)::geography,
    10.7770, 106.7009,
    'PENDING',
    (CURRENT_TIMESTAMP + INTERVAL '22 hours')::timestamp
);

-- =====================================================
-- WALK SESSIONS
-- =====================================================

-- Session 1: Completed session between Alice and Eve (past)
INSERT INTO walk_session (
    session_id, user1_id, user2_id,
    scheduled_start_time, scheduled_end_time,
    actual_start_time, actual_end_time,
    status, source_intent_id_a, source_intent_id_b
) VALUES (
    'ssss1111-ssss-1111-ssss-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '55555555-5555-5555-5555-555555555555',
    (CURRENT_TIMESTAMP - INTERVAL '3 days')::timestamp,
    (CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '1 hour')::timestamp,
    (CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '5 minutes')::timestamp,
    (CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '1 hour 3 minutes')::timestamp,
    'COMPLETED',
    NULL, NULL -- Created manually, not from intents
);

-- Session 2: Pending session between Bob and Charlie (future)
INSERT INTO walk_session (
    session_id, user1_id, user2_id,
    scheduled_start_time, scheduled_end_time,
    actual_start_time, actual_end_time,
    status
) VALUES (
    'ssss2222-ssss-2222-ssss-222222222222',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    (CURRENT_TIMESTAMP + INTERVAL '2 days')::timestamp,
    (CURRENT_TIMESTAMP + INTERVAL '2 days 1 hour')::timestamp,
    NULL, NULL,
    'PENDING'
);

-- Session 3: Cancelled session (Diana no-showed)
INSERT INTO walk_session (
    session_id, user1_id, user2_id,
    scheduled_start_time, scheduled_end_time,
    actual_start_time, actual_end_time,
    status
) VALUES (
    'ssss3333-ssss-3333-ssss-333333333333',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    (CURRENT_TIMESTAMP - INTERVAL '5 days')::timestamp,
    (CURRENT_TIMESTAMP - INTERVAL '5 days' + INTERVAL '1 hour')::timestamp,
    NULL, NULL,
    'NO_SHOW'
);

-- =====================================================
-- CHAT ROOMS & MESSAGES
-- =====================================================

-- Chat for completed session (Alice & Eve)
INSERT INTO chat_room (chat_room_id, session_id, open_at, close_at, status) VALUES (
    'cccc1111-cccc-1111-cccc-111111111111',
    'ssss1111-ssss-1111-ssss-111111111111',
    (CURRENT_TIMESTAMP - INTERVAL '3 days 30 minutes')::timestamp,
    (CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '2 hours')::timestamp,
    'CLOSED'
);

INSERT INTO chat_message (chat_room_id, sender_id, content, created_at) VALUES
    ('cccc1111-cccc-1111-cccc-111111111111', '11111111-1111-1111-1111-111111111111', 
     'Hi Eve! See you at the park in 10 mins!', 
     (CURRENT_TIMESTAMP - INTERVAL '3 days 10 minutes')::timestamp),
    ('cccc1111-cccc-1111-cccc-111111111111', '55555555-5555-5555-5555-555555555555', 
     'Great! I''m on my way 👋', 
     (CURRENT_TIMESTAMP - INTERVAL '3 days 8 minutes')::timestamp),
    ('cccc1111-cccc-1111-cccc-111111111111', '11111111-1111-1111-1111-111111111111', 
     'Thanks for the lovely walk! Hope we can do this again 🌸', 
     (CURRENT_TIMESTAMP - INTERVAL '3 days' + INTERVAL '1 hour 5 minutes')::timestamp);

-- Chat for pending session (Bob & Charlie)
INSERT INTO chat_room (chat_room_id, session_id, open_at, status) VALUES (
    'cccc2222-cccc-2222-cccc-222222222222',
    'ssss2222-ssss-2222-ssss-222222222222',
    (CURRENT_TIMESTAMP + INTERVAL '1 day 23 hours')::timestamp, -- Opens 1 hour before session
    'OPEN'
);

-- =====================================================
-- REVIEWS
-- =====================================================

-- Alice reviews Eve (5 stars)
INSERT INTO walk_review (review_id, session_id, reviewer_id, reviewee_id, rating_stars, comment) VALUES (
    'rrrr1111-rrrr-1111-rrrr-111111111111',
    'ssss1111-ssss-1111-ssss-111111111111',
    '11111111-1111-1111-1111-111111111111',
    '55555555-5555-5555-5555-555555555555',
    5,
    'Eve is wonderful! Very friendly and respectful. Had a great conversation.'
);

INSERT INTO review_tag (review_id, tag_type) VALUES
    ('rrrr1111-rrrr-1111-rrrr-111111111111', 'FRIENDLY'),
    ('rrrr1111-rrrr-1111-rrrr-111111111111', 'GOOD_CONVERSATION'),
    ('rrrr1111-rrrr-1111-rrrr-111111111111', 'PUNCTUAL');

-- Eve reviews Alice (4 stars)
INSERT INTO walk_review (review_id, session_id, reviewer_id, reviewee_id, rating_stars, comment) VALUES (
    'rrrr2222-rrrr-2222-rrrr-222222222222',
    'ssss1111-ssss-1111-ssss-111111111111',
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    4,
    'Nice walk! Alice was friendly though a bit late.'
);

INSERT INTO review_tag (review_id, tag_type) VALUES
    ('rrrr2222-rrrr-2222-rrrr-222222222222', 'FRIENDLY'),
    ('rrrr2222-rrrr-2222-rrrr-222222222222', 'RESPECTFUL');

-- Charlie reviews Diana negatively (NO_SHOW incident)
INSERT INTO walk_review (review_id, session_id, reviewer_id, reviewee_id, rating_stars, comment) VALUES (
    'rrrr3333-rrrr-3333-rrrr-333333333333',
    'ssss3333-ssss-3333-ssss-333333333333',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    1,
    'Diana did not show up and did not respond to messages.'
);

-- =====================================================
-- TRUST SCORES
-- =====================================================

-- Update trust scores based on session history
UPDATE trust_score SET 
    score = 105,
    total_sessions = 1,
    completed_sessions = 1,
    cancelled_sessions = 0,
    no_show_sessions = 0
WHERE user_id = '11111111-1111-1111-1111-111111111111';

UPDATE trust_score SET 
    score = 100,
    total_sessions = 1,
    completed_sessions = 0,
    cancelled_sessions = 0,
    no_show_sessions = 0
WHERE user_id = '22222222-2222-2222-2222-222222222222';

UPDATE trust_score SET 
    score = 95,
    total_sessions = 2,
    completed_sessions = 0,
    cancelled_sessions = 0,
    no_show_sessions = 1
WHERE user_id = '33333333-3333-3333-3333-333333333333';

UPDATE trust_score SET 
    score = 70,
    total_sessions = 1,
    completed_sessions = 0,
    cancelled_sessions = 0,
    no_show_sessions = 1
WHERE user_id = '44444444-4444-4444-4444-444444444444';

UPDATE trust_score SET 
    score = 105,
    total_sessions = 1,
    completed_sessions = 1,
    cancelled_sessions = 0,
    no_show_sessions = 0
WHERE user_id = '55555555-5555-5555-5555-555555555555';

-- =====================================================
-- USER BADGES
-- =====================================================

-- Award "First Steps" badge to users who completed a session
INSERT INTO user_badge (user_id, badge_id, earned_at)
SELECT 
    u.user_id,
    b.badge_id,
    (CURRENT_TIMESTAMP - INTERVAL '3 days')::timestamp
FROM user_account u
CROSS JOIN badge b
WHERE u.user_id IN ('11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555')
AND b.name = 'First Steps';

-- =====================================================
-- REPORTS
-- =====================================================

-- Charlie reports Diana for no-show
INSERT INTO report (
    report_id, reporter_id, reported_user_id, session_id,
    reason, description, status
) VALUES (
    'rept1111-rept-1111-rept-111111111111',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444',
    'ssss3333-ssss-3333-ssss-333333333333',
    'NO_SHOW',
    'Diana confirmed the session but never showed up and did not respond to messages.',
    'OPEN'
);

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Show all users with their trust scores
SELECT 
    up.full_name,
    ua.email,
    ts.score as trust_score,
    ts.total_sessions,
    ts.completed_sessions
FROM user_account ua
JOIN user_profile up ON ua.user_id = up.user_id
JOIN trust_score ts ON ua.user_id = ts.user_id
ORDER BY ts.score DESC;

-- Show all OPEN intents (available for matching)
SELECT 
    up.full_name,
    wi.time_window_start,
    wi.time_window_end,
    wi.purpose,
    wi.status
FROM walk_intent wi
JOIN user_profile up ON wi.user_id = up.user_id
WHERE wi.status = 'OPEN'
ORDER BY wi.time_window_start;

-- Show active/pending sessions
SELECT 
    s.session_id,
    u1.full_name as user1,
    u2.full_name as user2,
    s.scheduled_start_time,
    s.status
FROM walk_session s
JOIN user_profile u1 ON s.user1_id = u1.user_id
JOIN user_profile u2 ON s.user2_id = u2.user_id
WHERE s.status IN ('PENDING', 'ACTIVE')
ORDER BY s.scheduled_start_time;

-- Show user reputation summary
SELECT * FROM v_user_reputation
ORDER BY trust_score DESC;

-- =====================================================
-- SAMPLE DATA LOADED SUCCESSFULLY
-- =====================================================
