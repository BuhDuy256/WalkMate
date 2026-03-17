# Use Case Data Flow - WalkSession

Tài liệu mô tả luồng dữ liệu cho các use case chính: activate, cancel, abort, complete, append points.

Nguyên tắc áp dụng:

- DDD: Application Service chỉ orchestration.
- Domain (Aggregate/Domain Service) enforce business rule và invariant.
- Backend là source of truth.
- Frontend là eventual consistent replica.

## 1. Activate

- Actor:
  - User
- Input:
  - sessionId
  - user identity (JWT/X-User-Id)
- Flow (FE -> BE -> Domain -> DB):
  - FE gọi POST /sessions/{id}/activate
  - Controller nhận command và chuyển cho Application Service
  - Application Service load aggregate (thường có lock/optimistic version)
  - Domain validate invariants: state hợp lệ, activation window, participant hợp lệ, single active session
  - Domain áp transition PENDING -> ACTIVE (khi đủ điều kiện)
  - Repository persist aggregate
  - BE trả ApiResponse success với SessionResponse
- Side effects:
  - Ghi activation timestamp
  - Có thể phát integration event SessionActivated
- Failure cases:
  - Invalid state transition
  - Activation ngoài cửa sổ thời gian
  - Vi phạm single active session
  - Concurrency conflict do race với cron hoặc command khác

## 2. Cancel

- Actor:
  - User
- Input:
  - sessionId
  - reason
- Flow:
  - FE gọi POST /sessions/{id}/cancel
  - Application Service load aggregate
  - Domain validate: chỉ cho phép khi session đang PENDING, reason hợp lệ
  - Domain áp transition PENDING -> CANCELLED
  - Persist aggregate
  - Trả SessionResponse
- Side effects:
  - Ghi cancellationReason, cancelledBy
  - Có thể phát event SessionCancelled
- Failure cases:
  - Session không còn PENDING
  - Reason không hợp lệ
  - Concurrency conflict với activate gần đồng thời

## 3. Abort

- Actor:
  - User
- Input:
  - sessionId
  - reason (khẩn cấp/an toàn)
- Flow:
  - FE gọi POST /sessions/{id}/abort
  - Application Service load aggregate ACTIVE
  - Domain validate rule abort
  - Domain áp transition ACTIVE -> ABORTED
  - Persist aggregate
  - Trả SessionResponse
- Side effects:
  - Ghi abortReason
  - Đánh dấu actualEndTime theo policy
- Failure cases:
  - Session không ở ACTIVE
  - Session đã terminal
  - Race với complete

## 4. Complete

- Actor:
  - User hoặc System (force complete)
- Input:
  - sessionId
  - distance, duration
- Flow:
  - FE đảm bảo flush pending points trước khi complete
  - FE gọi POST /sessions/{id}/complete
  - Application Service load aggregate ACTIVE
  - Domain validate minimum duration và rules hoàn thành
  - Domain áp transition ACTIVE -> COMPLETED
  - Persist aggregate
  - Trả SessionResponse
- Side effects:
  - Chốt totalDistance/totalDuration
  - Ghi actualEndTime
  - Có thể phát event SessionCompleted
- Failure cases:
  - Không đạt điều kiện complete (ví dụ duration tối thiểu)
  - Session không ở ACTIVE
  - Concurrency conflict (đồng thời abort/cancel/system transition)

## 5. Append points

- Actor:
  - Frontend tracker/worker thay mặt user
- Input:
  - sessionId
  - points[] (pointOrder, lat, lng, time)
  - totalDistance, totalDuration (snapshot)
- Flow:
  - FE gom points thành batch và gọi POST /sessions/{id}/points:append
  - Application Service load aggregate ACTIVE
  - Domain validate append eligibility (state ACTIVE, dữ liệu hợp lệ)
  - Repository persist session_points
  - Repository/Domain cập nhật stats snapshot cho session
  - Trả SessionTrackingResponse
- Side effects:
  - Tăng dữ liệu trace trong DB
  - Cập nhật tiến trình session
- Failure cases:
  - Session không còn ACTIVE
  - Batch rỗng hoặc dữ liệu point invalid
  - Duplicate batch do retry timeout
  - DB conflict nếu enforce unique(sessionId, pointOrder)

## 6. Ghi chú triển khai FE để phù hợp eventual consistency

- FE phải chấp nhận backend state có thể đã thay đổi so với local UI.
- Khi nhận lỗi invalid transition, FE cần:
  - Pull lại state từ GET /sessions/{id}
  - Reconcile local state
  - Ngừng command không còn hợp lệ
- Với append points:
  - Ưu tiên Room + batch sync để tránh mất dữ liệu
  - Chỉ complete sau khi pending points đã flush thành công
