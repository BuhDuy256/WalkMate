-- ============================================================
-- WalkMate – Initial Schema  (Flyway V0_init)
-- Database: PostgreSQL (Supabase)
-- ============================================================

-- ── Extensions ───────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- pgvector: required for the user_embedding.vector_data column and ANN index.
-- Pre-installed on Supabase; for self-hosted Postgres run: apt install postgresql-16-pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- ENUM TYPES
-- ============================================================

-- user_account
CREATE TYPE auth_provider  AS ENUM ('LOCAL');
CREATE TYPE account_status AS ENUM ('ACTIVE');

-- user_profile
CREATE TYPE gender AS ENUM ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY');

-- walk_intent
CREATE TYPE intent_status AS ENUM ('OPEN', 'CONSUMED', 'CANCELLED', 'EXPIRED');

-- match_proposal
CREATE TYPE proposal_status AS ENUM ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED');

-- walk_session + session_state_change_log
-- FIX: renamed from "session_status" → "walk_session_status" to match
--      CAST(:status AS walk_session_status) in WalkSessionJdbcRepository
CREATE TYPE walk_session_status AS ENUM (
    'PENDING', 'ACTIVE', 'COMPLETED', 'NO_SHOW', 'CANCELLED', 'ABORTED'
);

-- notification
CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'READ');

-- presence
CREATE TYPE presence_status       AS ENUM ('ONLINE', 'OFFLINE');
CREATE TYPE presence_availability AS ENUM ('AVAILABLE', 'UNAVAILABLE');

-- reporting / moderation / chat
CREATE TYPE report_status  AS ENUM ('OPEN', 'RESOLVED', 'DISMISSED');
CREATE TYPE dispute_status AS ENUM ('OPEN', 'RESOLVED', 'DISMISSED');
CREATE TYPE chat_status    AS ENUM ('OPEN', 'CLOSED');

-- ============================================================
-- TABLES  (ordered by FK dependency — referenced tables first)
-- ============================================================

-- ── user_account ─────────────────────────────────────────────
CREATE TABLE public.user_account (
    user_id           uuid           NOT NULL DEFAULT uuid_generate_v4(),
    email             varchar        NOT NULL UNIQUE
        CHECK (email ~* '^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$'),
    phone             varchar        UNIQUE,
    password_hash     text,
    provider          auth_provider  NOT NULL DEFAULT 'LOCAL',
    status            account_status NOT NULL DEFAULT 'ACTIVE',
    created_at        timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at     timestamp,
    trust_score       integer        NOT NULL DEFAULT 0,
    total_points      integer        NOT NULL DEFAULT 0,
    total_distance_km numeric        NOT NULL DEFAULT 0,
    completed_sessions integer       NOT NULL DEFAULT 0,
    CONSTRAINT user_account_pkey PRIMARY KEY (user_id)
);

-- ── user_profile ─────────────────────────────────────────────
CREATE TABLE public.user_profile (
    user_id       uuid      NOT NULL,
    full_name     varchar   NOT NULL,
    gender        gender,
    date_of_birth date
        CHECK (date_of_birth IS NULL OR date_of_birth < (CURRENT_DATE - INTERVAL '13 years')),
    avatar_url    text,
    bio           text,
    search_radius integer   DEFAULT 5000
        CHECK (search_radius > 0 AND search_radius <= 50000),
    created_at    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_profile_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_profile_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── profile_tag ───────────────────────────────────────────────
-- FIX: replaced "tag_type USER-DEFINED" with "tag_name varchar".
--      UserProfileJdbcRepository.replaceTags inserts (user_id, tag_name)
--      and findTagsByUserId selects tag_name — there is no enum column here.
CREATE TABLE public.profile_tag (
    tag_id     uuid      NOT NULL DEFAULT uuid_generate_v4(),
    user_id    uuid      NOT NULL,
    tag_name   varchar   NOT NULL CHECK (length(trim(tag_name)) > 0),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profile_tag_pkey PRIMARY KEY (tag_id),
    CONSTRAINT profile_tag_user_profile_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_profile (user_id) ON DELETE CASCADE
);

-- ── refresh_token ─────────────────────────────────────────────
CREATE TABLE public.refresh_token (
    token_id    uuid      NOT NULL DEFAULT uuid_generate_v4(),
    user_id     uuid      NOT NULL,
    token_value text      NOT NULL UNIQUE,
    created_at  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT refresh_token_pkey PRIMARY KEY (token_id),
    CONSTRAINT refresh_token_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── user_presence ─────────────────────────────────────────────
CREATE TABLE public.user_presence (
    user_id        uuid                  NOT NULL,
    status         presence_status       NOT NULL DEFAULT 'OFFLINE',
    availability   presence_availability NOT NULL DEFAULT 'UNAVAILABLE',
    quick_mode     boolean               NOT NULL DEFAULT false,
    last_active_at timestamp             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at     timestamp,
    CONSTRAINT user_presence_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_presence_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── trust_score ───────────────────────────────────────────────
CREATE TABLE public.trust_score (
    user_id            uuid      NOT NULL,
    score              integer   NOT NULL DEFAULT 100
        CHECK (score >= 0 AND score <= 1000),
    total_sessions     integer   NOT NULL DEFAULT 0,
    completed_sessions integer   NOT NULL DEFAULT 0,
    cancelled_sessions integer   NOT NULL DEFAULT 0,
    no_show_sessions   integer   NOT NULL DEFAULT 0,
    aborted_sessions   integer   NOT NULL DEFAULT 0,
    last_updated       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT trust_score_pkey PRIMARY KEY (user_id),
    CONSTRAINT trust_score_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── user_embedding ────────────────────────────────────────────
-- FIX: uses pgvector's native "vector(768)" type instead of double precision[].
--      This enables cosine/L2/dot-product distance operators (<=> <#> <->)
--      and the HNSW approximate-nearest-neighbour index defined below.
--      Dimension 768 matches sentence-transformers / BERT-based models.
--      Java layer uses PGobject with wire format "[x1,x2,...,xn]" — no extra dep.
CREATE TABLE public.user_embedding (
    user_id      uuid        NOT NULL,
    vector_data  vector(768) NOT NULL,
    last_updated timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_embedding_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_embedding_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── user_badge ────────────────────────────────────────────────
-- FIX: completely rewritten to match UserBadgeJdbcRepository:
--   • badge_name varchar  instead of badge_id uuid FK → badge table
--   • awarded_at          instead of earned_at
--   • PRIMARY KEY (user_id, badge_name) to support
--     ON CONFLICT (user_id, badge_name) DO NOTHING in saveAll()
--   Badge is a pure Java enum; no separate badge lookup table is referenced.
CREATE TABLE public.user_badge (
    user_id    uuid      NOT NULL,
    badge_name varchar   NOT NULL,
    awarded_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_badge_pkey PRIMARY KEY (user_id, badge_name),
    CONSTRAINT user_badge_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── matching_preference_model ─────────────────────────────────
CREATE TABLE public.matching_preference_model (
    user_id             uuid             NOT NULL,
    weight_time_overlap double precision DEFAULT 1.0,
    weight_interest     double precision DEFAULT 1.0,
    weight_behavior     double precision DEFAULT 1.0,
    weight_distance     double precision DEFAULT 1.0,
    last_trained_at     timestamp        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT matching_preference_model_pkey PRIMARY KEY (user_id),
    CONSTRAINT matching_preference_model_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── follow_relation ───────────────────────────────────────────
-- FIX: renamed created_at → followed_at.
--      SocialJdbcRepository.getFollowerIds / getFolloweeIds both
--      ORDER BY followed_at DESC — the old created_at column caused a
--      "column does not exist" error at runtime.
-- FIX: added UNIQUE (follower_id, followee_id) required by ON CONFLICT DO NOTHING.
CREATE TABLE public.follow_relation (
    follow_id   uuid      NOT NULL DEFAULT uuid_generate_v4(),
    follower_id uuid      NOT NULL,
    followee_id uuid      NOT NULL,
    followed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT follow_relation_pkey    PRIMARY KEY (follow_id),
    CONSTRAINT follow_relation_unique  UNIQUE (follower_id, followee_id),
    CONSTRAINT follow_relation_no_self CHECK (follower_id <> followee_id),
    CONSTRAINT follow_relation_follower_id_fkey
        FOREIGN KEY (follower_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT follow_relation_followee_id_fkey
        FOREIGN KEY (followee_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── block_relation ────────────────────────────────────────────
-- FIX: added UNIQUE (blocker_id, blocked_id) required by ON CONFLICT DO NOTHING.
CREATE TABLE public.block_relation (
    block_id   uuid      NOT NULL DEFAULT uuid_generate_v4(),
    blocker_id uuid      NOT NULL,
    blocked_id uuid      NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT block_relation_pkey    PRIMARY KEY (block_id),
    CONSTRAINT block_relation_unique  UNIQUE (blocker_id, blocked_id),
    CONSTRAINT block_relation_no_self CHECK (blocker_id <> blocked_id),
    CONSTRAINT block_relation_blocker_id_fkey
        FOREIGN KEY (blocker_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT block_relation_blocked_id_fkey
        FOREIGN KEY (blocked_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── notification ──────────────────────────────────────────────
-- FIX: added read_at column.
--      NotificationJdbcRepository INSERT includes read_at and SELECT reads it.
--      The old schema omitted this column entirely, causing INSERT failures.
-- NOTE: type is stored as varchar (NotificationType.name()), not cast to a PG enum.
CREATE TABLE public.notification (
    notification_id uuid                NOT NULL DEFAULT uuid_generate_v4(),
    user_id         uuid                NOT NULL,
    type            varchar             NOT NULL,
    payload         jsonb,
    status          notification_status NOT NULL DEFAULT 'PENDING',
    created_at      timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         timestamp,
    CONSTRAINT notification_pkey PRIMARY KEY (notification_id),
    CONSTRAINT notification_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── badge (admin / display metadata — not accessed by any Java repository) ──
-- Kept as reference data table. Java gamification uses the Badge enum directly.
CREATE TABLE public.badge (
    badge_id    uuid      NOT NULL DEFAULT uuid_generate_v4(),
    name        varchar   NOT NULL UNIQUE,
    description text,
    icon_url    text,
    created_at  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT badge_pkey PRIMARY KEY (badge_id)
);

-- ── hotspot ───────────────────────────────────────────────────
CREATE TABLE public.hotspot (
    id   uuid             NOT NULL DEFAULT gen_random_uuid(),
    name varchar          NOT NULL,
    lat  double precision NOT NULL,
    lng  double precision NOT NULL,
    CONSTRAINT hotspot_pkey PRIMARY KEY (id)
);

-- ── walk_intent ───────────────────────────────────────────────
CREATE TABLE public.walk_intent (
    intent_id            uuid          NOT NULL DEFAULT gen_random_uuid(),
    hotspot_id           uuid          NOT NULL,
    user_id              uuid          NOT NULL,
    time_window_start    timestamp     NOT NULL,
    time_window_end      timestamp     NOT NULL,
    matching_constraints jsonb         NOT NULL DEFAULT '{}',
    status               intent_status NOT NULL DEFAULT 'OPEN',
    created_at           timestamp     NOT NULL DEFAULT now(),
    expires_at           timestamp     NOT NULL,
    version              bigint        NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT walk_intent_pkey PRIMARY KEY (intent_id),
    CONSTRAINT walk_intent_time_window_chk
        CHECK (time_window_end > time_window_start),
    CONSTRAINT walk_intent_hotspot_id_fkey
        FOREIGN KEY (hotspot_id) REFERENCES public.hotspot (id) ON DELETE RESTRICT,
    CONSTRAINT walk_intent_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- ── match_proposal ────────────────────────────────────────────
CREATE TABLE public.match_proposal (
    proposal_id           uuid            NOT NULL DEFAULT gen_random_uuid(),
    intent_id_a           uuid            NOT NULL,
    intent_id_b           uuid            NOT NULL,
    proposed_start_time   timestamp       NOT NULL,
    proposed_end_time     timestamp       NOT NULL,
    proposed_location_lat double precision NOT NULL,
    proposed_location_lng double precision NOT NULL,
    accepted_by_a         boolean         NOT NULL DEFAULT false,
    accepted_by_b         boolean         NOT NULL DEFAULT false,
    status                proposal_status NOT NULL DEFAULT 'PENDING',
    created_at            timestamp       NOT NULL DEFAULT now(),
    expires_at            timestamp       NOT NULL,
    confirmed_at          timestamp,
    CONSTRAINT match_proposal_pkey PRIMARY KEY (proposal_id),
    CONSTRAINT match_proposal_end_after_start_chk
        CHECK (proposed_end_time > proposed_start_time),
    CONSTRAINT match_proposal_intent_id_a_fkey
        FOREIGN KEY (intent_id_a) REFERENCES public.walk_intent (intent_id) ON DELETE RESTRICT,
    CONSTRAINT match_proposal_intent_id_b_fkey
        FOREIGN KEY (intent_id_b) REFERENCES public.walk_intent (intent_id) ON DELETE RESTRICT
);

-- ── walk_session ──────────────────────────────────────────────
-- COMPLETE REWRITE — aligned with WalkSessionJdbcRepository INSERT/SELECT.
--
-- Column renames (old → new):
--   user1_id, user2_id        → user_id_a, user_id_b
--   scheduled_start_time      → scheduled_start
--   scheduled_end_time        → scheduled_end
--   actual_start_time         → started_at
--   actual_end_time           → ended_at
--   user1_activated_at        → removed (was duplicate of user_a_activated_at)
--   user2_activated_at        → removed (was duplicate of user_b_activated_at)
--
-- Columns added:
--   meeting_point_lat/lng     (required by Java INSERT; was missing entirely)
--
-- Columns removed:
--   total_distance, total_duration  (not in Java INSERT or SELECT)
--
-- Enum type:  session_status → walk_session_status
--   Java explicitly uses CAST(:status AS walk_session_status).
--
-- abort_reason CHECK values corrected to match AbortReason enum:
--   Old: INJURY, SAFETY, ENVIRONMENT, OTHER
--   New: SAFETY_CONCERN, EMERGENCY, PARTNER_MISCONDUCT, OTHER
CREATE TABLE public.walk_session (
    session_id          uuid                NOT NULL DEFAULT uuid_generate_v4(),
    proposal_id         uuid                NOT NULL,
    user_id_a           uuid                NOT NULL,
    user_id_b           uuid                NOT NULL,
    meeting_point_lat   double precision    NOT NULL,
    meeting_point_lng   double precision    NOT NULL,
    scheduled_start     timestamp           NOT NULL,
    scheduled_end       timestamp           NOT NULL,
    status              walk_session_status NOT NULL DEFAULT 'PENDING',
    created_at          timestamp           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at          timestamp,
    ended_at            timestamp,
    user_a_activated_at timestamp,
    user_b_activated_at timestamp,
    cancellation_reason varchar,
    cancelled_by        uuid,
    abort_reason        varchar
        CHECK (abort_reason IS NULL OR abort_reason IN (
            'SAFETY_CONCERN', 'EMERGENCY', 'PARTNER_MISCONDUCT', 'OTHER'
        )),
    version             bigint              NOT NULL DEFAULT 0 CHECK (version >= 0),
    -- Walk outcome stats: populated at completion/abort by the domain + gamification layer.
    -- total_distance_km is set by GamificationCommandService after aggregating GPS polylines.
    -- total_duration_seconds is set by WalkSession.complete() / abort() from startedAt→endedAt.
    total_distance_km      numeric(10, 3)   NOT NULL DEFAULT 0 CHECK (total_distance_km >= 0),
    total_duration_seconds bigint           NOT NULL DEFAULT 0 CHECK (total_duration_seconds >= 0),
    -- Used by HotspotJdbcRepository to count active walkers per hotspot
    source_intent_id_a  uuid,
    source_intent_id_b  uuid,
    CONSTRAINT walk_session_pkey PRIMARY KEY (session_id),
    CONSTRAINT walk_session_end_after_start_chk
        CHECK (scheduled_end > scheduled_start),
    CONSTRAINT walk_session_proposal_id_fkey
        FOREIGN KEY (proposal_id)        REFERENCES public.match_proposal (proposal_id) ON DELETE RESTRICT,
    CONSTRAINT walk_session_user_id_a_fkey
        FOREIGN KEY (user_id_a)          REFERENCES public.user_account (user_id)       ON DELETE RESTRICT,
    CONSTRAINT walk_session_user_id_b_fkey
        FOREIGN KEY (user_id_b)          REFERENCES public.user_account (user_id)       ON DELETE RESTRICT,
    CONSTRAINT walk_session_cancelled_by_fkey
        FOREIGN KEY (cancelled_by)       REFERENCES public.user_account (user_id)       ON DELETE SET NULL,
    CONSTRAINT walk_session_source_a_fkey
        FOREIGN KEY (source_intent_id_a) REFERENCES public.walk_intent (intent_id)      ON DELETE SET NULL,
    CONSTRAINT walk_session_source_b_fkey
        FOREIGN KEY (source_intent_id_b) REFERENCES public.walk_intent (intent_id)      ON DELETE SET NULL
);

-- ── session_state_change_log ──────────────────────────────────
-- FIX: enum type changed from "session_status" to "walk_session_status".
--      WalkSessionJdbcRepository.logStateChange explicitly casts to walk_session_status.
-- SIMPLIFIED: merged redundant created_at + changed_at into a single changed_at column
--      that the Java INSERT actually writes.
CREATE TABLE public.session_state_change_log (
    log_id      uuid                NOT NULL DEFAULT uuid_generate_v4(),
    session_id  uuid                NOT NULL,
    from_status walk_session_status,
    to_status   walk_session_status NOT NULL,
    changed_by  uuid,
    reason      text,
    changed_at  timestamp           NOT NULL DEFAULT now(),
    CONSTRAINT session_state_change_log_pkey PRIMARY KEY (log_id),
    CONSTRAINT session_state_change_log_session_id_fkey
        FOREIGN KEY (session_id)  REFERENCES public.walk_session   (session_id) ON DELETE CASCADE,
    CONSTRAINT session_state_change_log_changed_by_fkey
        FOREIGN KEY (changed_by)  REFERENCES public.user_account   (user_id)    ON DELETE SET NULL
);

-- ── session_point_chunks ──────────────────────────────────────
-- Added UNIQUE (session_id, chunk_index) to enforce ordering integrity
-- and prevent duplicate chunk uploads.
CREATE TABLE public.session_point_chunks (
    chunk_id    uuid      NOT NULL DEFAULT uuid_generate_v4(),
    session_id  uuid      NOT NULL,
    chunk_index integer   NOT NULL CHECK (chunk_index >= 0),
    polyline    text      NOT NULL,
    timestamps  bytea,
    elevations  bytea,
    point_count integer   NOT NULL CHECK (point_count > 0),
    created_at  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_point_chunks_pkey   PRIMARY KEY (chunk_id),
    CONSTRAINT session_point_chunks_unique UNIQUE (session_id, chunk_index),
    CONSTRAINT session_point_chunks_session_id_fkey
        FOREIGN KEY (session_id) REFERENCES public.walk_session (session_id) ON DELETE CASCADE
);

-- ── walk_review ───────────────────────────────────────────────
-- Added UNIQUE (session_id, reviewer_id) — enforces the business rule that
-- one user can only review a given session once at the DB level.
CREATE TABLE public.walk_review (
    review_id    uuid      NOT NULL DEFAULT uuid_generate_v4(),
    session_id   uuid      NOT NULL,
    reviewer_id  uuid      NOT NULL,
    reviewee_id  uuid      NOT NULL,
    rating_stars integer   NOT NULL CHECK (rating_stars >= 1 AND rating_stars <= 5),
    comment      text,
    created_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT walk_review_pkey                    PRIMARY KEY (review_id),
    CONSTRAINT walk_review_session_reviewer_unique UNIQUE (session_id, reviewer_id),
    CONSTRAINT walk_review_no_self_review          CHECK (reviewer_id <> reviewee_id),
    CONSTRAINT walk_review_session_id_fkey
        FOREIGN KEY (session_id)  REFERENCES public.walk_session   (session_id) ON DELETE CASCADE,
    CONSTRAINT walk_review_reviewer_id_fkey
        FOREIGN KEY (reviewer_id) REFERENCES public.user_account   (user_id)    ON DELETE CASCADE,
    CONSTRAINT walk_review_reviewee_id_fkey
        FOREIGN KEY (reviewee_id) REFERENCES public.user_account   (user_id)    ON DELETE CASCADE
);

-- ── review_tag ────────────────────────────────────────────────
-- FIX: replaced USER-DEFINED tag_type with tag_name varchar
--      (consistent with the profile_tag pattern; no Java enum backing this column).
CREATE TABLE public.review_tag (
    tag_id    uuid    NOT NULL DEFAULT uuid_generate_v4(),
    review_id uuid    NOT NULL,
    tag_name  varchar NOT NULL,
    CONSTRAINT review_tag_pkey PRIMARY KEY (tag_id),
    CONSTRAINT review_tag_review_id_fkey
        FOREIGN KEY (review_id) REFERENCES public.walk_review (review_id) ON DELETE CASCADE
);

-- ── chat_room ─────────────────────────────────────────────────
CREATE TABLE public.chat_room (
    chat_room_id uuid        NOT NULL DEFAULT uuid_generate_v4(),
    session_id   uuid        NOT NULL UNIQUE,
    open_at      timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_at     timestamp,
    status       chat_status NOT NULL DEFAULT 'OPEN',
    CONSTRAINT chat_room_pkey PRIMARY KEY (chat_room_id),
    CONSTRAINT chat_room_session_id_fkey
        FOREIGN KEY (session_id) REFERENCES public.walk_session (session_id) ON DELETE CASCADE
);

-- ── chat_message ──────────────────────────────────────────────
CREATE TABLE public.chat_message (
    message_id   uuid      NOT NULL DEFAULT uuid_generate_v4(),
    chat_room_id uuid      NOT NULL,
    sender_id    uuid      NOT NULL,
    content      text      NOT NULL CHECK (length(trim(content)) > 0),
    created_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at      timestamp,
    CONSTRAINT chat_message_pkey PRIMARY KEY (message_id),
    CONSTRAINT chat_message_chat_room_id_fkey
        FOREIGN KEY (chat_room_id) REFERENCES public.chat_room   (chat_room_id) ON DELETE CASCADE,
    CONSTRAINT chat_message_sender_id_fkey
        FOREIGN KEY (sender_id)    REFERENCES public.user_account (user_id)     ON DELETE CASCADE
);

-- ── session_report ────────────────────────────────────────────
CREATE TABLE public.session_report (
    report_id        uuid          NOT NULL DEFAULT uuid_generate_v4(),
    session_id       uuid,
    reporter_id      uuid          NOT NULL,
    reported_user_id uuid          NOT NULL,
    reason           varchar       NOT NULL,
    evidence_url     text,
    status           report_status NOT NULL DEFAULT 'OPEN',
    created_at       timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT session_report_pkey PRIMARY KEY (report_id),
    CONSTRAINT session_report_session_id_fkey
        FOREIGN KEY (session_id)       REFERENCES public.walk_session   (session_id) ON DELETE SET NULL,
    CONSTRAINT session_report_reporter_id_fkey
        FOREIGN KEY (reporter_id)      REFERENCES public.user_account   (user_id)    ON DELETE CASCADE,
    CONSTRAINT session_report_reported_user_id_fkey
        FOREIGN KEY (reported_user_id) REFERENCES public.user_account   (user_id)    ON DELETE CASCADE
);

-- ── dispute_case ──────────────────────────────────────────────
CREATE TABLE public.dispute_case (
    dispute_id        uuid           NOT NULL DEFAULT uuid_generate_v4(),
    session_id        uuid           NOT NULL,
    opened_by         uuid           NOT NULL,
    status            dispute_status NOT NULL DEFAULT 'OPEN',
    resolution_action text,
    created_at        timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        timestamp      NOT NULL,
    CONSTRAINT dispute_case_pkey PRIMARY KEY (dispute_id),
    CONSTRAINT dispute_case_session_id_fkey
        FOREIGN KEY (session_id) REFERENCES public.walk_session (session_id) ON DELETE CASCADE,
    CONSTRAINT dispute_case_opened_by_fkey
        FOREIGN KEY (opened_by)  REFERENCES public.user_account (user_id)    ON DELETE CASCADE
);

-- ============================================================
-- INDEXES
-- ============================================================

-- user_account — leaderboard query in UserJdbcRepository.findTopByPoints
CREATE INDEX idx_user_account_points_trust  ON public.user_account (total_points DESC, trust_score DESC);

-- walk_intent — primary matching filter (hotspot + status + time window)
CREATE INDEX idx_walk_intent_hotspot_status ON public.walk_intent (hotspot_id, status);
CREATE INDEX idx_walk_intent_user_id        ON public.walk_intent (user_id);
CREATE INDEX idx_walk_intent_expires        ON public.walk_intent (status, expires_at);

-- match_proposal — lookup by intent and status
CREATE INDEX idx_match_proposal_intent_a    ON public.match_proposal (intent_id_a);
CREATE INDEX idx_match_proposal_intent_b    ON public.match_proposal (intent_id_b);
CREATE INDEX idx_match_proposal_status      ON public.match_proposal (status);
CREATE INDEX idx_match_proposal_pending_exp ON public.match_proposal (expires_at)
    WHERE status = 'PENDING';

-- walk_session — overlapping-session check and status polls
CREATE INDEX idx_walk_session_user_id_a       ON public.walk_session (user_id_a);
CREATE INDEX idx_walk_session_user_id_b       ON public.walk_session (user_id_b);
CREATE INDEX idx_walk_session_status          ON public.walk_session (status);
CREATE INDEX idx_walk_session_scheduled_start ON public.walk_session (scheduled_start);
CREATE INDEX idx_walk_session_proposal_id     ON public.walk_session (proposal_id);
-- Used by HotspotJdbcRepository active-walker join
CREATE INDEX idx_walk_session_source_intents  ON public.walk_session (source_intent_id_a, source_intent_id_b);

-- session_state_change_log — audit reads by session
CREATE INDEX idx_session_log_session_id ON public.session_state_change_log (session_id, changed_at DESC);

-- session_point_chunks — ordered reconstruction of GPS path
CREATE INDEX idx_chunks_session_order   ON public.session_point_chunks (session_id, chunk_index ASC);

-- walk_review — reviewee feed
CREATE INDEX idx_walk_review_reviewee   ON public.walk_review (reviewee_id, created_at DESC);
CREATE INDEX idx_walk_review_session    ON public.walk_review (session_id);

-- notification — user inbox and unread count
CREATE INDEX idx_notification_user_id  ON public.notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unread   ON public.notification (user_id, status)
    WHERE status = 'PENDING';

-- follow / block — social graph traversal (getBlockedAndBlockerIds uses UNION of both arms)
CREATE INDEX idx_follow_follower        ON public.follow_relation (follower_id);
CREATE INDEX idx_follow_followee        ON public.follow_relation (followee_id);
CREATE INDEX idx_block_blocker          ON public.block_relation  (blocker_id);
CREATE INDEX idx_block_blocked          ON public.block_relation  (blocked_id);

-- profile_tag — tag list per user
CREATE INDEX idx_profile_tag_user_id   ON public.profile_tag (user_id);

-- refresh_token — token lookup by user for rotation / revocation
CREATE INDEX idx_refresh_token_user_id ON public.refresh_token (user_id);

-- chat_message — paginated room history
CREATE INDEX idx_chat_message_room     ON public.chat_message (chat_room_id, created_at DESC);

-- user_embedding — HNSW approximate-nearest-neighbour index for cosine similarity.
-- UserEmbeddingJdbcRepository.findKNearestUsers() uses the <=> operator (cosine distance).
-- HNSW outperforms IVFFlat for this dataset size (no training required, better recall).
-- m=16 / ef_construction=64 are conservative defaults; tune upward if recall drops.
CREATE INDEX idx_user_embedding_hnsw
    ON public.user_embedding
    USING hnsw (vector_data vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ── Spatial columns note (PostGIS) ───────────────────────────────────────────
-- hotspot.lat/lng and walk_session.meeting_point_lat/lng are stored as
-- double precision pairs.  The current matching model is hotspot-ID–scoped
-- (UUID equality join); no radius queries exist yet.  When "find nearby
-- hotspots / sessions" features are needed, add a targeted migration:
--
--   ALTER TABLE public.hotspot
--       ADD COLUMN location geometry(Point, 4326)
--           GENERATED ALWAYS AS (ST_MakePoint(lng, lat)) STORED;
--   CREATE INDEX idx_hotspot_location ON public.hotspot USING gist(location);
--
-- Introducing PostGIS before those queries exist is YAGNI: extra extension
-- management, WKT/WKB parsing in Java, zero benefit until the feature ships.

-- ============================================================
-- FUNCTIONS & TRIGGERS
-- ============================================================

-- Generic helper: bump updated_at on any UPDATE
CREATE OR REPLACE FUNCTION public.trg_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

-- Fires on user_profile UPDATE to keep updated_at current
CREATE TRIGGER trg_user_profile_updated_at
    BEFORE UPDATE ON public.user_profile
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_set_updated_at();
