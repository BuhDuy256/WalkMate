# Implementation Order

## 1. Tầng Core Domain
> Nhìn vào: `2-domain-lifecycle-statemachine-invariants-stresstest.workflow.md`

- Định nghĩa các Aggregate Roots, Entities, Value Objects
- Định nghĩa Lifecycle Enum (VD: `PENDING`, `ACTIVE`...)
- Code các hàm State Transitions, ném `DomainException` ngay ở tầng Domain nếu:
  - Transition sai logic
  - Invariant bị vi phạm
- ⚠️ Code hoàn toàn thuần túy — không import gì từ framework, database, hay HTTP

---

## 2. Tầng Data / Persistence
> Nhìn vào: `3-from-lifecycle-spec-to-data-model.md`

- Chạy script Database Migration để tạo table (`walk_session`, `walk_trace`...)
- Cài đặt DB Constraints:
  - `UNIQUE INDEX` cho invariants dạng uniqueness
  - `CHECK` constraint cho invariants dạng điều kiện
- Code Repository Interfaces (ở Domain Layer) và Implementation (ở Infrastructure Layer)
- Xử lý concurrency theo kết quả Stress Test (Optimistic Locking / `SELECT FOR UPDATE`)

---

## 3. Tầng Application / Use Cases
> Nhìn vào: `1-use-cases-detection.workflow.md` + Stress Test output

- Viết các Use Cases (VD: `StartWalkSessionUseCase`, `EndWalkSessionUseCase`...)
- Gọi Repository để load và save Aggregate
- Wrap trong **Transaction** nếu có nhiều write
- Xử lý fail/retry theo đúng kịch bản Stress Test

---

## 4. Tầng Presentation (API / Controllers)
> Nhìn vào: Use Case interfaces từ Bước 3

- Expose các Use Case thành REST Endpoints
- Map Exceptions → HTTP Status Code:
  - `DomainException` → 422
  - `NotFoundError` → 404
  - `ConflictError` → 409

---

## 5. Tests
> Nhìn vào: Invariants list + Stress Test output từ Bước 2

- **Unit Tests**: mỗi State Transition + mỗi Invariant violation → 1 test
- **Integration Tests**: mỗi kịch bản Stress Test → 1 test
  - VD: Gọi `payOrder()` đồng thời 2 lần → assert chỉ 1 thành công
  - VD: Retry 3 lần → assert không duplicate side effect