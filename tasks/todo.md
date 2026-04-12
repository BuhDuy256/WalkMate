# WalkMate — Backend Integration Test Plan

**Author:** Staff Engineer Review
**Date:** 2026-04-12
**Branch:** `merge/oauth`
**Stack:** Spring Boot 3.5.x · PostgreSQL (Flyway) · MongoDB · Firebase Admin SDK (FCM)
**Goal:** Define a comprehensive, bite-sized integration test plan covering all 36 Use Cases, all HTTP 400 Domain Exception paths, and all Key Invariants from `appendix.md`.

> **Rule:** Each task is Single Responsibility. Execute one task at a time.
> **No test code is written until a task is started and the plan is approved.**

---

## PHASE 0 — Infrastructure & Test Harness Setup

> These tasks must be completed first. All subsequent test suites depend on this foundation.

### P0-1: Add Testcontainers to `build.gradle` ✅
- [x] Add `testcontainers-bom` (BOM) to dependency management
- [x] Add `org.testcontainers:postgresql` — replaces Supabase/real PG in tests
- [x] Add `org.testcontainers:mongodb` — replaces Atlas in tests
- [x] Add `org.testcontainers:junit-jupiter` — for `@Testcontainers` lifecycle management
- [x] Verify existing unit tests still pass after dependency additions

### P0-2: Create `AbstractIntegrationTest` Base Class ✅
- [x] Annotate with `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc`
- [x] Declare `@Container` static `PostgreSQLContainer` — shared across all test classes (singleton pattern) — uses `pgvector/pgvector:pg16` image (not `postgres`) due to V1 migration requiring pgvector extension
- [x] Declare `@Container` static `MongoDBContainer` — shared singleton
- [x] Override `spring.datasource.url`, `spring.data.mongodb.uri` via `@DynamicPropertySource`
- [x] Ensure Flyway migrations run automatically on container startup
- [x] Add `@BeforeEach` teardown that truncates all non-flyway tables (use `TRUNCATE ... RESTART IDENTITY CASCADE`) to guarantee test isolation
- [x] `@MockitoBean` for `FirebaseApp`, `FirebaseAuth`, `FirebaseMessaging` — prevents `FirebaseConfig` from attempting real credential loading (prerequisite for context load; P0-4 formalises this)
- [x] Smoke test `AbstractIntegrationTestSmokeTest` written — awaiting Docker Desktop to verify

### P0-3: Create `AuthTokenFactory` Test Helper ✅
- [x] Write a utility that registers a user via `POST /api/v1/auth/register` + logs in via `POST /api/v1/auth/login` and returns a `Bearer` token string
- [x] Add `createAndLoginUser(String email, String password)` convenience method
- [x] Add `createAndLoginUserWithProfile(...)` that also calls `PUT /api/v1/profile/me` to seed profile data
- [x] Covers: UC-01 and UC-02 as a side effect

### P0-4: Create `MockFirebaseConfig` for OAuth & FCM ✅
- [x] Replaced `FirebaseTokenVerifier` with `@MockitoBean protected GoogleTokenVerifier googleTokenVerifier` in `AbstractIntegrationTest` — no @TestConfiguration needed; @MockitoBean intercepts at bean-definition level
- [x] `FirebaseMessaging` already mocked; changed visibility to `protected` so tests can assert invocations
- [x] Documented: FCM verified via `verify(firebaseMessaging, times(N)).send(...)` — not real delivery
- [x] `P04MockFirebaseAcceptanceTest` (3 tests) — context loads, stub pattern verified, FCM mock accessible

### P0-5: Create `TestDataSeeder` Helper ✅
- [x] `seedHotspot(name, lat, lng)` + `seedHotspot()` convenience overload — returns generated UUID; needed by all Intent tests
- [x] `seedAcceptedFriendship(requesterUserId, addresseeUserId)` — inserts into `friendship` table (replaced `follow_relation` in V104) with status ACCEPTED
- [x] `rewindSessionStartedAt(sessionId, Duration)` — UPDATE started_at = now() - N seconds
- [x] `expireProposal(proposalId)` — UPDATE expires_at = now() - 1 second
- [x] `TestDataSeederTest` (5 tests) — all methods acceptance-tested; `rewindSessionStartedAt` and `expireProposal` use minimal raw JDBC chain setup

### P0-6: Verify API Response Envelope Shape (Contract Smoke Test) ✅
- [x] `GET /api/v1/profile/me` without token → HTTP 401 (Spring Security gate)
- [x] `POST /api/v1/auth/login` with wrong credentials → HTTP 400 DomainException; verified `{ success: false, data: null, error: { code, message }, timestamp }` envelope
- [x] `POST /api/v1/auth/register` with blank `fullname` → HTTP 422; `error.code = VALIDATION_ERROR`, `error.message` is a String containing `"field: reason"` format
- [x] Multi-field validation error → `error.message` is a single comma-separated string, not a JSON array

---

## PHASE 1 — Auth & Profile Test Suite (UC-01 to UC-13)

### T01-1: UC-01 Register Account — Happy Path
- [ ] `POST /api/v1/auth/register` with valid payload → assert `201 Created`, `data.email` present

### T01-2: UC-01 Register Account — Duplicate Email
- [ ] Register same email twice → second call returns HTTP **400**, `error.code = USER_ALREADY_EXISTS`

### T01-3: UC-01 Register Account — Validation Errors (UC-01 / Appendix A)
- [ ] Send blank `fullname` → assert HTTP **422**, `error.code = VALIDATION_ERROR`
- [ ] Assert `error.message` is a comma-separated `field: reason` string, not an array

### T02-1: UC-02 Login — Happy Path
- [ ] `POST /api/v1/auth/login` with correct credentials → assert `200 OK`, `data.accessToken` non-null, `tokenType = Bearer`

### T02-2: UC-02 Login — Wrong Credentials
- [ ] Login with wrong password → HTTP **400**, `error.code = USER_INVALID_CREDENTIALS`

### T03-1: UC-03 View My Profile — Authenticated
- [ ] `GET /api/v1/profile/me` with valid token → `200 OK`, profile fields present

### T03-2: UC-03 View My Profile — Unauthenticated
- [ ] Call without token → HTTP **401** (Spring Security, not a domain error)

### T04-1: UC-04 Edit My Profile — Happy Path
- [ ] `PUT /api/v1/profile/me` with valid payload → `200 OK`, returned DTO reflects changes

### T04-2: UC-04 Edit My Profile — Validation Errors
- [ ] Send `bio` > 500 chars → HTTP **422**, `error.code = VALIDATION_ERROR`
- [ ] Send `tags` with 11 items → HTTP **422**

### T05-1: UC-05 Upload Avatar — Happy Path
- [ ] `POST /api/v1/profile/avatar` with valid image bytes as `multipart/form-data` → `200 OK`, `data.avatarUrl` non-null

### T06-1: UC-06 Register FCM Token — Happy Path
- [ ] `PATCH /api/v1/users/me/fcm-token` with valid token string → `200 OK`, `data = null`

### T07-1: UC-07 Google OAuth — New User Registration
- [ ] Stub `FirebaseTokenVerifier.verify()` to return a mock `FirebaseToken` with email + name + sub
- [ ] `POST /api/v1/auth/google` → `200 OK`, `data.accessToken` present, user record created in DB

### T07-2: UC-07 Google OAuth — Existing LOCAL User Account Linking
- [ ] Register a LOCAL user first; then call `POST /api/v1/auth/google` with same email
- [ ] Assert response is `200 OK` (merged), no duplicate user created, `password_hash` preserved in DB

### T07-3: UC-07 Google OAuth — Invalid Firebase Token
- [ ] Stub `FirebaseTokenVerifier.verify()` to throw `FirebaseAuthException`
- [ ] Assert HTTP **400**, `error.code = GOOGLE_LOGIN_FAILED`

### T08-1: UC-08 Phone OTP — Send OTP Happy Path
- [ ] `POST /api/v1/auth/phone/send-otp` with valid E.164 phone number → `200 OK`, `data = null`
- [ ] Assert the OTP record was created in the DB

### T08-2: UC-08 Phone OTP — Invalid Phone Format
- [ ] Send phone not in E.164 format → HTTP **400**, `error.code = INVALID_USER_DATA`

### T09-1: UC-09 Phone OTP — Verify OTP Happy Path
- [ ] Seed an OTP record directly in the DB; call `POST /api/v1/auth/phone/verify` with correct code
- [ ] Assert `200 OK`, `data.accessToken` present, `data.refreshToken` present

### T09-2: UC-09 Phone OTP — Wrong OTP Code
- [ ] Call verify with wrong 6-digit code → HTTP **400**, `error.code = USER_OTP_INVALID`

### T10-1: UC-10 Logout (This Device)
- [ ] Login to get a refresh token; call `POST /api/v1/auth/logout` with `deviceId`
- [ ] Assert `200 OK`; assert the refresh token row for that device is marked invalid/deleted in DB

### T11-1: UC-11 Logout All Devices
- [ ] Login from two simulated `deviceId`s; call `POST /api/v1/auth/logout-all`
- [ ] Assert `200 OK`; assert **all** refresh token rows for that user are invalidated in DB

### T12-1: UC-12 Silent Token Refresh — Happy Path
- [ ] Issue a refresh token; call `POST /api/v1/auth/refresh` with it
- [ ] Assert `200 OK`, new `accessToken` and `refreshToken` returned, old refresh token invalidated (rotation)

### T12-2: UC-12 Silent Token Refresh — Revoked Token
- [ ] Call `/auth/refresh` with an already-invalidated refresh token → HTTP **401**

### T13-1: UC-13 Set Profile Visibility
- [ ] `PATCH /api/v1/users/me/visibility` with `"visibility": "PRIVATE"` → `200 OK`
- [ ] Verify DB row reflects `PRIVATE`
- [ ] Toggle back to `PUBLIC` → `200 OK`, DB reflects `PUBLIC`

---

## PHASE 2 — Discovery Test Suite (UC-07 Hotspots)

### T14-1: UC-07 Browse Hotspot Map — Unauthenticated (Public Endpoint)
- [ ] Seed two hotspot rows; `GET /api/v1/hotspots` without a token → `200 OK`, list of hotspots returned
- [ ] Assert `openIntentCount` field is present on each hotspot

### T14-2: UC-07 Get Single Hotspot — Not Found
- [ ] `GET /api/v1/hotspots/{nonExistentId}` → HTTP **400**, `error.code = HOTSPOT_NOT_FOUND`

---

## PHASE 3 — Walk Intent Test Suite (UC-08 to UC-11)

### T15-1: UC-08 Create Walk Intent — Happy Path
- [ ] Authenticated user calls `POST /api/v1/intents` with valid payload (seeded hotspot)
- [ ] Assert `201 Created`, `data.status = OPEN`, `data.expires_at` non-null

### T15-2: UC-08 Create Intent — Overlapping OPEN Intent (Invariant I-1)
- [ ] Create a first intent for time window `[17:00, 18:00]`
- [ ] Create a second overlapping intent `[17:30, 19:00]` for the same user
- [ ] Assert HTTP **400**, `error.code = INTENT_OVERLAPPING`

### T15-3: UC-08 Create Intent — Overlapping PENDING Session (Invariant I-1)
- [ ] Seed a `PENDING` WalkSession in the same time window for the user
- [ ] Attempt to create an intent overlapping that window
- [ ] Assert HTTP **400**, `error.code = INTENT_OVERLAPPING_SESSION`

### T15-4: UC-08 Create Intent — Invalid Time Range
- [ ] Send `time_start >= time_end` → HTTP **400**, `error.code = INVALID_TIME_RANGE`

### T15-5: UC-08 Create Intent — Invalid Age Range
- [ ] Send `age_min > age_max` → HTTP **400**, `error.code = INVALID_AGE_RANGE`

### T15-6: UC-08 Create Private Intent — Friend Not Accepted (Invariant I-7)
- [ ] Send `is_private = true` with `invited_friend_id` pointing to a non-friend user
- [ ] Assert HTTP **400**, `error.code = INTENT_PRIVATE_FRIEND_NOT_ACCEPTED`

### T16-1: UC-09 View My Active Intents
- [ ] Create two `OPEN` intents; `GET /api/v1/intents` → `200 OK`, list contains both
- [ ] Assert only `OPEN`/`MATCHING` statuses appear (no `CANCELLED`/`EXPIRED` in response)

### T17-1: UC-10 Cancel Walk Intent — Happy Path
- [ ] Create an `OPEN` intent; `DELETE /api/v1/intents/{intentId}` → `200 OK`
- [ ] Assert intent status in DB is `CANCELLED`
- [ ] Assert the same user can now create a new intent in the same time window (overlap lock released)

### T17-2: UC-10 Cancel Intent — Not OPEN (Invariant I-4 / I-6)
- [ ] Seed a `MATCHING` intent; call `DELETE /api/v1/intents/{intentId}`
- [ ] Assert HTTP **400**, `error.code = INTENT_NOT_OPEN`

### T17-3: UC-10 Cancel Intent — Not Owner
- [ ] Create intent as User A; attempt to delete as User B
- [ ] Assert HTTP **400**, `error.code = INTENT_NOT_OWNER`

### T18-1: UC-11 Trigger Match — No Match Found (204 No Content)
- [ ] Create an `OPEN` intent with no compatible counterpart in DB
- [ ] `POST /api/v1/intents/{intentId}/match` → `204 No Content`
- [ ] Intent remains `OPEN` in DB

### T18-2: UC-11 Trigger Match — Match Found (200 OK with Proposal)
- [ ] Seed two compatible `OPEN` intents (User A and User B, same hotspot, overlapping time)
- [ ] Trigger match for User A's intent → `200 OK`, `data.status = PENDING`
- [ ] Assert User A's intent status changed to `MATCHING` in DB (Invariant I-4)
- [ ] Assert a `MatchProposal` row with `PENDING` status exists in DB

### T18-3: UC-11 Trigger Match — Intent Not OPEN (Invariant I-4)
- [ ] Seed a `MATCHING` intent; trigger match again → HTTP **400**, `error.code = INVALID_INTENT_DATA`

---

## PHASE 4 — Proposal Negotiation Test Suite (UC-12 to UC-15)

### T19-1: UC-12 View Incoming Proposals
- [ ] Seed a `PENDING` proposal for User A; `GET /api/v1/proposals` as User A → `200 OK`, proposal in list
- [ ] Assert `expires_at` is present on each proposal

### T20-1: UC-13 Accept Proposal — Partial (Only One User Accepts)
- [ ] Seed a `PENDING` proposal; User A calls `POST /api/v1/proposals/{proposalId}/accept`
- [ ] Assert `200 OK`, `data.status = PENDING` (partner has not yet accepted — Invariant P-2)
- [ ] Assert no `WalkSession` row created yet

### T20-2: UC-13 Accept Proposal — Both Accept → Session Created (Invariants P-2, P-3, I-3)
- [ ] Seed a `PENDING` proposal; User A accepts, then User B accepts
- [ ] Second acceptance returns `200 OK`, `data.status = CONFIRMED`, `data.session_id` non-null
- [ ] Assert a `WalkSession` in `PENDING` status exists in DB
- [ ] Assert **both** intents are now `CONSUMED` in DB (Invariant I-3 — terminal, immutable)
- [ ] Assert a MongoDB chat room document exists keyed by `session_id`

### T20-3: UC-13 Accept Proposal — Proposal Already Terminal (Invariant I-6)
- [ ] Seed an `EXPIRED` proposal; call accept → HTTP **400**, `error.code = PROPOSAL_ALREADY_TERMINAL`

### T20-4: UC-13 Accept Proposal — Intent No Longer MATCHING
- [ ] Seed a proposal where one intent was concurrently cancelled; call accept
- [ ] Assert HTTP **400**, `error.code = PROPOSAL_INTENT_NO_LONGER_OPEN`

### T20-5: UC-13 Accept Proposal — Concurrent Modification (Invariant X-5)
- [ ] Use two threads to simulate simultaneous acceptance by both users
- [ ] Assert exactly one thread receives `CONFIRMED`; the other may receive `PROPOSAL_CONCURRENT_MODIFICATION`
- [ ] Assert no duplicate `WalkSession` rows created (atomicity check)

### T21-1: UC-14 Pass Proposal — Happy Path (Invariant X-3)
- [ ] Seed a `PENDING` proposal; User A calls `POST /api/v1/proposals/{proposalId}/pass`
- [ ] Assert `200 OK`; proposal moves to `REJECTED` in DB
- [ ] Assert **both** intents revert to `OPEN` in DB
- [ ] Assert the exclude list is updated (User B should not appear in next match for User A's intent)

### T21-2: UC-14 Pass Proposal — Already Terminal
- [ ] Pass on a `REJECTED` proposal → HTTP **400**, `error.code = PROPOSAL_ALREADY_TERMINAL`

### T22-1: UC-15 Cancel Proposal (Withdraw Intent) — Happy Path
- [ ] Seed a `PENDING` proposal; User A calls `DELETE /api/v1/proposals/{proposalId}`
- [ ] Assert `200 OK`; User A's intent moves to `CANCELLED` (terminal — Invariant I-6) in DB
- [ ] Assert User B's intent reverts to `OPEN` in DB (eligible for re-matching)

### T22-2: UC-15 Cancel Proposal — Not Participant
- [ ] Seed a proposal between User A and User B; User C calls delete
- [ ] Assert HTTP **400**, `error.code = PROPOSAL_NOT_PARTICIPANT`

---

## PHASE 5 — Session Lifecycle Test Suite (UC-16 to UC-20)

### T23-1: UC-16 View Active Sessions
- [ ] Seed one `PENDING` and one `ACTIVE` session for User A; `GET /api/v1/sessions/active`
- [ ] Assert both sessions are in the response; no terminal-state sessions included

### T24-1: UC-17 Activate Session — Partial Activation (Invariant S-2)
- [ ] Seed a `PENDING` session within activation window; User A calls `POST /api/v1/sessions/{id}/activate`
- [ ] Assert `200 OK`, `data.status = PENDING` (User B not yet activated)
- [ ] Assert `user_a_activated_at` is set in DB; `started_at` remains null

### T24-2: UC-17 Activate Session — Mutual Activation → ACTIVE (Invariants S-2, S-3)
- [ ] User A and User B both activate within the window
- [ ] Second activation returns `200 OK`, `data.status = ACTIVE`
- [ ] Assert `started_at` is set in DB
- [ ] Assert session is now queryable via `GET /api/v1/sessions/active` with `ACTIVE` status

### T24-3: UC-17 Activate Session — Outside Activation Window (Invariant S-3)
- [ ] Seed a `PENDING` session with `scheduled_start` more than 15 minutes in the past
- [ ] Call activate → HTTP **400**, `error.code = SESSION_ACTIVATION_WINDOW_CLOSED`

### T24-4: UC-17 Activate Session — Session Not PENDING
- [ ] Call activate on an `ACTIVE` session → HTTP **400**, `error.code = SESSION_NOT_PENDING`

### T25-1: UC-18 Cancel Pending Session — Happy Path
- [ ] Seed a `PENDING` session; call `POST /api/v1/sessions/{id}/cancel` with a reason
- [ ] Assert `200 OK`; session moves to `CANCELLED` (terminal) in DB

### T25-2: UC-18 Cancel Session — Session Is ACTIVE
- [ ] Call cancel on an `ACTIVE` session → HTTP **400**, `error.code = SESSION_CANCEL_NOT_PENDING`

### T25-3: UC-18 Cancel Session — Empty Reason
- [ ] Call cancel with empty `reason` field → HTTP **422**, `error.code = VALIDATION_ERROR`

### T26-1: UC-19 Complete Walk Session — Happy Path (Invariant S-5)
- [ ] Seed an `ACTIVE` session; use `TestDataSeeder` to set `started_at` to 6 minutes ago
- [ ] Call `POST /api/v1/sessions/{id}/complete` → `200 OK`, `data.status = COMPLETED`
- [ ] Assert session is terminal in DB; chat write access is revoked (Invariant S-7)

### T26-2: UC-19 Complete Walk Session — Too Early (Invariant S-5)
- [ ] Seed an `ACTIVE` session with `started_at` set to 2 minutes ago
- [ ] Call complete → HTTP **400**, `error.code = SESSION_COMPLETE_TOO_EARLY`

### T26-3: UC-19 Complete Walk Session — Session Not ACTIVE
- [ ] Call complete on a `PENDING` session → HTTP **400**, `error.code = SESSION_NOT_ACTIVE`

### T27-1: UC-20 Abort Active Session — Happy Path
- [ ] Seed an `ACTIVE` session; call `POST /api/v1/sessions/{id}/abort` with `reason: "SAFETY_CONCERN"`
- [ ] Assert `200 OK`; session moves to `ABORTED` (terminal) in DB
- [ ] Assert `SessionAbortedEvent` is published (verify via mock ApplicationEventPublisher or DB side-effects)

### T27-2: UC-20 Abort Session — Session Not ACTIVE
- [ ] Call abort on a `PENDING` session → HTTP **400**, `error.code = SESSION_NOT_ACTIVE`

### T27-3: UC-20 Abort Session — Invalid Reason Enum
- [ ] Call abort with `reason: "INVALID_REASON"` → HTTP **422**, `error.code = VALIDATION_ERROR`

---

## PHASE 6 — GPS Tracking Test Suite (UC-21)

### T28-1: UC-21 GPS Route Sync — Happy Path
- [ ] Seed an `ACTIVE` session; call `POST /api/v1/tracking/sync` with 5 GPS points
- [ ] Assert `200 OK`, `acknowledged_ids` contains all 5 `local_id`s
- [ ] Assert GPS point documents exist in MongoDB for this session

### T28-2: UC-21 GPS Route Sync — Session No Longer Active (Invariant S-6)
- [ ] Seed a `COMPLETED` session; call sync → HTTP **400**, `error.code = SESSION_NOT_ACTIVE`

### T28-3: UC-21 GPS Route Sync — Invalid Coordinates
- [ ] Submit a point with `lat = 999.0` (out of bounds) → HTTP **400**, `error.code = INVALID_ARGUMENT`

---

## PHASE 7 — Post-Session Test Suite (UC-22 to UC-25)

### T29-1: UC-22 View Session History
- [ ] Seed one `COMPLETED` and one `ABORTED` session; `GET /api/v1/sessions/history`
- [ ] Assert both appear; `COMPLETED` has `total_distance_km` and `duration_minutes`; `ABORTED` shows `"—"` equivalent

### T30-1: UC-23 View Session Route Replay — Happy Path
- [ ] Seed GPS points for a `COMPLETED` session; `GET /api/v1/sessions/{id}/route`
- [ ] Assert `200 OK`, `user_a_polylines` and `user_b_polylines` present

### T30-2: UC-23 Route Replay — Session Not Finished
- [ ] Call route on an `ACTIVE` session → HTTP **400**, `error.code = SESSION_NOT_FINISHED`

### T30-3: UC-23 Route Replay — No GPS Data (Cancelled Early)
- [ ] Seed a `CANCELLED` session with no GPS points; call route
- [ ] Assert `200 OK`, empty polylines array (not an error)

### T31-1: UC-24 Submit Review — Happy Path
- [ ] Seed a `COMPLETED` session; User A calls `POST /api/v1/sessions/{id}/review` with `rating_stars: 5`
- [ ] Assert `200 OK`; review row exists in DB; reviewee's `trustScore` updated (Invariant X-4)

### T31-2: UC-24 Submit Review — Duplicate Review
- [ ] Submit review twice → second call returns HTTP **400**, `error.code = REVIEW_ALREADY_SUBMITTED`

### T31-3: UC-24 Submit Review — Session Not Completed
- [ ] Submit review on an `ABORTED` session → HTTP **400**, `error.code = REVIEW_SESSION_NOT_COMPLETED`

### T31-4: UC-24 Submit Review — Invalid Rating
- [ ] Submit with `rating_stars: 6` → HTTP **400**, `error.code = REVIEW_INVALID_RATING`

### T32-1: UC-25 Submit Incident Report — Happy Path
- [ ] Seed a `COMPLETED` session; User A calls `POST /api/v1/sessions/{id}/report`
- [ ] Assert `201 Created`, `data.reportId` non-null

### T32-2: UC-25 Submit Report — Window Expired
- [ ] Seed a `COMPLETED` session with `completed_at` = 73 hours ago; call report
- [ ] Assert HTTP **400**, `error.code = REPORT_WINDOW_EXPIRED`

### T32-3: UC-25 Submit Report — Duplicate Report
- [ ] Submit two reports for the same session → HTTP **400**, `error.code = REPORT_ALREADY_SUBMITTED`

### T32-4: UC-25 Submit Report — Non-Reportable Session Status
- [ ] Call report on a `PENDING` session → HTTP **400**, `error.code = REPORT_SESSION_INVALID_STATUS`

---

## PHASE 8 — Social Test Suite (UC-26 to UC-31)

### T33-1: UC-26 View Public User Profile — Authenticated
- [ ] `GET /api/v1/users/{userId}` as an authenticated user → `200 OK`, profile fields present

### T33-2: UC-26 View Public User Profile — Unauthenticated (Public Endpoint)
- [ ] Call without token → `200 OK` (endpoint is public per use case)

### T33-3: UC-26 View User Profile — Not Found
- [ ] Call with non-existent userId → HTTP **400**, `error.code = USER_NOT_FOUND`

### T34-1: UC-27 Follow a User — Happy Path
- [ ] `POST /api/v1/users/{userId}/follow` → `200 OK`
- [ ] Assert follow relationship exists in DB
- [ ] Assert target appears in User A's following list

### T34-2: UC-27 Follow Self
- [ ] Call follow with own userId → HTTP **400**, `error.code = FOLLOW_SELF_FOLLOW_FORBIDDEN`

### T34-3: UC-27 Follow — Already Following (Idempotency)
- [ ] Follow the same user twice → second call returns HTTP **400**, `error.code = FOLLOW_ALREADY_FOLLOWING`

### T35-1: UC-28 Unfollow a User — Happy Path
- [ ] Follow then unfollow; assert relationship removed from DB

### T36-1: UC-29 View Followers / Following Lists
- [ ] User A follows User B; `GET /api/v1/users/{userB}/followers` → User A appears in list
- [ ] `GET /api/v1/users/{userA}/following` → User B appears in list

### T37-1: UC-30 Block a User — Happy Path
- [ ] User A follows User B; User A blocks User B
- [ ] Assert `200 OK`; block relationship in DB
- [ ] Assert pre-existing follow relationships in both directions are removed from DB

### T37-2: UC-30 Block Self
- [ ] Call block with own userId → HTTP **400**, `error.code = BLOCK_SELF_BLOCK_FORBIDDEN`

### T37-3: UC-30 Block — Already Blocked (Idempotency)
- [ ] Block the same user twice → HTTP **400**, `error.code = BLOCK_ALREADY_BLOCKED`

### T38-1: UC-31 Unblock a User — Happy Path
- [ ] Block then unblock; assert relationship removed from DB

---

## PHASE 9 — Notifications Test Suite (UC-32 to UC-33)

### T39-1: UC-32 View Notification Feed
- [ ] Seed two notification rows (one `UNREAD`, one `READ`) for User A
- [ ] `GET /api/v1/notifications` as User A → `200 OK`, both notifications present, ordered newest-first
- [ ] Assert `UNREAD` item has `readAt = null`; `READ` item has `readAt` set

### T40-1: UC-33 Mark Notification as Read — Happy Path
- [ ] Seed an `UNREAD` notification; `POST /api/v1/notifications/{id}/read`
- [ ] Assert `200 OK`; notification status is `READ` in DB, `readAt` is set

### T40-2: UC-33 Mark Notification — Not Owner
- [ ] Seed a notification for User A; User B calls mark-as-read on it
- [ ] Assert HTTP **400**, `error.code = NOTIFICATION_NOT_OWNER`

### T40-3: UC-33 Mark Notification — Not Found
- [ ] Call with non-existent notification ID → HTTP **400**, `error.code = NOTIFICATION_NOT_FOUND`

---

## PHASE 10 — Gamification Test Suite (UC-34 to UC-36)

### T41-1: UC-34 View User Badges — Empty State
- [ ] `GET /api/v1/users/{userId}/badges` for a new user → `200 OK`, empty list (not an error)

### T41-2: UC-34 View User Badges — Populated
- [ ] Seed badge rows for a user; assert they appear in the response with `badgeName` and `awardedAt`

### T42-1: UC-35 View User Stats
- [ ] `GET /api/v1/users/{userId}/stats` → `200 OK`
- [ ] Assert response contains `totalPoints`, `totalDistanceKm`, `completedSessions`, `trustScore`

### T43-1: UC-36 View Leaderboard — Unauthenticated (Public Endpoint)
- [ ] Seed 5 users with varying `totalPoints`; `GET /api/v1/leaderboard` without token
- [ ] Assert `200 OK`; results ordered by `totalPoints` descending; `rank` field starts at 1

---

## PHASE 11 — Invariant Matrix Integration Tests

> These tests focus purely on enforcing the invariants from `appendix.md`. They may involve multi-step setups.

### INV-I1: Overlapping Time Windows — Both Directions
- [ ] Verify `INTENT_OVERLAPPING` when new intent's window **contains** an existing one
- [ ] Verify `INTENT_OVERLAPPING` when new intent's window is **contained by** an existing one
- [ ] Verify `INTENT_OVERLAPPING` when new intent's window **partially overlaps** at the start
- [ ] Verify **no** overlap error when windows are adjacent (e.g., end of first = start of second)

### INV-I3: CONSUMED Intent Is Immutable
- [ ] After double-accept (both intents become `CONSUMED`), attempt to cancel either intent
- [ ] Assert HTTP **400**, `error.code = INTENT_NOT_OPEN` (cannot cancel a CONSUMED intent)

### INV-I4: MATCHING State Locks Intent
- [ ] Set an intent to `MATCHING` (via trigger match); attempt `DELETE /api/v1/intents/{id}`
- [ ] Assert HTTP **400**, `error.code = INTENT_NOT_OPEN` — the cancel button must be locked

### INV-I6: Terminal States Are Immutable
- [ ] For a `COMPLETED` session: attempt `POST .../activate`, `.../cancel`, `.../abort`
- [ ] Each attempt → HTTP **400** with the appropriate domain error code
- [ ] For a `CANCELLED` intent: attempt to cancel again → HTTP **400**

### INV-I7: Private Intent Requires Accepted Friendship
- [ ] User A and User B have a **pending** (not accepted) follow request
- [ ] User A creates a private intent targeting User B → HTTP **400**, `error.code = INTENT_PRIVATE_FRIEND_NOT_ACCEPTED`
- [ ] Accept the follow; retry the private intent → `201 Created`

### INV-P4: Proposal TTL Expiry
- [ ] Create a proposal; use `TestDataSeeder` to set `expires_at` to the past
- [ ] Run the expiry scheduler (or call the scheduled job directly)
- [ ] Assert proposal is `EXPIRED` in DB; associated intents revert to `OPEN`

### INV-S3: Activation Window Boundaries
- [ ] Attempt activation exactly at `scheduledStart − 10 min` → `200 OK` (window just opened)
- [ ] Attempt activation exactly at `scheduledStart + 15 min` → `200 OK` (last valid moment)
- [ ] Attempt activation at `scheduledStart + 16 min` → HTTP **400**, `error.code = SESSION_ACTIVATION_WINDOW_CLOSED`

### INV-S5: 5-Minute Walk Minimum — Boundary
- [ ] Set `started_at` to exactly 4m 59s ago → call complete → `SESSION_COMPLETE_TOO_EARLY`
- [ ] Set `started_at` to exactly 5m 00s ago → call complete → `200 OK`, `COMPLETED`

### INV-X4: Reputation Updated on Session Terminal State
- [ ] Record User B's `trustScore` before a completed session with a 5-star review
- [ ] Complete session + submit review; re-fetch User B's stats
- [ ] Assert `trustScore` has increased

### INV-X5: Optimistic Locking on Proposal Acceptance
- [ ] Simulate two concurrent `accept` calls on the same proposal from both users
- [ ] Assert only one `WalkSession` is created — no duplicate
- [ ] Assert no deadlock or unhandled 500 error — at most one `PROPOSAL_CONCURRENT_MODIFICATION` error

---

## PHASE 12 — End-to-End (E2E) Lifecycle Flows

> These multi-step tests validate the full happy-path lifecycle through the entire system. They are the highest-confidence regression tests.

### E2E-1: Full Happy Path — Register to Completed Session
- [ ] **Step 1:** Register User A and User B (UC-01 × 2)
- [ ] **Step 2:** Login both users, obtain tokens (UC-02 × 2)
- [ ] **Step 3:** Both users seed hotspot; each creates a compatible `OPEN` intent (UC-08 × 2)
- [ ] **Step 4:** User A triggers match → proposal created (UC-11), both intents become `MATCHING` (I-4)
- [ ] **Step 5:** User A accepts proposal → `PENDING` (partial, P-2)
- [ ] **Step 6:** User B accepts proposal → `CONFIRMED`, `WalkSession PENDING` created (P-3), both intents `CONSUMED` (I-3)
- [ ] **Step 7:** Both users activate within window → session becomes `ACTIVE` (S-2)
- [ ] **Step 8:** Advance `started_at` 6 min into the past; User A calls complete → `COMPLETED` (S-5)
- [ ] **Step 9:** User A submits a 5-star review (UC-24); assert User B's `trustScore` updated (X-4)
- [ ] **Final Assert:** Session in `COMPLETED` state; chat is locked (S-7); intents are `CONSUMED` (I-3)

### E2E-2: Proposal Rejection → Intent Reverts and Rematches
- [ ] **Step 1:** User A and User B both have `OPEN` intents; match triggered → proposal created
- [ ] **Step 2:** User A passes the proposal (UC-14)
- [ ] **Step 3:** Assert both intents are back to `OPEN`; exclude list updated (X-3)
- [ ] **Step 4:** Seed User C with a compatible `OPEN` intent
- [ ] **Step 5:** User A triggers match again → User C is matched (User B excluded)
- [ ] **Final Assert:** New proposal exists between User A and User C

### E2E-3: Emergency Abort → Report Window
- [ ] **Step 1:** Two users go through intent → proposal → double-accept → double-activate → `ACTIVE` session
- [ ] **Step 2:** User A calls abort with `reason: "SAFETY_CONCERN"` (UC-20)
- [ ] **Step 3:** Assert session is `ABORTED`; chat is locked (S-7)
- [ ] **Step 4:** User A submits an incident report within 24h window (UC-25) → `201 Created`
- [ ] **Step 5:** Advance time past 24h; User A attempts another report → `REPORT_WINDOW_EXPIRED`

### E2E-4: Intent Withdraw During Active Proposal
- [ ] **Step 1:** User A and User B have a `PENDING` proposal (User A's intent is `MATCHING`)
- [ ] **Step 2:** User A withdraws their intent via UC-15 (`DELETE /api/v1/proposals/{id}`)
- [ ] **Step 3:** Assert User A's intent is `CANCELLED` (terminal); User B's intent reverts to `OPEN`
- [ ] **Step 4:** User A attempts to create a new intent in the same time window → `201 Created` (lock released)
- [ ] **Final Assert:** User B can still trigger a match with a different user

---

## Review Section

> To be filled after all phases are complete.

| Phase | Status | Notes |
|-------|--------|-------|
| P0-1: Testcontainers deps | `[x] Done` | BOM 1.20.4; PG + Mongo + JUnit-Jupiter added; all existing unit tests pass |
| P0-2: AbstractIntegrationTest | `[x] Done` | pgvector image, @MockitoBean Firebase, CASCADE truncate — BUILD SUCCESSFUL 2m 12s |
| P0-3: AuthTokenFactory | `[x] Done` | createAndLoginUser, login, createAndLoginUserWithProfile — 6/6 tests pass |
| P1: Auth & Profile | `[ ] Pending` | — |
| P2: Discovery | `[ ] Pending` | — |
| P3: Walk Intent | `[ ] Pending` | — |
| P4: Proposal | `[ ] Pending` | — |
| P5: Session Lifecycle | `[ ] Pending` | — |
| P6: GPS Tracking | `[ ] Pending` | — |
| P7: Post-Session | `[ ] Pending` | — |
| P8: Social | `[ ] Pending` | — |
| P9: Notifications | `[ ] Pending` | — |
| P10: Gamification | `[ ] Pending` | — |
| P11: Invariants | `[ ] Pending` | — |
| P12: E2E Flows | `[ ] Pending` | — |
