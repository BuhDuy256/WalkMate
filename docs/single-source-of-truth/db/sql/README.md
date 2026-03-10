# 📊 WalkMate Database Schema

## 🎯 Overview

Schema PostgreSQL cho WalkMate app với thiết kế DDD-driven, hỗ trợ đầy đủ 4 invariants chính.

## 🚀 Quick Start

```bash
# Create database
createdb walkmate

# Run schema
psql -d walkmate -f db.sql

# Verify
psql -d walkmate -c "\dt"
```

## 📐 Architecture

### Contexts & Aggregates

```
Identity & Access
├── UserAccount (AR)

User Profile
├── UserProfile (AR)
└── ProfileTag

Social Graph
└── Friendship (AR)

Walk Coordination (Intent-Intent Matching)
├── WalkIntent (AR)
└── MatchProposal (AR)

Walk Lifecycle
├── WalkSession (AR) ⭐ Core
├── ChatRoom
└── ChatMessage

Trust & Reputation
├── WalkReview (AR)
├── TrustScore (AR)
├── Badge (AR)
└── UserBadge

Moderation
└── Report (AR)

AI Personalization
└── UserEmbedding (AR)
```

## 🔴 Invariants Enforcement

### ✅ Invariant #1: Session Non-Overlapping (CORE)

**Rule:** Một user không được có 2 WalkSession trùng time window với `status IN (PENDING, ACTIVE)`

**Implementation:**

- **Database:** Helper function `check_session_overlap()`
- **Application:** MUST validate before `INSERT` hoặc `UPDATE` session

```sql
-- Example check before insert
SELECT check_session_overlap(
    'user-uuid-here',
    '2026-03-10 10:00:00',
    '2026-03-10 11:00:00'
);
-- Returns TRUE if overlap exists (BLOCKED)
-- Returns FALSE if no overlap (ALLOWED)
```

**Java Example:**

```java
@Service
public class SessionService {
    public void createSession(CreateSessionCommand cmd) {
        // ⚠️ CRITICAL: Check overlap BEFORE creating
        if (hasOverlappingSession(cmd.getUser1Id(), cmd.getTimeWindow())) {
            throw new SessionOverlapException("User1 has overlapping session");
        }
        if (hasOverlappingSession(cmd.getUser2Id(), cmd.getTimeWindow())) {
            throw new SessionOverlapException("User2 has overlapping session");
        }

        // Safe to create
        sessionRepository.save(session);
    }
}
```

---

### ✅ Invariant #2: Intent Non-Overlapping

**Rule:** Một user không được có 2 WalkIntent OPEN trùng time window

**Implementation:**

- **Application layer:** Check before creating intent
- **Index:** `idx_walk_intent_active` for fast lookup

```java
public void createIntent(CreateIntentCommand cmd) {
    if (hasOverlappingOpenIntent(cmd.getUserId(), cmd.getTimeWindow())) {
        throw new IntentOverlapException("User has overlapping OPEN intent");
    }
    intentRepository.save(intent);
}
```

---

### ✅ Invariant #3: Proposal Exclusivity

**Rule:** Một intent chỉ có tối đa 1 proposal PENDING

**Implementation:**

- **Database:** Unique partial indexes
  - `idx_proposal_pending_intent_a`
  - `idx_proposal_pending_intent_b`
- **Automatic:** PostgreSQL enforces at DB level

```sql
-- This will fail if intent already has PENDING proposal
INSERT INTO match_proposal (intent_id_a, intent_id_b, status, ...)
VALUES ('intent-uuid', 'another-uuid', 'PENDING', ...);
-- ERROR: duplicate key value violates unique constraint
```

---

### ✅ Invariant #4: Session Creation Guard

**Rule:** SessionService phải re-check Invariant #1 khi tạo session từ proposal

**Implementation:**

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void confirmProposal(UUID proposalId) {
    var proposal = proposalRepo.findById(proposalId);

    // Re-check overlap even though intents were validated
    validateNoSessionOverlap(proposal.getUser1(), proposal.getTimeWindow());
    validateNoSessionOverlap(proposal.getUser2(), proposal.getTimeWindow());

    // Safe to create session
    var session = createSessionFromProposal(proposal);
    sessionRepo.save(session);
}
```

---

## 📊 Key Tables

### `walk_session` (Core)

```sql
session_id              UUID
user1_id                UUID
user2_id                UUID
scheduled_start_time    TIMESTAMP  -- Used for overlap detection
scheduled_end_time      TIMESTAMP  -- Used for overlap detection
status                  ENUM       -- PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED
source_intent_id_a      UUID       -- Nullable (supports manual session creation)
source_intent_id_b      UUID
```

**Critical Indexes:**

- `idx_walk_session_active` - Fast lookup for overlap check
- `idx_walk_session_scheduled_time` - Time range queries

---

### `walk_intent`

```sql
intent_id           UUID
user_id             UUID
time_window_start   TIMESTAMP
time_window_end     TIMESTAMP
status              ENUM  -- OPEN, MATCHED, CONFIRMED, EXPIRED, CANCELLED
```

**Critical Indexes:**

- `idx_walk_intent_active` - WHERE status = 'OPEN'

---

### `match_proposal`

```sql
proposal_id         UUID
intent_id_a         UUID
intent_id_b         UUID
status              ENUM  -- PENDING, ACCEPTED_BY_A, ACCEPTED_BY_B, CONFIRMED, ...
```

**Critical Constraints:**

- `idx_proposal_pending_intent_a` - UNIQUE WHERE status = 'PENDING'
- `idx_proposal_pending_intent_b` - UNIQUE WHERE status = 'PENDING'

---

## 🔧 Database Extensions Required

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";      -- UUID generation
CREATE EXTENSION IF NOT EXISTS "postgis";        -- Geospatial queries
-- Optional but recommended:
CREATE EXTENSION IF NOT EXISTS "pg_trgm";        -- Fuzzy text search
CREATE EXTENSION IF NOT EXISTS "pgvector";       -- Vector similarity (for AI)
```

---

## ⚡ Performance Considerations

### Production Optimizations

1. **Partitioning**

```sql
-- Partition walk_session by month
CREATE TABLE walk_session_2026_03 PARTITION OF walk_session
FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
```

2. **Archiving**

```sql
-- Move old completed sessions to archive table
CREATE TABLE walk_session_archive (LIKE walk_session INCLUDING ALL);
```

3. **Materialized Views**

```sql
-- Pre-compute user stats
CREATE MATERIALIZED VIEW mv_user_stats AS
SELECT user_id, COUNT(*) as total_sessions, AVG(rating) as avg_rating
FROM walk_session
GROUP BY user_id;

-- Refresh daily
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_user_stats;
```

4. **Connection Pooling**

- Use PgBouncer in transaction mode
- Pool size: `(2 * CPU cores) + effective_spindle_count`

5. **Read Replicas**

- Analytics queries → read replica
- Writes & critical reads → primary

---

## 🧪 Testing Invariants

### Test Invariant #1

```sql
-- Setup
INSERT INTO user_account (user_id, email) VALUES
    ('11111111-1111-1111-1111-111111111111', 'user1@test.com'),
    ('22222222-2222-2222-2222-222222222222', 'user2@test.com');

-- Create first session
INSERT INTO walk_session (session_id, user1_id, user2_id, scheduled_start_time, scheduled_end_time, status)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '2026-03-10 10:00:00',
    '2026-03-10 11:00:00',
    'PENDING'
);

-- Check overlap (should return TRUE - blocked)
SELECT check_session_overlap(
    '11111111-1111-1111-1111-111111111111',
    '2026-03-10 10:30:00',
    '2026-03-10 11:30:00'
);

-- Check non-overlap (should return FALSE - allowed)
SELECT check_session_overlap(
    '11111111-1111-1111-1111-111111111111',
    '2026-03-10 11:00:00',  -- Exactly when first session ends
    '2026-03-10 12:00:00'
);
```

### Test Invariant #3

```sql
-- Create intents
INSERT INTO walk_intent (intent_id, user_id, location, location_lat, location_lng,
                         time_window_start, time_window_end, purpose, expires_at)
VALUES
    ('aaaa-intent', 'user1-uuid', ST_MakePoint(106.7, 10.8)::geography, 106.7, 10.8,
     '2026-03-10 10:00', '2026-03-10 11:00', 'EXERCISE', '2026-03-10 09:00'),
    ('bbbb-intent', 'user2-uuid', ST_MakePoint(106.7, 10.8)::geography, 106.7, 10.8,
     '2026-03-10 10:00', '2026-03-10 11:00', 'EXERCISE', '2026-03-10 09:00');

-- First proposal - OK
INSERT INTO match_proposal (proposal_id, intent_id_a, intent_id_b, proposed_start_time,
                           proposed_end_time, proposed_location, proposed_lat, proposed_lng,
                           status, expires_at)
VALUES ('proposal-1', 'aaaa-intent', 'bbbb-intent', '2026-03-10 10:00', '2026-03-10 11:00',
        ST_MakePoint(106.7, 10.8)::geography, 106.7, 10.8, 'PENDING', '2026-03-10 09:50');

-- Second proposal for same intent - SHOULD FAIL
INSERT INTO match_proposal (proposal_id, intent_id_a, intent_id_b, proposed_start_time,
                           proposed_end_time, proposed_location, proposed_lat, proposed_lng,
                           status, expires_at)
VALUES ('proposal-2', 'aaaa-intent', 'cccc-intent', '2026-03-10 10:00', '2026-03-10 11:00',
        ST_MakePoint(106.7, 10.8)::geography, 106.7, 10.8, 'PENDING', '2026-03-10 09:50');
-- ERROR: duplicate key value violates unique constraint "idx_proposal_pending_intent_a"
```

---

## 🔍 Useful Queries

### Find overlapping sessions

```sql
SELECT * FROM walk_session s1
WHERE EXISTS (
    SELECT 1 FROM walk_session s2
    WHERE s1.session_id != s2.session_id
    AND (s1.user1_id = s2.user1_id OR s1.user1_id = s2.user2_id
         OR s1.user2_id = s2.user1_id OR s1.user2_id = s2.user2_id)
    AND s1.status IN ('PENDING', 'ACTIVE')
    AND s2.status IN ('PENDING', 'ACTIVE')
    AND s1.scheduled_start_time < s2.scheduled_end_time
    AND s1.scheduled_end_time > s2.scheduled_start_time
);
```

### User's upcoming sessions

```sql
SELECT * FROM walk_session
WHERE (user1_id = 'user-uuid' OR user2_id = 'user-uuid')
AND status IN ('PENDING', 'ACTIVE')
AND scheduled_start_time > NOW()
ORDER BY scheduled_start_time;
```

### Available intents for matching

```sql
SELECT i.*, up.full_name, ts.score as trust_score
FROM walk_intent i
JOIN user_profile up ON i.user_id = up.user_id
JOIN trust_score ts ON i.user_id = ts.user_id
WHERE i.status = 'OPEN'
AND i.expires_at > NOW()
AND ST_DWithin(
    i.location,
    ST_MakePoint(106.7, 10.8)::geography,
    5000  -- 5km radius
)
ORDER BY i.created_at DESC;
```

---

## 🚨 Migration Notes

### From Development to Production

1. **Backup Strategy**

```bash
# Daily full backup
pg_dump walkmate > walkmate_$(date +%Y%m%d).sql

# Continuous WAL archiving
archive_mode = on
archive_command = 'cp %p /archive/%f'
```

2. **Monitoring**

```sql
-- Check table sizes
SELECT schemaname, tablename,
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Check index usage
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND schemaname = 'public';
```

3. **Security**

```sql
-- Create read-only user for analytics
CREATE ROLE analytics_readonly;
GRANT CONNECT ON DATABASE walkmate TO analytics_readonly;
GRANT USAGE ON SCHEMA public TO analytics_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO analytics_readonly;
```

---

## 📝 Schema Versioning

Current version: **1.0.0**

### Migration Strategy

- Use Flyway or Liquibase for version control
- Never modify existing migrations
- Always create new migration files
- Test migrations on staging first

Example migration naming:

```
V1__initial_schema.sql
V2__add_user_preferences.sql
V3__add_session_reschedule.sql
```

---

## 🔗 Related Documents

- [DDD-driven DB Design](../ddd-driven-db.md)
- [Final Invariants](../../../invariants/Final%20Invariants%20Version.md)
- [Backend Architecture](../../backend/BackendArchitecture.md)

---

## 🤝 Contributing

When modifying schema:

1. Update this README
2. Document WHY the change is needed
3. Verify all 4 invariants are still enforced
4. Test with sample data
5. Update application layer checks if needed

---

## ❓ FAQ

### Q: Tại sao không dùng trigger để enforce Invariant #1?

**A:** Overlap detection phức tạp và trigger có thể gây performance issue. Application layer linh hoạt hơn và dễ test hơn.

### Q: Có cần transaction isolation SERIALIZABLE không?

**A:** Với operations quan trọng như confirm proposal → create session, nên dùng SERIALIZABLE để tránh race condition.

### Q: Làm sao archive old sessions?

**A:** Partition by time + cronjob move old partitions to archive table.

### Q: PostGIS có bắt buộc không?

**A:** Không nếu chỉ lưu lat/lng. Nhưng nên dùng để query "find intents within radius" hiệu quả.

---

**Last Updated:** March 8, 2026
**Schema Version:** 1.0.0
