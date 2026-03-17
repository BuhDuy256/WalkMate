# Room + Batch Sync Design for WalkSession

Tai lieu nay mo ta thiet ke chi tiet cho huong luu GPS vao Room truoc, sau do dong bo theo batch len backend.
Muc tieu: khong mat du lieu khi app kill, van giu map realtime, va giam tai network/backend.

## 1. Muc tieu thiet ke

- Durable first: point ghi duoc vao local DB ngay khi nhan GPS.
- Sync asynchronous: worker dong bo theo lo thay vi call API moi point.
- Complete safety: chi complete session sau khi flush het point pending.
- Idempotent-friendly: retry an toan, khong tao duplicate logic.

## 2. Kien truc tong quan

Thanh phan:

- LocationTrackingService
  - Nhan GPS point
  - Ghi point vao Room
  - Gui event cho UI de render realtime
- SessionLocalRepository
  - DAO thao tac local session + points
- SyncCoordinator
  - Trigger WorkManager de sync batch
  - Quan ly lock theo session
- SyncWorker
  - Lay points PENDING theo batch
  - Goi API points:append
  - Danh dau SYNCED hoac FAILED
- SessionViewModel
  - Render du lieu map + stats
  - Orchestrate complete flow

Nguyen tac:

- UI map khong cho ket qua network moi ve point, UI doc tu stream local de mượt.
- Network la duong dong bo, khong la nguon su that duy nhat cua route.

## 3. Local schema de xuat

## 3.1 Bang session_local

Cot chinh:

- sessionId (PK)
- status (IDLE, ACTIVATING, TRACKING, PAUSED, COMPLETING, COMPLETED, ERROR)
- totalDistanceMeters
- totalDurationSeconds
- lastPointOrder
- startedAt
- endedAt
- syncState (IDLE, SYNCING, FAILED)
- lastSyncAt
- createdAt
- updatedAt

## 3.2 Bang session_point_local

Cot chinh:

- localId (PK, autoincrement)
- sessionId (index)
- pointOrder
- lat
- lng
- time
- syncStatus (PENDING, SYNCING, SYNCED, FAILED)
- retryCount
- batchId (nullable)
- createdAt
- updatedAt

Index va rang buoc quan trong:

- UNIQUE(sessionId, pointOrder)
- INDEX(sessionId, syncStatus, pointOrder)
- INDEX(sessionId, createdAt)

Loi ich:

- UNIQUE tranh duplicate logic khi service ghi trung point hoac worker retry.
- Index giup lay nhanh PENDING theo thu tu.

## 4. Luong nghiep vu chi tiet

## 4.1 Start session

1. ViewModel goi activate API.
2. Thanh cong thi tao/initialize session_local voi status TRACKING.
3. Start Foreground Service.
4. Schedule SyncWorker periodic hoac one-time chain.

## 4.2 Nhan GPS point

1. Service nhan point moi.
2. Validate point (accuracy, min-distance).
3. Insert vao session_point_local voi syncStatus = PENDING.
4. Update session_local stats va lastPointOrder.
5. Publish event UI de ve polyline realtime.
6. Neu dat nguong N point hoac T giay thi trigger sync.

## 4.3 Batch sync

1. Worker lay lock theo sessionId (tranh 2 worker cung sync).
2. Query top K points PENDING theo pointOrder.
3. Mark tam SYNCING + gan batchId.
4. Goi API append voi list points va totalDistance/totalDuration snapshot.
5. Neu thanh cong:

- Mark points trong batch la SYNCED.
- Cap nhat lastSyncAt.

6. Neu that bai:

- Tra ve FAILED hoac PENDING tuy theo loai loi.
- Tang retryCount.
- Schedule retry backoff.

## 4.4 Pause/Resume

- Pause: service dung nhan point moi, status local = PAUSED.
- Resume: tiep tuc nhan point, status local = TRACKING.
- Sync van co the tiep tuc cho point dang pending.

## 4.5 Complete session

1. User bam End -> status = COMPLETING.
2. Trigger immediate flush: sync den khi khong con PENDING/FAILED co the retry.
3. Goi API complete voi snapshot stats cuoi.
4. Thanh cong -> status = COMPLETED, stop service.
5. That bai -> status = ERROR, giu local state de retry complete.

## 5. Chien luoc batch

Khuyen nghi:

- Batch size K: 20-50 points.
- Time window T: 8-15 giay.
- Trigger theo dieu kien OR:
  - Du K point pending
  - Qua T giay tu lan sync cuoi

Ly do:

- Giam request overhead.
- Van giu do tre nhan duoc cho near-realtime.

## 6. Xu ly loi va retry

Phan loai loi:

- Loi tam thoi (timeout, 5xx, no network): retry backoff exponential.
- Loi client (4xx do payload sai): danh dau FAILED, can can thiep.
- Loi duplicate logic: neu backend idempotent thi xem la success logic.

Retry policy goi y:

- maxRetry per batch: 5-8
- backoff: 2s, 5s, 15s, 30s, 60s
- co jitter de tranh dồn request

## 7. Cac van de ban co the gap

1. Duplicate points khi retry

- Nguyen nhan: append timeout nhung backend da ghi.
- Giam thieu:
  - Backend unique(session_id, point_order)
  - Upsert/ignore duplicate

2. Out-of-order pointOrder

- Nguyen nhan: race condition giua service va worker.
- Giam thieu:
  - pointOrder cap phat 1 chieu trong service
  - Query sync theo pointOrder tang dan

3. Worker chay song song

- Nguyen nhan: multiple trigger cung luc.
- Giam thieu:
  - unique work name theo sessionId
  - DB lock logic per session

4. Session complete khi van con pending

- Nguyen nhan: user bam End trong luc network loi.
- Giam thieu:
  - hard gate: complete chi khi pending = 0
  - UI thong bao dang dong bo

5. DB local phinh to

- Nguyen nhan: luu raw points qua lau.
- Giam thieu:
  - retention policy sau khi completed + synced
  - nen route (optional giai doan sau)

6. Battery impact

- Nguyen nhan: GPS high accuracy + sync qua day.
- Giam thieu:
  - min distance filter
  - batch theo T giay
  - can nhac priority location theo state

7. Mismatch stats local va backend

- Nguyen nhan: lam tron so, retry mot phan batch.
- Giam thieu:
  - snapshot stats tu local source of truth
  - complete gui stats cuoi cung

## 8. Yeu cau backend de mo hinh nay on dinh

Nen co:

- Append idempotent theo (sessionId, pointOrder)
- Reject duplicate theo unique key hoac ignore duplicate
- API response ro rang voi success, error code/message

Neu chua co idempotent:

- Frontend van chay duoc, nhung rui ro duplicate tang cao khi retry.

## 9. Metrics can theo doi

- pending_points_count theo session
- sync_success_rate
- sync_retry_rate
- average_batch_size
- median_sync_latency
- duplicate_reject_count (neu backend expose)
- time_from_end_to_complete

## 10. Ke hoach rollout de xuat

Phase 1:

- Add Room schema + local insert + batch sync co ban
- Gate complete khi pending = 0

Phase 2:

- Retry/backoff day du + metrics
- Canh bao UI khi co FAILED dai

Phase 3:

- Toi uu retention va compact du lieu
- Danh gia chunk append neu volume rat lon

## 11. Ket luan

Room + batch sync la lua chon tot hon cho production reliability:

- Giam nguy co mat du lieu
- On dinh hon khi offline/mang yeu
- Van giu duoc map realtime

Tradeoff chinh la tang do phuc tap implementation, nhung doi lai la tinh ben va kha nang van hanh tot hon ro ret.
