# GPS Trace Options - Tradeoff Analysis

Tai lieu nay tong hop tradeoff giua cac option luu va dong bo GPS trace cho WalkSession.
Muc tieu la chon duoc huong vua dung duoc ngay, vua co duong nang cap ro rang.

## 1) Tieu chi danh gia

- Do ben du lieu: co mat point khi app kill/crash khong
- Tai network: so request, kich thuoc payload
- Tai database: so lan write, do phinh du lieu
- Do phuc tap implementation
- Kha nang realtime map
- Kha nang retry/idempotency
- Do phu hop voi API backend hien tai

## 2) Cac option

### Option A - Full replace route moi lan cap nhat

Mo ta:

- Moi lan cap nhat, gui toan bo danh sach points da co de backend set lai route.

Uu diem:

- De hieu o muc y tuong.
- Khong can quan tam point trung theo tung dot append.

Nhuoc diem:

- Payload tang theo thoi gian, rat ton bang thong.
- Write amplification lon o backend/DB.
- De timeout khi route dai.
- Scale kem cho session dai hoac user dong thoi cao.

Danh gia:

- Khong nen dung cho tracking realtime.

### Option B - Append tung point, 1 call cho 1 point

Mo ta:

- Moi point GPS goi 1 lan API append.

Uu diem:

- Don gian phia model du lieu.
- Realtime manh, point den dau thay den do.

Nhuoc diem:

- So request rat lon.
- Overhead HTTP va transaction cao.
- De nhe hang network va backend khi user dong thoi cao.

Danh gia:

- Co the dung prototype, nhung khong toi uu.

### Option C - Append theo batch points (hien tai)

Mo ta:

- Gom N points (vi du 20) hoac theo timer, gui 1 lan append.
- End session thi flush batch cuoi roi complete.

Uu diem:

- Giam request manh so voi Option B.
- Van giu duoc realtime tot (tre nho theo batch window).
- Phu hop API backend hien tai ngay lap tuc.

Nhuoc diem:

- Neu chi buffer tren RAM, app kill co the mat point chua flush.
- Can xu ly duplicate neu retry ma backend chua idempotent.

Danh gia:

- Lua chon can bang tot de ship nhanh.

### Option D - Append theo chunk (1 record chua nhieu points)

Mo ta:

- Moi chunk chua 20-100 points (JSON hoac encoded polyline), append chunk.

Uu diem:

- Giam so row DB so voi 1 point/row.
- Giam chi phi index va I/O row-level.
- Van la append, khong can full replace.

Nhuoc diem:

- Query/phan tich point-level phuc tap hon.
- Can logic decode chunk.
- Can quyet dinh kich thuoc chunk toi uu.

Danh gia:

- Tot khi traffic lon va can toi uu storage.

### Option E - Local DB (Room) + sync batch len backend

Mo ta:

- Point duoc ghi local truoc (durable).
- Worker sync cac point pending theo batch.
- Complete chi goi sau khi flush pending.

Uu diem:

- Chong mat du lieu khi app kill/crash.
- Offline-first, retry on dinh.
- Kiem soat traffic backend tot hon.

Nhuoc diem:

- Tang do phuc tap (schema, state sync, worker, retry).
- Can quy trinh nhat quan trang thai pending/synced.

Danh gia:

- Tot nhat cho production reliability.

### Option F - Chi luu local, upload 1 lan khi ket thuc

Mo ta:

- Trong luc di bo chi luu local.
- Ket thuc moi upload toan bo.

Uu diem:

- Rat it call backend trong session.
- De giam tai backend realtime.

Nhuoc diem:

- Mat mang luc ket thuc de fail ca session dong bo.
- Khong co telemetry gan realtime phia server.
- Payload ket thuc co the rat lon.

Danh gia:

- Chi hop cho case dac biet, khong phu hop nhu cau realtime.

## 3) Bang so sanh nhanh

| Option                   | Data loss risk           | Network cost                    | DB pressure        | Complexity  | Realtime                 | Production fit |
| ------------------------ | ------------------------ | ------------------------------- | ------------------ | ----------- | ------------------------ | -------------- |
| A Full replace           | Medium                   | Very high                       | Very high          | Low         | Medium                   | Low            |
| B 1 point/1 call         | Medium                   | Very high                       | High               | Low         | High                     | Low-Medium     |
| C Batch append           | Medium (neu khong local) | Medium                          | Medium             | Medium      | High                     | Medium-High    |
| D Chunk append           | Medium                   | Medium-Low                      | Low-Medium         | Medium-High | High                     | High           |
| E Room + batch sync      | Low                      | Medium-Low                      | Medium             | High        | High                     | Very high      |
| F Local then upload once | Low-Medium               | Low during session, high at end | Low during session | Medium      | Low-Medium (server side) | Medium         |

## 4) Khuyen nghi theo giai doan

### Giai doan 1 - Ship nhanh, dung duoc ngay

- Chon Option C.
- Them idempotency co ban:
  - unique theo (session_id, point_order)
  - insert bo qua duplicate
- Batch trigger theo 2 dieu kien:
  - du N points
  - hoac qua T giay

### Giai doan 2 - Tang do ben production

- Nang cap len Option E.
- Room luu point local voi trang thai:
  - PENDING
  - SYNCING
  - SYNCED
  - FAILED
- Worker retry co backoff.
- Complete chi cho phep sau khi khong con PENDING/FAILED.

### Giai doan 3 - Toi uu storage va scale lon

- Xem xet Option D (chunk) neu row growth la van de lon.
- Giu summary distance/duration tren bang session de query nhanh.

## 5) Dieu kien ky thuat quan trong (bat ke option)

- API append phai idempotent tren du lieu logic (khong nhan doi point khi retry).
- Khong append batch rong.
- pointOrder phai monotonic trong cung session.
- Complete phai dien ra sau flush append cuoi.
- Monitoring can co:
  - append success rate
  - duplicate reject count
  - average batch size
  - time-to-sync khi co offline

## 6) Ket luan

- Khong nen full replace route moi lan cap nhat.
- Huong can bang nhat hien tai la Batch append.
- Neu uu tien do ben va offline, Room + batch sync la huong tot hon cho production.
- Neu sau nay DB row phinh lon, nang cap tiep sang chunk append.
