# Proposal: WalkSession Domain + API Implementation (Compliant with BackendDevelopmentGuide)

## 1. Mục tiêu

Triển khai đầy đủ backend cho WalkSession theo thứ tự trong `docs/prompts/duy/4-implement.md`, bám sát SSOT trong:

- `docs/dev/walk-session/domain-lifecycle-statemachine-invariants-stresstest.md`
- `docs/dev/walk-session/use-cases.md`

Và tuân thủ nghiêm `docs/single-source-of-truth/development-guide/BackendDevelopmentGuide.md`.

Scope chức năng:

- WalkSession lifecycle: `PENDING -> ACTIVE -> (COMPLETED | CANCELLED | NO_SHOW | ABORTED)`
- Tracking summary trong `walk_session`: `total_distance`, `total_duration`
- GPS route points trong `session_points`
- API cho user actions + system actions
- Unit tests + integration tests cho invariants và stress scenarios

## 2. Nguyên tắc bắt buộc từ BackendDevelopmentGuide

1. DDD-lite chuẩn layer:

- `domain/` mới được feature subfolder (`domain/session/...`)
- `application/`, `presentation/`, `infrastructure/` phải giữ phang (khong `application/session/...`)

2. Controller mỏng:

- Khong inject repository trong controller
- Controller chi validate request, goi service, map response

3. Domain thuần:

- Khong import Spring/JPA/HTTP trong domain core models
- Logic transition + invariant nằm trong domain methods

4. Transaction boundary:

- Dat tai Application Service (`@Transactional`)
- Khong dat trong controller

5. Response envelope:

- Tra ve `ApiResponse<ResponseDTO>`
- Mapping exception: `DomainException -> 422`, `NotFound -> 404`, `Conflict -> 409`

6. Concurrency:

- Optimistic locking qua `version` column + `@Version`
- Batch/system transitions can use row lock (`SELECT ... FOR UPDATE`) at repository query level

## 3. Thiết kế triển khai theo 5 tầng (theo 4-implement.md)

## 3.1 Core Domain

Package du kien:

- `domain/session/WalkSession.java`
- `domain/session/SessionStatus.java`
- `domain/session/AbortReason.java`
- `domain/session/SessionRepository.java`
- `domain/session/DomainException.java`

Value Objects du kien:

- `domain/valueobject/SessionTrackingStats.java` (distance, duration)
- (optional) `domain/valueobject/SessionPoint.java` cho validation logic phuc tap truoc khi persist

Domain methods (intention-revealing):

- `activate(UUID userId, Instant now)`
- `cancel(UUID userId, String reason, Instant now)`
- `complete(UUID userId, Instant now, SessionTrackingStats stats)`
- `abort(UUID userId, AbortReason reason, Instant now)`
- `systemProcessActivationWindow(Instant now)` => `NO_SHOW`/`CANCELLED`
- `systemForceComplete(Instant now)`

Domain invariants enforced:

- Transition hop le theo state machine
- Activation window valid (`S-3`, `S-4`)
- Min duration de complete (`S-7`)
- Terminal immutability (`S-8`)
- Idempotent for retry flows where applicable

## 3.2 Data / Persistence

Migration:

- Su dung migration update da co:
  - `docs/single-source-of-truth/db/migrations/2026-03-14-update-session-tracking.sql`
- Them migration tiep theo (neu chua co) cho optimistic lock:
  - Add `version BIGINT NOT NULL DEFAULT 0` vao `walk_session`

Repository split:

- Domain interface: `domain/session/SessionRepository.java`
- Infra implementation: `infrastructure/repository/JpaSessionRepository.java`
- JPA entity mapping:
  - `infrastructure/repository/entity/WalkSessionJpaEntity.java`
  - `infrastructure/repository/entity/SessionPointJpaEntity.java`

Queries quan trong:

- `findByIdForUpdate(...)` cho system cron conflict scenario
- `findExpiredPendingSessionsForUpdate(limit)`
- `findExpiredActiveSessionsForUpdate(limit)`

DB defenses:

- `CHECK` constraints (time/distance/duration)
- Unique `(session_id, point_order)` tren `session_points`

## 3.3 Application / Services

Luu y folder phang theo guide (`application/`):

- `ActivateWalkSessionService`
- `CancelWalkSessionService`
- `CompleteWalkSessionService`
- `AbortWalkSessionService`
- `ProcessSessionActivationWindowService` (system)
- `ForceCompleteOverdueSessionService` (system)
- `AppendSessionPointsService` (ghi GPS points)

Pattern cho moi service:

1. Load aggregate tu repository
2. Goi domain method
3. Save aggregate
4. Persist side data (`session_points`) neu co

Transaction:

- Services mutating dat `@Transactional`
- System sweep xu ly theo chunk + lock

## 3.4 Presentation / API

Controllers (`presentation/controller/`):

- `SessionController`
- `SessionTrackingController`
- `SessionSystemController` (neu can endpoint internal/admin; neu cron tu scheduler thi khong expose)

DTOs (`presentation/dto/request`, `presentation/dto/response`):

- Request:
  - `ActivateSessionRequest` (neu can payload)
  - `CancelSessionRequest`
  - `CompleteSessionRequest` (distance, duration)
  - `AbortSessionRequest`
  - `AppendSessionPointsRequest` with `List<RoutePointDTO>`
- Response:
  - `SessionResponse`
  - `SessionTrackingResponse`

Mapper:

- `presentation/mapper/SessionMapper`

API proposal:

- `POST /api/v1/sessions/{id}/activate`
- `POST /api/v1/sessions/{id}/cancel`
- `POST /api/v1/sessions/{id}/complete`
- `POST /api/v1/sessions/{id}/abort`
- `POST /api/v1/sessions/{id}/points:append`
- `GET /api/v1/sessions/{id}`

Security:

- User context lay tu `@AuthenticationPrincipal`, khong trust `userId` trong body

Error mapping:

- DomainException => 422
- ResourceNotFoundException => 404
- ObjectOptimisticLockingFailureException => 409

## 3.5 Tests

Unit tests (Domain):

- Moi transition hop le: 1 test
- Moi transition sai: 1 test
- Moi invariant violation: 1 test
- Idempotency tests cho retry complete/cancel

Integration tests (Application + DB):

- Activate vs cron close-window race
- Cancel vs activate concurrent race (optimistic locking)
- Force complete overdue sessions
- Append route points outside session window -> fail

Repository tests (`@DataJpaTest`):

- Custom queries lock/update
- Constraint assertions cho `session_points`

API tests (MockMvc):

- Status mapping 200/404/409/422
- JSON envelope `ApiResponse`

## 4. Kế hoạch giao hàng (Implementation slices)

Slice 1: Foundation + Migration

- Add migration columns/points/version
- Add JPA entities + repository skeleton
- Add global exception mapping scaffolding

Slice 2: Domain + Core Services

- Implement aggregate methods + domain exceptions
- Implement activate/cancel/complete/abort use cases
- Unit tests cho lifecycle rules

Slice 3: Tracking API

- Implement append points + summary update flow
- Validate session window for points
- API + mapper + contract tests

Slice 4: System workers + Concurrency hardening

- ProcessActivationWindow + ForceCompleteOverdue
- Locking + conflict handling + integration tests

Slice 5: Hardening + Documentation

- Complete test matrix from invariants/stress tests
- API examples + runbook + checklist for PR review

## 5. Definition of Done

1. All use cases trong `docs/dev/walk-session/use-cases.md` (nhom A + B) da co implementation.
2. Lifecycle va invariants trong `docs/dev/walk-session/domain-lifecycle-statemachine-invariants-stresstest.md` duoc cover bang unit tests.
3. Concurrency scenarios co integration tests pass.
4. Controller-only routing rule duoc giu dung (khong business logic trong controller).
5. Response envelope va exception mapping dung theo BackendDevelopmentGuide.
6. Package structure dung chuan folder rules (flat outside `domain/`).

## 6. Rủi ro kỹ thuật và cách giảm thiểu

1. Race condition giua user action va scheduler:

- Giam thieu: lock query (`FOR UPDATE`) + optimistic lock version field

2. GPS points write volume cao:

- Giam thieu: batch insert points, index `(session_id, point_order)`, avoid loading full route in write path

3. Retry tu mobile gay duplicate effects:

- Giam thieu: idempotent domain methods + conflict-safe update handling

4. Drift giua SSOT docs va code:

- Giam thieu: review checklist bat buoc map rule S-1...S-9 vao tests

## 7. Đề xuất thứ tự thực hiện ngay

1. Implement migration `version` cho `walk_session` (neu chua co).
2. Scaffold domain session aggregate + status/errors.
3. Implement 4 user use cases (`activate/cancel/complete/abort`) + API.
4. Implement session_points append + summary update.
5. Implement 2 system use cases + concurrency tests.
