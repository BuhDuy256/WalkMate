# Proposal: WalkIntent Domain + API Implementation (Compliant with BackendDevelopmentGuide)

## 1. Mục tiêu

Triển khai đầy đủ backend cho `WalkIntent` theo thứ tự bám sát SSOT trong:
- `docs/dev/walk-intent/domain-lifecycle-stresstest.md`
- `docs/dev/walk-intent/use-cases.md`

Và tuân thủ nghiêm ngặt kiến trúc DDD-lite từ `docs/single-source-of-truth/development-guide/BackendDevelopmentGuide.md`.

**Scope chức năng:**
- WalkIntent lifecycle: `DRAFT -> OPEN -> (CONSUMED | CANCELLED | EXPIRED)`
- Cấu trúc thư mục theo chuẩn Spring Boot BackendDevelopmentGuide.
- Optimistic Locking chống race condition.
- API cho thao tác người dùng và hệ thống.

---

## 2. Nguyên tắc bắt buộc từ BackendDevelopmentGuide

1.  **DDD-lite layer chuẩn:**
    *   Thư mục `domain/` là nơi duy nhất được gom nhóm theo feature (`domain/intent/...`).
    *   Các tầng `application/`, `presentation/`, `infrastructure/` phải giữ cấu trúc phẳng (flat), không có sub-folder theo feature.
2.  **Naming Convention:**
    *   Use Case ở tầng Application bắt buộc gọi là `*Service` (VD: `SubmitWalkIntentService` thay vì `SubmitWalkIntentUseCase`).
3.  **Controller mỏng:**
    *   Không inject repository, không chứa business logic, chỉ map DTO và gọi Service.
4.  **Transaction Boundary:**
    *   Đặt `@Transactional` tại Application Service.
5.  **Response Envelope:**
    *   Mọi API trả về `ApiResponse<T>`.
    *   Mapping exception qua Global Handler: `DomainException` -> 422, `NotFound` -> 404, `Conflict` (OptimisticLock) -> 409.

---

## 3. Thiết kế triển khai theo 5 tầng

### 3.1 Core Domain (Thuần tuý, Không Framework)

**Package dự kiến:**
- `domain/intent/WalkIntent.java` (Aggregate Root)
- `domain/intent/IntentStatus.java` (Enum: `OPEN`, `EXPIRED`, `CANCELLED`, `CONSUMED`)
- `domain/intent/WalkPurpose.java`
- `domain/intent/IntentRepository.java` (Interface)
- `domain/intent/DomainException.java`

**Value Objects dự kiến:**
- `domain/valueobject/TimeWindow.java` (startTime, endTime)
- `domain/valueobject/LocationSnapshot.java` (lat, lng)
- `domain/valueobject/MatchingConstraints.java` (Min/Max age, Gender preference...)

**Domain methods (Intention-revealing):**
- `static create(UUID userId, TimeWindow window, LocationSnapshot loc, ...)`: Validate `time_window_start > now() + buffer` (I-6). Trả về intent trạng thái `OPEN`.
- `consume()`: Đổi state sang `CONSUMED`. Throw exception nếu khác `OPEN`. (I-3).
- `cancel()`: Đổi state sang `CANCELLED`. Throw exception nếu khác `OPEN`. (I-5).
- `expire(Instant now)`: Đổi state sang `EXPIRED`. Throw exception nếu `now < time_window_end` hoặc state khác `OPEN`.

### 3.2 Data / Persistence

**Migration:**
- Đã chạy script tạo bảng `walk_intent` kèm `GiST EXCLUDE constraint` chống trùng lịch (I-1).
- Chạy script bổ sung trường `version` dùng cho Optimistic Locking (`2026-03-14-add-walk-intent-version.sql`).

**Repository split:**
- Domain interface: `domain/intent/IntentRepository.java`
- Infra implementation: `infrastructure/repository/JpaIntentRepository.java`
- JPA entity mapping: `infrastructure/repository/entity/WalkIntentJpaEntity.java` (Áp dụng `@Version` cho cột version).

**Queries quan trọng:**
- `findByIdForUpdate(...)`: Dùng raw query chứa `SELECT ... FOR UPDATE SKIP LOCKED` cho cronjob quét zombie intents để tránh tranh chấp với quá trình matching (I-7).

### 3.3 Application / Services (Flat Structure)

**Thư mục `application/`:**
*   `SubmitWalkIntentService`
*   `CancelWalkIntentService`
*   `ConsumeWalkIntentService` (Do hệ thống gọi khi MatchProposal confirm)
*   `ExpireWalkIntentJobService` (Cronjob hệ thống gọi)

**Pattern thực thi:**
- `Submit`: Khởi tạo Aggregate `WalkIntent.create(...)` -> gọi `repository.save()`. Bắt lỗi `DataIntegrityViolationException` do (I-1 GiST) bắn ra và ném thành lỗi `Conflict`.
- `Cancel/Consume`: `repository.findById()` -> gọi `intent.cancel()`/`consume()` -> `repository.save()`. Nếu có exception khóa lạc quan, hệ thống ném HTTP 409.

### 3.4 Presentation / API

**Thư mục `presentation/`:**
- `presentation/controller/IntentController.java`
- `presentation/dto/request/SubmitIntentRequest.java`
- `presentation/dto/request/CancelIntentRequest.java`
- `presentation/dto/response/IntentResponse.java`
- `presentation/mapper/IntentMapper.java`

**API Mapping:**
- `POST /api/v1/intents` -> `SubmitWalkIntentService`
- `DELETE /api/v1/intents/{id}` -> `CancelWalkIntentService`
- *(Các method Consume/Expire hoàn toàn nằm ở nội bộ hệ thống).*

**Security:**
- Trích xuất `UserId` an toàn từ `@AuthenticationPrincipal` thay vì body payload.

### 3.5 Tests (Unit & Integration)

**Unit Tests (Tầng Domain):**
- Test `WalkIntent.create(...)` bắt lỗi mốc thời gian không chuẩn.
- Test validate các trạng thái `.cancel()`, `.consume()`, `.expire()` ném `DomainException` đúng nếu sai state.

**Integration Tests (Tầng Data & Application):**
- Test **I-1**: Giả lập gọi `SubmitWalkIntentService` 2 lần song song với cùng một `TimeWindow` trùng nhau. Assert cái thứ 2 bị exception Data Conflict từ DB dội lên thay vì pass qua.
- Test **I-7**: Mô phỏng Thread 1 lấy `FOR UPDATE` bằng job expire. Thread 2 gọi consume. Phải sinh ra OptimisticLock hoặc bị block.

---

## 4. Lộ trình giao hàng (Slicing)

*   **Slice 1: Foundation:** Áp dụng schema migration `version`. Setup hệ thống domain files `WalkIntent`.
*   **Slice 2: API & Application:** Cài đặt các service `SubmitWalkIntentService` và `CancelWalkIntentService` kèm Controller, DTO, Mapper. Cài đặt test cho Invariant I-1 (Chống trùng lịch).
*   **Slice 3: System Logic:** Viết job `ExpireWalkIntentJobService` với DB Lock và `ConsumeWalkIntentService`. Tích hợp logic xử lý Optimistic Locking Exception trả về 409 trong Global Exception Handler. Viết Integration tests tương ứng.
