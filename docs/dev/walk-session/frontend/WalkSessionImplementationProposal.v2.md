# WalkSession Implementation Proposal v2 (Room + Batch Sync)

Tài liệu này là bản v2 để triển khai WalkSession theo hướng Local DB (Room) + batch sync, đồng bộ với Domain state machine backend và bám sát UI mock (Init, Progress, Paused).

## 1. Mục tiêu v2

- Không mất dữ liệu GPS khi app bị kill, crash, mất mạng.
- Triển khai đầy đủ use case:
  - activate
  - cancel
  - abort
  - complete
  - append points
- Frontend là eventual consistent replica, backend là source of truth.
- UI bám theo mock:
  - Init: Start Walk, Cancel Walk
  - Progress (LIVE): Pause Walk, End Walk
  - Paused: Resume Walk, End Walk
- Domain rule không bị đẩy vào Application Service.

## 2. Nguyên tắc kiến trúc

- DDD:
  - Backend Domain enforce toàn bộ invariant và state transition legality.
  - Application Service chỉ orchestration.
- State model:
  - Backend lifecycle: PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW, ABORTED.
  - Frontend UI state có operational states (ACTIVATING, SYNCING, ERROR, PAUSED local).
- Dữ liệu:
  - Local Room là write-first store cho trace points.
  - Backend nhận append theo batch từ local queue.

## 3. Scope và Out of Scope

Scope:

- Android frontend WalkSession với Room + WorkManager + Foreground Service.
- Đồng bộ đủ các API session hiện có.
- Hỗ trợ eventual consistency và retry.

Out of scope:

- Thiết kế lại domain backend.
- Bổ sung backend APIs mới ngoài 5 API đã có.
- Refactor UI framework lớn ngoài MVVM-lite hiện tại.

## 4. Contract API dùng trong v2

- POST /api/v1/sessions/{id}/activate
- POST /api/v1/sessions/{id}/points:append
- POST /api/v1/sessions/{id}/complete
- POST /api/v1/sessions/{id}/abort
- POST /api/v1/sessions/{id}/cancel
- GET /api/v1/sessions/{id} (reconcile state khi mismatch)

ApiResponse backend:

- success
- data
- error { code, message }
- timestamp

## 5. Thiết kế dữ liệu Local (Room)

## 5.1 Bảng session_local

Mục đích:

- Cache snapshot session cho UI và orchestration complete/sync.

Các cột chính:

- sessionId (PK)
- backendState (PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW, ABORTED)
- uiState (IDLE, ACTIVATING, TRACKING_ACTIVE, TRACKING_PAUSED, SYNCING, COMPLETING, COMPLETED_VIEW, ERROR)
- totalDistanceMeters
- totalDurationSeconds
- localTrackerState (RUNNING, PAUSED, STOPPED)
- hasPendingSync
- lastSyncedPointOrder
- lastErrorCode
- lastErrorMessage
- updatedAt

## 5.2 Bảng session_point_local

Mục đích:

- Lưu trace points durable trước khi sync backend.

Các cột chính:

- localId (PK, autoincrement)
- sessionId
- pointOrder
- lat
- lng
- time
- syncStatus (PENDING, SYNCING, SYNCED, FAILED)
- retryCount
- batchToken (nullable)
- createdAt
- updatedAt

Index và ràng buộc:

- UNIQUE(sessionId, pointOrder)
- INDEX(sessionId, syncStatus, pointOrder)
- INDEX(sessionId, time)

## 5.3 Bảng session_sync_job (tùy chọn nhưng nên có)

Mục đích:

- Theo dõi trạng thái worker để debug và chống chạy chồng.

Cột chính:

- sessionId (PK)
- workerState (IDLE, RUNNING, BACKOFF)
- nextRetryAt
- lastBatchSize
- lastFailure

## 6. State model frontend v2

## 6.1 UI state enum

- IDLE
- ACTIVATING
- TRACKING_ACTIVE
- TRACKING_PAUSED
- SYNCING_PENDING_POINTS
- COMPLETING
- COMPLETED_VIEW
- ERROR_RETRYABLE
- ERROR_BLOCKING

## 6.2 Mapping với backend state

- PENDING -> IDLE hoặc ACTIVATING
- ACTIVE -> TRACKING_ACTIVE, TRACKING_PAUSED, SYNCING_PENDING_POINTS, COMPLETING
- COMPLETED/CANCELLED/NO_SHOW/ABORTED -> COMPLETED_VIEW

Lưu ý quan trọng:

- TRACKING_PAUSED là local tracker state, không phải backend lifecycle state.

## 7. Kiến trúc lớp triển khai

- ui/session
  - SessionActivity
  - SessionViewModel
  - SessionUiState
- core/tracking
  - LocationTrackingService
  - TrackingServiceContract
  - TrackingMath
- data/local
  - Room database
  - Dao cho session_local và session_point_local
- data/remote
  - Retrofit SessionApi
- data/repository
  - SessionRepository (orchestration local + remote)
  - SessionSyncRepository (batch sync pipeline)
- worker
  - SessionPointSyncWorker (WorkManager)

## 8. Luồng chuẩn theo UI mock

## 8.1 Màn Init

UI chính:

- Nút Start Walk
- Nút Cancel Walk
- Card partner + route summary

Hành vi:

- Start Walk -> activate use case
- Cancel Walk -> cancel use case

## 8.2 Màn Progress (LIVE)

UI chính:

- Badge LIVE
- Timer, steps/distance/calories
- Nút Pause Walk
- Nút End Walk

Hành vi:

- GPS chạy foreground service
- Point ghi Room ngay lập tức
- Worker sync theo batch nền

## 8.3 Màn Paused

UI chính:

- Badge PAUSED
- Nút Resume Walk
- Nút End Walk

Hành vi:

- Không nhận điểm mới trong lúc paused
- Worker vẫn có thể sync nốt điểm pending

## 9. Use Case triển khai đầy đủ

## 9.1 Activate

Actor:

- User

Input:

- sessionId
- user identity token/header

Flow:

1. FE set uiState = ACTIVATING.
2. FE gọi POST activate.
3. Thành công:

- Upsert session_local.backendState = ACTIVE
- uiState = TRACKING_ACTIVE
- Start foreground tracking service
- Schedule sync worker unique theo sessionId

4. Thất bại:

- Nếu retryable network: ERROR_RETRYABLE
- Nếu invalid transition (window đóng, terminal): refresh GET session -> map về COMPLETED_VIEW hoặc IDLE tùy backend

Side effects:

- UI chuyển từ Init sang Progress.

Failure cases:

- Race với system expire window.
- Vi phạm invariant single active session.

## 9.2 Cancel

Actor:

- User

Input:

- sessionId, reason

Flow:

1. FE gọi POST cancel.
2. Thành công:

- session_local.backendState = CANCELLED
- uiState = COMPLETED_VIEW (variant Cancelled)
- Stop tracking service nếu đang chạy
- Cancel sync worker

3. Thất bại:

- invalid transition -> refresh state backend

Side effects:

- Hiển thị reason cancel trong summary cuối.

Failure cases:

- Session đã ACTIVE/terminal.

## 9.3 Abort

Actor:

- User

Input:

- sessionId, reason

Flow:

1. FE đổi uiState tạm = COMPLETING (blocking thao tác).
2. FE có thể trigger flush pending nhanh (best effort).
3. FE gọi POST abort.
4. Thành công:

- backendState = ABORTED
- uiState = COMPLETED_VIEW (variant Aborted)
- Stop tracking service
- Dừng worker

5. Thất bại:

- network lỗi: ERROR_RETRYABLE + cho phép retry abort
- invalid transition: reconcile từ backend

Side effects:

- Kết thúc session khẩn cấp.

Failure cases:

- Race với complete.

## 9.4 Complete

Actor:

- User (hoặc system force-complete ở backend)

Input:

- sessionId
- snapshot distance/duration từ local source

Flow:

1. User bấm End Walk -> uiState = COMPLETING.
2. Khóa các nút Pause/Resume/End.
3. Trigger flush pipeline:

- chạy sync batch cho tới khi không còn PENDING/FAILED có thể retry

4. Khi pending = 0 -> gọi POST complete(distance, duration).
5. Thành công:

- backendState = COMPLETED
- uiState = COMPLETED_VIEW
- Stop tracking service + stop worker

6. Thất bại:

- nếu append chưa flush hết: giữ COMPLETING + retry sync
- nếu complete lỗi retryable: ERROR_RETRYABLE nhưng vẫn giữ queue local
- nếu complete bị reject vì state backend đã đổi: refresh backend và reconcile

Side effects:

- Chốt thống kê cuối, điều hướng màn hình kết quả.

Failure cases:

- Không đạt minimum duration rule.
- Backend đã chuyển ABORTED/CANCELLED do race.

## 9.5 Append points

Actor:

- Tracker service + sync worker

Input:

- sessionId
- points[]
- totalDistance, totalDuration snapshot

Flow ghi point local:

1. Service nhận GPS point hợp lệ.
2. Insert session_point_local với syncStatus=PENDING.
3. Update session_local stats + hasPendingSync=true.
4. Phát stream local cho UI render map realtime.

Flow sync batch:

1. Worker lấy top K điểm PENDING theo pointOrder.
2. Mark SYNCING + batchToken.
3. Gọi POST points:append.
4. Thành công -> mark SYNCED, cập nhật lastSyncedPointOrder.
5. Thất bại:

- retryable -> trả lại PENDING, tăng retryCount, backoff.
- non-retryable -> FAILED + báo UI.

Side effects:

- Dữ liệu backend tăng dần theo lô.
- UI có thể hiện badge syncing.

Failure cases:

- timeout/retry gây duplicate logic.
- session backend không còn ACTIVE.

## 10. Chính sách sync và retry

Batch policy:

- K = 20-50 points
- T = 8-15 giây
- Trigger khi đủ K hoặc quá T

Retry policy:

- Exponential backoff: 2s, 5s, 15s, 30s, 60s
- Max retry mỗi batch: 8
- Có jitter tránh dồn tải

Yêu cầu idempotency:

- FE giữ pointOrder tăng đơn điệu theo session.
- Backend nên enforce unique(sessionId, pointOrder) hoặc insert ignore duplicate.

## 11. Hành vi UI chi tiết theo thiết kế ảnh

## 11.1 Header map và badge trạng thái

- Init: không badge LIVE/PAUSED.
- Progress: badge LIVE màu xanh.
- Paused: badge PAUSED màu cam.

## 11.2 Cụm CTA chính

- Init: Start Walk (primary), Cancel Walk (secondary-danger).
- Progress: Pause Walk (primary), End Walk (danger).
- Paused: Resume Walk (primary), End Walk (danger).

## 11.3 Card thông tin

- Partner card luôn hiển thị avatar + trust score + status text.
- Stats card cập nhật từ local stream (duration, distance, calories).
- Route card hiển thị tuyến và khoảng cách mục tiêu.
- Tip card đổi nội dung theo state:
  - Init: nhắc bắt đầu
  - Progress: khuyến khích tiến độ
  - Paused: nhắc resume

## 11.4 Map behavior

- Không recreate map fragment.
- Polyline setPoints từ dữ liệu local.
- Camera cập nhật mềm theo điểm cuối (không reset zoom đột ngột).

## 12. DDD compliance checklist

- Domain rule chỉ nằm backend aggregate/domain service.
- FE không tự suy ra hợp lệ transition domain.
- FE luôn reconcile khi backend trả invalid transition.
- Application service backend không chứa business rule.

## 13. Mismatch và eventual consistency handling

Case 1: FE đang ACTIVATING nhưng backend đã NO_SHOW/CANCELLED

- Xử lý: refresh GET session, chuyển COMPLETED_VIEW variant phù hợp.

Case 2: FE local đang TRACKING nhưng backend đã terminal

- Xử lý: request append/complete fail -> stop tracker -> reconcile backend.

Case 3: FE hiển thị route mới nhưng backend chưa kịp sync

- Xử lý: vẫn render local route, hiển thị SYNCING_PENDING_POINTS.

## 14. Package structure đề xuất v2

```text
com.walkmate
├── core
│   └── tracking
├── data
│   ├── local
│   │   ├── db/WalkSessionDatabase.java
│   │   ├── entity/SessionLocalEntity.java
│   │   ├── entity/SessionPointLocalEntity.java
│   │   ├── dao/SessionLocalDao.java
│   │   └── dao/SessionPointLocalDao.java
│   ├── remote
│   ├── repository
│   │   ├── SessionRepository.java
│   │   └── SessionSyncRepository.java
│   └── worker
│       └── SessionPointSyncWorker.java
└── ui
    └── session
```

## 15. Kế hoạch triển khai

Phase 1 (core flow):

- Room schema + DAO
- Activate/append/complete với sync batch
- UI Init/Progress/Paused chạy end-to-end

Phase 2 (độ bền):

- Retry/backoff hoàn chỉnh
- Error taxonomy retryable/blocking
- Mismatch reconcile bằng GET session

Phase 3 (vận hành):

- Metrics sync
- Cleanup dữ liệu local sau terminal + synced
- Tối ưu batch size theo telemetry

## 16. Acceptance criteria v2

- Không mất point khi app bị kill giữa session.
- Start/Pause/Resume/End đúng hành vi UI mock.
- Cancel/Abort/Complete xử lý đầy đủ và đúng terminal mapping.
- Complete chỉ gọi khi pending points đã flush thành công.
- Khi backend trả invalid transition, UI reconcile chính xác.
- App vẫn render route realtime dù backend đang sync delay.

## 17. Kết luận

Bản v2 với Room + batch sync giải quyết được cả độ bền dữ liệu lẫn trải nghiệm realtime.
Thiết kế này implement đủ toàn bộ use case domain, tuân thủ DDD, và nhất quán với nguyên tắc backend source of truth, frontend eventual consistent replica.
