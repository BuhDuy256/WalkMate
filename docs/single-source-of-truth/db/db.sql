-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.badge (
  badge_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  name character varying NOT NULL UNIQUE,
  description text,
  icon_url text,
  condition_type USER-DEFINED NOT NULL,
  condition_value integer NOT NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT badge_pkey PRIMARY KEY (badge_id)
);
CREATE TABLE public.block_relation (
  block_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  blocker_id uuid NOT NULL,
  blocked_id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT block_relation_pkey PRIMARY KEY (block_id),
  CONSTRAINT block_relation_blocker_id_fkey FOREIGN KEY (blocker_id) REFERENCES public.user_account(user_id),
  CONSTRAINT block_relation_blocked_id_fkey FOREIGN KEY (blocked_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.chat_message (
  message_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  chat_room_id uuid NOT NULL,
  sender_id uuid NOT NULL,
  content text NOT NULL CHECK (length(TRIM(BOTH FROM content)) > 0),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at timestamp without time zone,
  CONSTRAINT chat_message_pkey PRIMARY KEY (message_id),
  CONSTRAINT chat_message_chat_room_id_fkey FOREIGN KEY (chat_room_id) REFERENCES public.chat_room(chat_room_id),
  CONSTRAINT chat_message_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.chat_room (
  chat_room_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL UNIQUE,
  open_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  close_at timestamp without time zone,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::chat_status,
  CONSTRAINT chat_room_pkey PRIMARY KEY (chat_room_id),
  CONSTRAINT chat_room_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id)
);
CREATE TABLE public.dispute_case (
  dispute_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  opened_by uuid NOT NULL,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::dispute_status,
  resolution_action text,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp without time zone NOT NULL,
  CONSTRAINT dispute_case_pkey PRIMARY KEY (dispute_id),
  CONSTRAINT dispute_case_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT dispute_case_opened_by_fkey FOREIGN KEY (opened_by) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.flyway_schema_history (
  installed_rank integer NOT NULL,
  version character varying,
  description character varying NOT NULL,
  type character varying NOT NULL,
  script character varying NOT NULL,
  checksum integer,
  installed_by character varying NOT NULL,
  installed_on timestamp without time zone NOT NULL DEFAULT now(),
  execution_time integer NOT NULL,
  success boolean NOT NULL,
  CONSTRAINT flyway_schema_history_pkey PRIMARY KEY (installed_rank)
);
CREATE TABLE public.follow_relation (
  follow_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  follower_id uuid NOT NULL,
  followee_id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT follow_relation_pkey PRIMARY KEY (follow_id),
  CONSTRAINT follow_relation_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES public.user_account(user_id),
  CONSTRAINT follow_relation_followee_id_fkey FOREIGN KEY (followee_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.match_proposal (
  proposal_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  intent_id_a uuid NOT NULL,
  intent_id_b uuid NOT NULL,
  proposed_start_time timestamp without time zone NOT NULL,
  proposed_end_time timestamp without time zone NOT NULL,
  proposed_location USER-DEFINED NOT NULL,
  proposed_location_lat double precision NOT NULL,
  proposed_location_lng double precision NOT NULL,
  accepted_by_a boolean NOT NULL DEFAULT false,
  accepted_by_b boolean NOT NULL DEFAULT false,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::proposal_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp without time zone NOT NULL,
  confirmed_at timestamp without time zone,
  CONSTRAINT match_proposal_pkey PRIMARY KEY (proposal_id),
  CONSTRAINT match_proposal_intent_id_a_fkey FOREIGN KEY (intent_id_a) REFERENCES public.walk_intent(intent_id),
  CONSTRAINT match_proposal_intent_id_b_fkey FOREIGN KEY (intent_id_b) REFERENCES public.walk_intent(intent_id)
);
CREATE TABLE public.matching_preference_model (
  user_id uuid NOT NULL,
  weight_time_overlap double precision DEFAULT 1.0,
  weight_interest double precision DEFAULT 1.0,
  weight_behavior double precision DEFAULT 1.0,
  weight_distance double precision DEFAULT 1.0,
  last_trained_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT matching_preference_model_pkey PRIMARY KEY (user_id),
  CONSTRAINT matching_preference_model_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.notification (
  notification_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  type character varying NOT NULL,
  payload jsonb,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::notification_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT notification_pkey PRIMARY KEY (notification_id),
  CONSTRAINT notification_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.profile_tag (
  tag_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  tag_type USER-DEFINED NOT NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT profile_tag_pkey PRIMARY KEY (tag_id),
  CONSTRAINT profile_tag_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_profile(user_id)
);
CREATE TABLE public.refresh_token (
  token_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  token_value text NOT NULL UNIQUE,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT refresh_token_pkey PRIMARY KEY (token_id),
  CONSTRAINT refresh_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.review_tag (
  tag_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  review_id uuid NOT NULL,
  tag_type USER-DEFINED NOT NULL,
  CONSTRAINT review_tag_pkey PRIMARY KEY (tag_id),
  CONSTRAINT review_tag_review_id_fkey FOREIGN KEY (review_id) REFERENCES public.walk_review(review_id)
);
CREATE TABLE public.session_point_chunks (
  chunk_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  chunk_index integer NOT NULL CHECK (chunk_index >= 0),
  polyline text NOT NULL,
  timestamps bytea,
  elevations bytea,
  point_count integer NOT NULL CHECK (point_count > 0),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT session_point_chunks_pkey PRIMARY KEY (chunk_id),
  CONSTRAINT session_point_chunks_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id)
);
CREATE TABLE public.session_report (
  report_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid,
  reporter_id uuid NOT NULL,
  reported_user_id uuid NOT NULL,
  reason character varying NOT NULL,
  evidence_url text,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::report_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT session_report_pkey PRIMARY KEY (report_id),
  CONSTRAINT session_report_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.user_account(user_id),
  CONSTRAINT session_report_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT session_report_reported_user_id_fkey FOREIGN KEY (reported_user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.session_state_change_log (
  log_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  from_status USER-DEFINED,
  to_status USER-DEFINED NOT NULL,
  changed_by uuid,
  reason text,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT session_state_change_log_pkey PRIMARY KEY (log_id),
  CONSTRAINT session_state_change_log_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT session_state_change_log_changed_by_fkey FOREIGN KEY (changed_by) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.spatial_ref_sys (
  srid integer NOT NULL CHECK (srid > 0 AND srid <= 998999),
  auth_name character varying,
  auth_srid integer,
  srtext character varying,
  proj4text character varying,
  CONSTRAINT spatial_ref_sys_pkey PRIMARY KEY (srid)
);
CREATE TABLE public.test_table (
  id uuid NOT NULL,
  name character varying NOT NULL,
  CONSTRAINT test_table_pkey PRIMARY KEY (id)
);
CREATE TABLE public.trust_score (
  user_id uuid NOT NULL,
  score integer NOT NULL DEFAULT 100 CHECK (score >= 0 AND score <= 1000),
  total_sessions integer DEFAULT 0,
  completed_sessions integer DEFAULT 0,
  cancelled_sessions integer DEFAULT 0,
  no_show_sessions integer DEFAULT 0,
  aborted_sessions integer DEFAULT 0,
  last_updated timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT trust_score_pkey PRIMARY KEY (user_id),
  CONSTRAINT trust_score_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.user_account (
  user_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  email character varying NOT NULL UNIQUE CHECK (email::text ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'::text),
  phone character varying UNIQUE,
  password_hash text,
  provider USER-DEFINED NOT NULL DEFAULT 'LOCAL'::auth_provider,
  status USER-DEFINED NOT NULL DEFAULT 'ACTIVE'::account_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at timestamp without time zone,
  CONSTRAINT user_account_pkey PRIMARY KEY (user_id)
);
CREATE TABLE public.user_badge (
  user_id uuid NOT NULL,
  badge_id uuid NOT NULL,
  earned_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_badge_pkey PRIMARY KEY (user_id, badge_id),
  CONSTRAINT user_badge_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id),
  CONSTRAINT user_badge_badge_id_fkey FOREIGN KEY (badge_id) REFERENCES public.badge(badge_id)
);
CREATE TABLE public.user_embedding (
  user_id uuid NOT NULL,
  vector_data ARRAY NOT NULL CHECK (array_length(vector_data, 1) > 0),
  last_updated timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_embedding_pkey PRIMARY KEY (user_id),
  CONSTRAINT user_embedding_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.user_presence (
  user_id uuid NOT NULL,
  status USER-DEFINED NOT NULL DEFAULT 'OFFLINE'::presence_status,
  availability USER-DEFINED NOT NULL DEFAULT 'UNAVAILABLE'::presence_availability,
  quick_mode boolean NOT NULL DEFAULT false,
  last_active_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp without time zone,
  CONSTRAINT user_presence_pkey PRIMARY KEY (user_id),
  CONSTRAINT user_presence_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.user_profile (
  user_id uuid NOT NULL,
  full_name character varying NOT NULL,
  gender USER-DEFINED,
  date_of_birth date CHECK (date_of_birth IS NULL OR date_of_birth < (CURRENT_DATE - '13 years'::interval)),
  avatar_url text,
  bio text,
  search_radius integer DEFAULT 5000 CHECK (search_radius > 0 AND search_radius <= 50000),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_profile_pkey PRIMARY KEY (user_id),
  CONSTRAINT user_profile_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.walk_intent (
  intent_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  location USER-DEFINED NOT NULL,
  location_lat double precision NOT NULL,
  location_lng double precision NOT NULL,
  time_window_start timestamp without time zone NOT NULL,
  time_window_end timestamp without time zone NOT NULL,
  purpose USER-DEFINED NOT NULL,
  matching_constraints jsonb,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::intent_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp without time zone NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  CONSTRAINT walk_intent_pkey PRIMARY KEY (intent_id),
  CONSTRAINT walk_intent_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.walk_review (
  review_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  reviewer_id uuid NOT NULL,
  reviewee_id uuid NOT NULL,
  rating_stars integer NOT NULL CHECK (rating_stars >= 1 AND rating_stars <= 5),
  comment text,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT walk_review_pkey PRIMARY KEY (review_id),
  CONSTRAINT walk_review_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT walk_review_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_review_reviewee_id_fkey FOREIGN KEY (reviewee_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.walk_session (
  session_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user1_id uuid NOT NULL,
  user2_id uuid NOT NULL,
  scheduled_start_time timestamp without time zone NOT NULL,
  scheduled_end_time timestamp without time zone NOT NULL,
  user1_activated_at timestamp without time zone,
  user2_activated_at timestamp without time zone,
  actual_start_time timestamp without time zone,
  actual_end_time timestamp without time zone,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::session_status CHECK (status = ANY (ARRAY['PENDING'::session_status, 'ACTIVE'::session_status, 'COMPLETED'::session_status, 'NO_SHOW'::session_status, 'CANCELLED'::session_status, 'ABORTED'::session_status])),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  source_intent_id_a uuid,
  source_intent_id_b uuid,
  cancellation_reason character varying,
  cancelled_by uuid,
  total_distance numeric NOT NULL DEFAULT 0 CHECK (total_distance >= 0::numeric),
  total_duration bigint NOT NULL DEFAULT 0 CHECK (total_duration >= 0),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  abort_reason character varying CHECK (abort_reason IS NULL OR (abort_reason::text = ANY (ARRAY['INJURY'::character varying, 'SAFETY'::character varying, 'ENVIRONMENT'::character varying, 'OTHER'::character varying]::text[]))),
  CONSTRAINT walk_session_pkey PRIMARY KEY (session_id),
  CONSTRAINT walk_session_user1_id_fkey FOREIGN KEY (user1_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_session_user2_id_fkey FOREIGN KEY (user2_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_session_source_intent_id_a_fkey FOREIGN KEY (source_intent_id_a) REFERENCES public.walk_intent(intent_id),
  CONSTRAINT walk_session_source_intent_id_b_fkey FOREIGN KEY (source_intent_id_b) REFERENCES public.walk_intent(intent_id),
  CONSTRAINT walk_session_cancelled_by_fkey FOREIGN KEY (cancelled_by) REFERENCES public.user_account(user_id)
);
