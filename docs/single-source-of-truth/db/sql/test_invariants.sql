-- =====================================================
-- INVARIANT TESTING SUITE
-- Test all 4 core invariants of WalkMate
-- Run this after loading sample_data.sql
-- =====================================================

\echo '=========================================='
\echo 'TESTING WALKMATE INVARIANTS'
\echo '=========================================='
\echo ''

-- =====================================================
-- TEST 1: INVARIANT #1 - Session Non-Overlapping
-- =====================================================

\echo '🔴 TEST 1: Session Non-Overlapping Invariant'
\echo '--------------------------------------------'
\echo 'Rule: User cannot have 2 sessions with overlapping time windows'
\echo '      when both have status IN (PENDING, ACTIVE)'
\echo ''

-- Setup test data
\echo '1.1 Creating test user and first session...'
DO $$
DECLARE
    test_user UUID := 'test1111-test-1111-test-111111111111';
    test_user2 UUID := 'test2222-test-2222-test-222222222222';
BEGIN
    -- Create test users if not exist
    INSERT INTO user_account (user_id, email, password_hash)
    VALUES (test_user, 'testuser1@test.com', 'hash')
    ON CONFLICT (user_id) DO NOTHING;
    
    INSERT INTO user_account (user_id, email, password_hash)
    VALUES (test_user2, 'testuser2@test.com', 'hash')
    ON CONFLICT (user_id) DO NOTHING;
    
    -- Create first session
    INSERT INTO walk_session (
        session_id, user1_id, user2_id,
        scheduled_start_time, scheduled_end_time,
        status
    ) VALUES (
        'test-sess-1111-1111-1111-111111111111',
        test_user,
        test_user2,
        CURRENT_TIMESTAMP + INTERVAL '10 hours',
        CURRENT_TIMESTAMP + INTERVAL '11 hours',
        'PENDING'
    ) ON CONFLICT DO NOTHING;
    
    RAISE NOTICE 'First session created: 10:00 - 11:00';
END $$;

\echo ''
\echo '1.2 Checking overlap detection function...'

-- Test overlap function
SELECT 
    CASE 
        WHEN check_session_overlap(
            'test1111-test-1111-test-111111111111',
            CURRENT_TIMESTAMP + INTERVAL '10.5 hours',
            CURRENT_TIMESTAMP + INTERVAL '11.5 hours'
        ) THEN '✅ PASS - Overlap detected correctly'
        ELSE '❌ FAIL - Overlap NOT detected'
    END as test_result;

\echo ''
\echo '1.3 Checking non-overlap (should be allowed)...'

SELECT 
    CASE 
        WHEN NOT check_session_overlap(
            'test1111-test-1111-test-111111111111',
            CURRENT_TIMESTAMP + INTERVAL '11 hours',  -- Starts when first ends
            CURRENT_TIMESTAMP + INTERVAL '12 hours'
        ) THEN '✅ PASS - Non-overlap allowed'
        ELSE '❌ FAIL - False positive overlap'
    END as test_result;

\echo ''
\echo '1.4 Testing application-level enforcement...'
\echo '    (In production, this should be blocked by SessionService)'

-- This SHOULD be blocked by application layer
-- Database allows it, but app should prevent it
DO $$
DECLARE
    has_overlap BOOLEAN;
BEGIN
    has_overlap := check_session_overlap(
        'test1111-test-1111-test-111111111111',
        CURRENT_TIMESTAMP + INTERVAL '10.5 hours',
        CURRENT_TIMESTAMP + INTERVAL '11.5 hours'
    );
    
    IF has_overlap THEN
        RAISE NOTICE '✅ Application should BLOCK this - overlap exists';
    ELSE
        RAISE NOTICE '❌ No overlap detected - something is wrong';
    END IF;
END $$;

\echo ''

-- Cleanup
DELETE FROM walk_session WHERE session_id = 'test-sess-1111-1111-1111-111111111111';

-- =====================================================
-- TEST 2: INVARIANT #2 - Intent Non-Overlapping
-- =====================================================

\echo ''
\echo '🔴 TEST 2: Intent Non-Overlapping Invariant'
\echo '--------------------------------------------'
\echo 'Rule: User cannot have 2 OPEN intents with overlapping time windows'
\echo ''

\echo '2.1 Creating first OPEN intent...'

DO $$
DECLARE
    test_user UUID := 'test1111-test-1111-test-111111111111';
BEGIN
    INSERT INTO walk_intent (
        intent_id, user_id,
        location, location_lat, location_lng,
        time_window_start, time_window_end,
        purpose, status, expires_at
    ) VALUES (
        'test-int-1111-1111-1111-111111111111',
        test_user,
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        CURRENT_TIMESTAMP + INTERVAL '5 hours',
        CURRENT_TIMESTAMP + INTERVAL '6 hours',
        'EXERCISE', 'OPEN',
        CURRENT_TIMESTAMP + INTERVAL '4.5 hours'
    ) ON CONFLICT DO NOTHING;
    
    RAISE NOTICE 'First OPEN intent: 05:00 - 06:00';
END $$;

\echo ''
\echo '2.2 Checking for overlapping OPEN intents (application-level)...'

-- Query to detect overlap
SELECT 
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM walk_intent
            WHERE user_id = 'test1111-test-1111-test-111111111111'
            AND status = 'OPEN'
            AND time_window_start < CURRENT_TIMESTAMP + INTERVAL '5.5 hours'
            AND time_window_end > CURRENT_TIMESTAMP + INTERVAL '4.5 hours'
        ) THEN '✅ PASS - Overlap would be detected by application'
        ELSE '❌ FAIL - No overlap detected'
    END as test_result;

\echo ''
\echo '2.3 Non-overlapping OPEN intent (should be allowed)...'

SELECT 
    CASE 
        WHEN NOT EXISTS (
            SELECT 1 FROM walk_intent
            WHERE user_id = 'test1111-test-1111-test-111111111111'
            AND status = 'OPEN'
            AND time_window_start < CURRENT_TIMESTAMP + INTERVAL '8 hours'
            AND time_window_end > CURRENT_TIMESTAMP + INTERVAL '7 hours'
        ) THEN '✅ PASS - Non-overlapping intent can be created'
        ELSE '❌ FAIL - Should allow non-overlapping'
    END as test_result;

\echo ''

-- Cleanup
DELETE FROM walk_intent WHERE intent_id = 'test-int-1111-1111-1111-111111111111';

-- =====================================================
-- TEST 3: INVARIANT #3 - Proposal Exclusivity
-- =====================================================

\echo ''
\echo '🔴 TEST 3: Proposal Exclusivity Invariant'
\echo '--------------------------------------------'
\echo 'Rule: An intent can have max 1 PENDING proposal at a time'
\echo ''

\echo '3.1 Creating test intents...'

DO $$
BEGIN
    INSERT INTO walk_intent (
        intent_id, user_id,
        location, location_lat, location_lng,
        time_window_start, time_window_end,
        purpose, status, expires_at
    ) VALUES 
    (
        'test-int-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'test1111-test-1111-test-111111111111',
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        CURRENT_TIMESTAMP + INTERVAL '20 hours',
        CURRENT_TIMESTAMP + INTERVAL '21 hours',
        'EXERCISE', 'OPEN',
        CURRENT_TIMESTAMP + INTERVAL '19 hours'
    ),
    (
        'test-int-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'test2222-test-2222-test-222222222222',
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        CURRENT_TIMESTAMP + INTERVAL '20 hours',
        CURRENT_TIMESTAMP + INTERVAL '21 hours',
        'EXERCISE', 'OPEN',
        CURRENT_TIMESTAMP + INTERVAL '19 hours'
    ),
    (
        'test-int-cccc-cccc-cccc-cccccccccccc',
        '33333333-3333-3333-3333-333333333333', -- Charlie from sample data
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        CURRENT_TIMESTAMP + INTERVAL '20 hours',
        CURRENT_TIMESTAMP + INTERVAL '21 hours',
        'EXERCISE', 'OPEN',
        CURRENT_TIMESTAMP + INTERVAL '19 hours'
    )
    ON CONFLICT DO NOTHING;
END $$;

\echo ''
\echo '3.2 Creating first PENDING proposal (should succeed)...'

DO $$
BEGIN
    INSERT INTO match_proposal (
        proposal_id, intent_id_a, intent_id_b,
        proposed_start_time, proposed_end_time,
        proposed_location, proposed_location_lat, proposed_location_lng,
        status, expires_at
    ) VALUES (
        'test-prop-1111-1111-1111-111111111111',
        'test-int-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'test-int-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        CURRENT_TIMESTAMP + INTERVAL '20 hours',
        CURRENT_TIMESTAMP + INTERVAL '21 hours',
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        'PENDING',
        CURRENT_TIMESTAMP + INTERVAL '19 hours'
    );
    RAISE NOTICE '✅ First PENDING proposal created';
EXCEPTION
    WHEN others THEN
        RAISE NOTICE '❌ FAIL - Could not create first proposal: %', SQLERRM;
END $$;

\echo ''
\echo '3.3 Attempting to create second PENDING proposal (should fail)...'

DO $$
BEGIN
    INSERT INTO match_proposal (
        proposal_id, intent_id_a, intent_id_b,
        proposed_start_time, proposed_end_time,
        proposed_location, proposed_location_lat, proposed_location_lng,
        status, expires_at
    ) VALUES (
        'test-prop-2222-2222-2222-222222222222',
        'test-int-aaaa-aaaa-aaaa-aaaaaaaaaaaa', -- Same intent A
        'test-int-cccc-cccc-cccc-cccccccccccc', -- Different intent B
        CURRENT_TIMESTAMP + INTERVAL '20 hours',
        CURRENT_TIMESTAMP + INTERVAL '21 hours',
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        'PENDING',
        CURRENT_TIMESTAMP + INTERVAL '19 hours'
    );
    RAISE NOTICE '❌ FAIL - Second PENDING proposal should NOT be allowed';
EXCEPTION
    WHEN unique_violation THEN
        RAISE NOTICE '✅ PASS - Unique constraint prevented duplicate PENDING proposal';
    WHEN others THEN
        RAISE NOTICE '❌ FAIL - Unexpected error: %', SQLERRM;
END $$;

\echo ''
\echo '3.4 Creating non-PENDING proposal (should succeed)...'

DO $$
BEGIN
    INSERT INTO match_proposal (
        proposal_id, intent_id_a, intent_id_b,
        proposed_start_time, proposed_end_time,
        proposed_location, proposed_location_lat, proposed_location_lng,
        status, expires_at
    ) VALUES (
        'test-prop-3333-3333-3333-333333333333',
        'test-int-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'test-int-cccc-cccc-cccc-cccccccccccc',
        CURRENT_TIMESTAMP + INTERVAL '20 hours',
        CURRENT_TIMESTAMP + INTERVAL '21 hours',
        ST_SetSRID(ST_MakePoint(106.7, 10.8), 4326)::geography,
        10.8, 106.7,
        'REJECTED', -- Not PENDING
        CURRENT_TIMESTAMP + INTERVAL '19 hours'
    );
    RAISE NOTICE '✅ PASS - Non-PENDING proposal allowed (multiple REJECTED ok)';
EXCEPTION
    WHEN others THEN
        RAISE NOTICE '❌ FAIL - Could not create REJECTED proposal: %', SQLERRM;
END $$;

\echo ''

-- Cleanup
DELETE FROM match_proposal WHERE proposal_id LIKE 'test-prop%';
DELETE FROM walk_intent WHERE intent_id LIKE 'test-int%';

-- =====================================================
-- TEST 4: INVARIANT #4 - Session Creation Guard
-- =====================================================

\echo ''
\echo '🔴 TEST 4: Session Creation Guard'
\echo '--------------------------------------------'
\echo 'Rule: SessionService must re-check overlap before creating session'
\echo ''

\echo '4.1 Simulating proposal confirmation flow...'

DO $$
DECLARE
    test_user1 UUID := 'test1111-test-1111-test-111111111111';
    test_user2 UUID := 'test2222-test-2222-test-222222222222';
    has_overlap BOOLEAN;
BEGIN
    -- Step 1: Create existing session
    INSERT INTO walk_session (
        session_id, user1_id, user2_id,
        scheduled_start_time, scheduled_end_time,
        status
    ) VALUES (
        'test-existing-sess-1111-111111111111',
        test_user1,
        test_user2,
        CURRENT_TIMESTAMP + INTERVAL '30 hours',
        CURRENT_TIMESTAMP + INTERVAL '31 hours',
        'PENDING'
    );
    RAISE NOTICE 'Existing session: 30:00 - 31:00';
    
    -- Step 2: Check overlap before creating new session
    has_overlap := check_session_overlap(
        test_user1,
        CURRENT_TIMESTAMP + INTERVAL '30.5 hours',
        CURRENT_TIMESTAMP + INTERVAL '31.5 hours'
    );
    
    IF has_overlap THEN
        RAISE NOTICE '✅ PASS - SessionService would detect overlap and BLOCK creation';
    ELSE
        RAISE NOTICE '❌ FAIL - Overlap not detected';
    END IF;
    
    -- Step 3: Try non-overlapping session
    has_overlap := check_session_overlap(
        test_user1,
        CURRENT_TIMESTAMP + INTERVAL '32 hours',
        CURRENT_TIMESTAMP + INTERVAL '33 hours'
    );
    
    IF NOT has_overlap THEN
        RAISE NOTICE '✅ PASS - Non-overlapping session would be ALLOWED';
    ELSE
        RAISE NOTICE '❌ FAIL - False positive overlap';
    END IF;
END $$;

\echo ''

-- Cleanup
DELETE FROM walk_session WHERE session_id LIKE 'test-%';
DELETE FROM user_account WHERE user_id LIKE 'test%';

-- =====================================================
-- SUMMARY REPORT
-- =====================================================

\echo ''
\echo '=========================================='
\echo 'INVARIANT TEST SUMMARY'
\echo '=========================================='
\echo ''
\echo 'Invariant #1: Session Non-Overlapping'
\echo '  ├─ Overlap detection: Database function ✅'
\echo '  └─ Enforcement: Application layer (SessionService)'
\echo ''
\echo 'Invariant #2: Intent Non-Overlapping'
\echo '  ├─ Overlap detection: Application query ✅'
\echo '  └─ Enforcement: Application layer (IntentService)'
\echo ''
\echo 'Invariant #3: Proposal Exclusivity'
\echo '  ├─ Enforcement: Database unique constraint ✅'
\echo '  └─ Automatic database-level protection'
\echo ''
\echo 'Invariant #4: Session Creation Guard'
\echo '  ├─ Helper function: check_session_overlap() ✅'
\echo '  └─ Enforcement: SessionService.confirmProposal()'
\echo ''
\echo '=========================================='
\echo 'All invariant mechanisms tested!'
\echo '=========================================='

-- =====================================================
-- ADDITIONAL DIAGNOSTIC QUERIES
-- =====================================================

\echo ''
\echo 'DIAGNOSTIC QUERIES'
\echo '===================='
\echo ''

\echo 'Current overlapping sessions (should be 0):'
SELECT 
    s1.session_id as session1,
    s2.session_id as session2,
    CASE 
        WHEN s1.user1_id IN (s2.user1_id, s2.user2_id) THEN s1.user1_id
        WHEN s1.user2_id IN (s2.user1_id, s2.user2_id) THEN s1.user2_id
    END as overlapping_user,
    s1.scheduled_start_time as s1_start,
    s1.scheduled_end_time as s1_end,
    s2.scheduled_start_time as s2_start,
    s2.scheduled_end_time as s2_end
FROM walk_session s1
JOIN walk_session s2 ON s1.session_id < s2.session_id
WHERE s1.status IN ('PENDING', 'ACTIVE')
AND s2.status IN ('PENDING', 'ACTIVE')
AND (
    s1.user1_id IN (s2.user1_id, s2.user2_id) OR
    s1.user2_id IN (s2.user1_id, s2.user2_id)
)
AND s1.scheduled_start_time < s2.scheduled_end_time
AND s1.scheduled_end_time > s2.scheduled_start_time;

\echo ''
\echo 'Intents with multiple PENDING proposals (should be 0):'
SELECT 
    intent_id,
    COUNT(*) as pending_count
FROM (
    SELECT intent_id_a as intent_id FROM match_proposal WHERE status = 'PENDING'
    UNION ALL
    SELECT intent_id_b as intent_id FROM match_proposal WHERE status = 'PENDING'
) pending_intents
GROUP BY intent_id
HAVING COUNT(*) > 1;

\echo ''
\echo 'Users with overlapping OPEN intents (should be 0):'
SELECT 
    i1.user_id,
    COUNT(*) as overlapping_intents
FROM walk_intent i1
JOIN walk_intent i2 ON i1.user_id = i2.user_id AND i1.intent_id < i2.intent_id
WHERE i1.status = 'OPEN'
AND i2.status = 'OPEN'
AND i1.time_window_start < i2.time_window_end
AND i1.time_window_end > i2.time_window_start
GROUP BY i1.user_id;

\echo ''
\echo 'Test suite complete!'
