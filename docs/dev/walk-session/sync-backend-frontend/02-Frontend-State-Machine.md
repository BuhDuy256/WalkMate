# Frontend State Machine - WalkSession UI

Tài liệu này mô tả state machine ở frontend, tách biệt giữa UI state và state suy diễn từ backend.

## 1. Nguyên tắc

- Backend state là canonical state.
- Frontend là eventual consistent replica.
- UI có thể có trạng thái vận hành tạm thời (loading/syncing/error) không tồn tại ở backend.

## 2. UI states

- IDLE
- ACTIVATING
- TRACKING_ACTIVE
- TRACKING_PAUSED
- SYNCING_PENDING_POINTS
- COMPLETING
- COMPLETED
- ERROR_RETRYABLE
- ERROR_BLOCKING

## 3. Derived state từ backend

Backend state nhận được:

- PENDING
- ACTIVE
- COMPLETED
- CANCELLED
- NO_SHOW
- ABORTED

Frontend derived/composite:

- Nếu backend = PENDING và user chưa bấm start: IDLE
- Nếu backend = PENDING và đang gọi activate: ACTIVATING
- Nếu backend = ACTIVE và tracker đang chạy: TRACKING_ACTIVE
- Nếu backend = ACTIVE và user pause tracker local: TRACKING_PAUSED
- Nếu backend = ACTIVE và còn local points chưa sync: SYNCING_PENDING_POINTS (overlay)
- Nếu backend = ACTIVE và user bấm end: COMPLETING
- Nếu backend thuộc terminal: COMPLETED (hiển thị biến thể theo terminal type)

Lưu ý:

- TRACKING_PAUSED không map thành backend state riêng.
- SYNCING_PENDING_POINTS là operational overlay state.

## 4. Hành vi UI khi chuyển state

## 4.1 IDLE -> ACTIVATING

- Disable nút Start
- Hiện loading indicator
- Gọi API activate

## 4.2 ACTIVATING -> TRACKING_ACTIVE

- Start foreground tracking service
- Bật hiển thị route realtime
- Hiện nút Pause/End

## 4.3 TRACKING_ACTIVE -> TRACKING_PAUSED

- Dừng intake GPS local
- Giữ nguyên route đã vẽ
- Không thay đổi backend lifecycle

## 4.4 TRACKING_PAUSED -> TRACKING_ACTIVE

- Resume intake GPS local
- Tiếp tục append points theo batch

## 4.5 TRACKING_ACTIVE/TRACKING_PAUSED -> SYNCING_PENDING_POINTS

- Khi có pending points local hoặc worker đang sync
- Hiện trạng thái đồng bộ nền (không chặn thao tác cần thiết)

## 4.6 TRACKING_ACTIVE/TRACKING_PAUSED -> COMPLETING

- Disable thao tác gây race
- Trigger flush toàn bộ pending points
- Chỉ gọi complete khi pending = 0

## 4.7 COMPLETING -> COMPLETED

- Stop tracking service
- Điều hướng sang màn hình kết quả session

## 4.8 Any -> ERROR_RETRYABLE

- Khi lỗi mạng/timeout
- Hiện thông báo + action Retry
- Không xóa local pending data

## 4.9 Any -> ERROR_BLOCKING

- Khi lỗi logic không retry được (invalid state transition, session đã terminal ở backend)
- Khóa hành động không hợp lệ
- Bắt buộc refresh state từ backend

## 5. Gợi ý biểu diễn state trong code

- Main state bằng enum duy nhất:
  - SessionScreenStatus
- Operational flags cho replica/sync:
  - hasPendingSync
  - isSyncWorkerRunning
  - lastSyncError

Cách này tránh mâu thuẫn do nhiều boolean lifecycle, nhưng vẫn đủ thông tin vận hành UI.
