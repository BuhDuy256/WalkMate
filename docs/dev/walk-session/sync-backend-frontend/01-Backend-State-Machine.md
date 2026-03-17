# Backend State Machine - WalkSession

Tài liệu này mô tả state machine ở tầng Domain cho Aggregate WalkSession.

## 1. Trạng thái Domain (source of truth)

- PENDING
- ACTIVE
- COMPLETED (terminal)
- CANCELLED (terminal)
- NO_SHOW (terminal)
- ABORTED (terminal)

Lưu ý:

- Không có trạng thái PAUSED ở backend.
- PAUSED chỉ là trạng thái vận hành tracker ở frontend/local.

## 2. Invariants bắt buộc

- Mutual confirmation:
  - WalkSession chỉ chuyển sang ACTIVE khi đủ điều kiện xác nhận từ 2 phía theo cửa sổ activation hợp lệ.
- Single active session:
  - Một user không được đồng thời thuộc nhiều session chồng lấp ở trạng thái PENDING/ACTIVE trong cùng khung thời gian.
- Terminal immutability:
  - Khi đã vào COMPLETED/CANCELLED/NO_SHOW/ABORTED thì không được chuyển trạng thái tiếp.

## 3. Command-triggered transitions

## 3.1 PENDING -> ACTIVE

- Trigger:
  - API: POST /api/v1/sessions/{id}/activate
- Guard conditions:
  - Session đang ở PENDING
  - Request nằm trong activation window hợp lệ
  - User gọi API thuộc participants của session
  - Không vi phạm single active session
  - Domain xác định đủ điều kiện mutual activation để vào ACTIVE
- Side effects:
  - Ghi activation time theo user
  - Cập nhật actualStartTime khi transition sang ACTIVE
  - Persist version mới (optimistic locking)

## 3.2 PENDING -> CANCELLED

- Trigger:
  - API: POST /api/v1/sessions/{id}/cancel
- Guard conditions:
  - Session đang ở PENDING
  - Lý do hủy hợp lệ
  - Không ở terminal state
- Side effects:
  - Ghi cancellationReason, cancelledBy
  - Đóng session ở trạng thái terminal

## 3.3 ACTIVE -> COMPLETED

- Trigger:
  - API: POST /api/v1/sessions/{id}/complete
- Guard conditions:
  - Session đang ở ACTIVE
  - Duration/distance hợp lệ theo domain rule (ví dụ minimum duration)
  - Không ở terminal state
- Side effects:
  - Ghi actualEndTime
  - Chốt totalDistance, totalDuration
  - Đóng session ở terminal COMPLETED

## 3.4 ACTIVE -> ABORTED

- Trigger:
  - API: POST /api/v1/sessions/{id}/abort
- Guard conditions:
  - Session đang ở ACTIVE
  - Lý do abort hợp lệ
  - Không ở terminal state
- Side effects:
  - Ghi abortReason
  - Ghi actualEndTime (nếu rule yêu cầu)
  - Đóng session ở terminal ABORTED

## 3.5 ACTIVE -> ACTIVE (append points, state-preserving)

- Trigger:
  - API: POST /api/v1/sessions/{id}/points:append
- Guard conditions:
  - Session đang ở ACTIVE
  - points không rỗng, pointOrder hợp lệ
  - Stats snapshot hợp lệ (distance/duration không âm)
- Side effects:
  - Persist session_points theo batch
  - Cập nhật tracking stats hiện tại
  - Không đổi state lifecycle

## 4. Time-based transitions (system-triggered)

## 4.1 PENDING -> NO_SHOW

- Trigger:
  - System event: ProcessSessionActivationWindow khi hết activation window
- Guard conditions:
  - Đúng thời điểm đóng cửa sổ activation
  - Chỉ có 1 user đã activate
  - Transaction lock để chống race với lệnh activate realtime
- Side effects:
  - Đánh dấu NO_SHOW
  - Ghi metadata liên quan no-show theo domain policy

## 4.2 PENDING -> CANCELLED

- Trigger:
  - System event: ProcessSessionActivationWindow khi hết activation window
- Guard conditions:
  - Không user nào activate trong window
  - Row lock/optimistic lock đảm bảo nhất quán
- Side effects:
  - Đánh dấu CANCELLED
  - Ghi lý do hệ thống (expired window)

## 4.3 ACTIVE -> COMPLETED

- Trigger:
  - System event: ForceCompleteOverdueSession
- Guard conditions:
  - Session ACTIVE quá ngưỡng tối đa cho phép (zombie guard)
- Side effects:
  - Auto-complete session
  - Chốt thời gian kết thúc và stats snapshot hiện có

## 5. Ranh giới DDD

- Application Service:
  - Chỉ orchestration (load aggregate, gọi domain method, save, publish integration event).
  - Không chứa business rule cốt lõi.
- Domain (Aggregate/Domain Service):
  - Là nơi enforce toàn bộ invariant và transition legality.
- Backend:
  - Là source of truth duy nhất cho lifecycle state của WalkSession.
