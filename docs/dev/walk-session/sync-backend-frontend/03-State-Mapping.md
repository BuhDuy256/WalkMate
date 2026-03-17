# State Mapping - Backend <-> Frontend

Tài liệu mapping giữa Domain state của backend và UI state của frontend.

## 1. Mapping cơ bản

| Backend State | Frontend State khả dĩ                                                | Ghi chú                            |
| ------------- | -------------------------------------------------------------------- | ---------------------------------- |
| PENDING       | IDLE, ACTIVATING                                                     | 1-n mapping do UI có loading state |
| ACTIVE        | TRACKING_ACTIVE, TRACKING_PAUSED, SYNCING_PENDING_POINTS, COMPLETING | 1-n mapping, có composite/overlay  |
| COMPLETED     | COMPLETED                                                            | Terminal                           |
| CANCELLED     | COMPLETED (variant Cancelled)                                        | Terminal, hiển thị reason          |
| NO_SHOW       | COMPLETED (variant NoShow)                                           | Terminal                           |
| ABORTED       | COMPLETED (variant Aborted)                                          | Terminal                           |

## 2. 1-n mapping và composite states

## 2.1 PENDING -> IDLE, ACTIVATING

- IDLE: chưa phát sinh command activate.
- ACTIVATING: command đã gửi, chờ backend commit.

## 2.2 ACTIVE -> TRACKING_ACTIVE, TRACKING_PAUSED, SYNCING_PENDING_POINTS, COMPLETING

- TRACKING_ACTIVE: tracker local đang nhận điểm.
- TRACKING_PAUSED: tracker local pause, backend vẫn ACTIVE.
- SYNCING_PENDING_POINTS: overlay khi local queue còn pending hoặc worker đang đẩy batch.
- COMPLETING: đã bấm end, đang flush + complete.

## 3. Derived/composite states

Composite state gợi ý:

- TRACKING_ACTIVE + hasPendingSync = true => hiển thị badge Syncing
- TRACKING_PAUSED + hasPendingSync = true => Paused + Syncing
- COMPLETING + syncInProgress = true => Blocking progress state

## 4. Mismatch cases (eventual consistency)

## 4.1 Activate race với system expiry

Hiện tượng:

- FE đang ACTIVATING, nhưng backend cron vừa đóng window và chuyển PENDING -> NO_SHOW/CANCELLED.

Cách xử lý FE:

- Nhận lỗi transition không hợp lệ.
- Refresh session từ backend.
- Chuyển UI về terminal variant tương ứng.

## 4.2 FE nghĩ ACTIVE nhưng backend đã terminal

Hiện tượng:

- Do delay mạng, FE chưa nhận update terminal.

Cách xử lý FE:

- Ở request append/complete tiếp theo nhận lỗi invalid state.
- Ngừng tracker local.
- Đồng bộ lại state từ backend và cập nhật UI.

## 4.3 Syncing delay

Hiện tượng:

- FE hiển thị route realtime từ local nhưng backend chưa có đủ points mới.

Cách xử lý FE:

- Hiển thị trạng thái Syncing/Pending rõ ràng.
- Không dùng backend route để vẽ realtime tức thời.

## 4.4 Duplicate append do retry

Hiện tượng:

- FE retry sau timeout, backend có thể đã ghi batch trước.

Cách xử lý hệ thống:

- Backend cần idempotent theo (sessionId, pointOrder).
- FE giữ pointOrder monotonic và batch retry an toàn.

## 5. Nguyên tắc nhất quán

- Backend luôn là source of truth lifecycle.
- Frontend chỉ cache và suy diễn trạng thái vận hành.
- Mọi mismatch phải được resolve bằng backend refresh + local reconciliation.
