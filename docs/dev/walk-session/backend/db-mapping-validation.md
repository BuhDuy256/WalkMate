# Đối chiếu Database Schema (`db.sql`) với Domain Spec & Hướng dẫn Mapping

Tài liệu này đánh giá table `walk_session` trong file `db.sql` để đối chiếu xem liệu schema hiện tại có thỏa mãn các nguyên tắc map từ Domain → Database (được quy định trong `3-from-lifecycle-spec-to-data-model.md`) dựa trên các Spec của WalkSession hay không.

## 🟩 1. Điểm TỐT - Đã tuân thủ chuẩn Mapping

**1. Lifecycle → ENUM Column (Tuân thủ Bước 2)**
- `db.sql` đã định nghĩa chính xác ENUM `session_status` với các giá trị: `'PENDING', 'ACTIVE', 'COMPLETED', 'NO_SHOW', 'CANCELLED', 'ABORTED'`. 
- Sự đồng nhất 100% với SSOT Lifecycle cho thấy ranh giới hệ thống được bảo vệ ngay từ định dạng Data Type gốc. Không có state rác "PAUSED".

**2. Invariants cơ bản → CHECK Constraints (Tuân thủ Bước 4)**
Các invariant mức độ single-row đã được map rất chuẩn chỉ bằng DB Constraints:
- `different_users CHECK (user1_id != user2_id)`: Đảm bảo Session có 2 người khác nhau (Invariant X-3).
- `valid_scheduled_time CHECK (scheduled_end_time > scheduled_start_time)`: Chống dữ liệu rác về thời gian lịch trình dự kiến.
- `valid_actual_time CHECK (actual_end_time > actual_start_time)`: Đảm bảo quy tắc thời gian thực tế luôn tiến về phía trước.

**3. Domain Events → Event Tables (Tuân thủ Bước 6)**
- Đã tồn tại bảng `session_state_change_log` và có một **Database Trigger (`trg_log_session_state`) tự động** ghi đè log nếu `status` thay đổi. Điều này làm rất xuất sắc mục tiêu lưu vết Event để audit và debug.

---

## 🟥 2. Điểm THIẾU SÓT LỚN - Cần sửa đổi `db.sql`

Khi soi chiếu với **Stress Test** và **Invariants phức tạp**, `db.sql` đang để lộ những lỗ hổng chí mạng (Final Defense của DB bị thủng).

### ❌ Lỗi 1: Không có Optimistic Locking (Vi phạm quy tắc Stress Test)
Theo phân tích Stress Test (Muc 5 trong file `3-from-lifecycle-spec-to-data-model.md`), WalkSession xảy ra "State Mutation Race" (User A bấm Cancel, User B bấm Activate cùng lúc).
- **Thiếu sót:** Bảng `walk_session` hiện tại **KHÔNG CÓ CỘT `version` integer**. 
- **Hậu quả:** Hệ thống Backend sẽ gặp khó khăn để implement Optimistic Locking (thường ORM như Hibernate / Prisma chỉ cần cột `@Version`). Cách dùng khóa bi quan (SELECT FOR UPDATE) sẽ gây bottleneck lớn cho database.
- **Khuyến nghị bổ sung:** `ALTER TABLE walk_session ADD COLUMN version INTEGER NOT NULL DEFAULT 1;`

### ❌ Lỗi 2: Invariant `S-8` (Terminal Immutability) bị map sai, tạo ảo giác an toàn
- **Thiếu sót:** Ở line 257 của `db.sql` có viết Constraint:
  ```sql
  CONSTRAINT terminal_immutable CHECK (
      -- Terminal states cannot be modified (application enforced)
      status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'NO_SHOW', 'CANCELLED', 'ABORTED')
  )
  ```
- **Hậu quả:** Constraint `CHECK` này **chỉ kiểm tra xem dữ liệu nhập vào có hợp lệ trong chuỗi ENUM không**, chứ hoàn toàn **KHÔNG ngăn chặn** việc UPDATE một Session đang `COMPLETED` ngược lại thành `ACTIVE` (bước lùi State Machine).
- **Khuyến nghị bổ sung:** Cần tạo một `BEFORE UPDATE TRIGGER` trên bảng `walk_session`. Nếu `OLD.status` là một trạng thái Terminal (COMPLETED, CANCELLED...), thì `RAISE EXCEPTION` để cấm DB thực thi khóa việc update.

### ❌ Lỗi 3: Invariant `S-2` (No Overlapping Sessions) DB rũ bỏ trách nhiệm
- **Thiếu sót:** `db.sql` nhận định `Invariant 1: Complex overlap detection - enforced at application layer` và từ chối chặn Constraint ở mức DB. Dù có viết Function `check_session_overlap` nhưng lại **không gắn TRIGGER** nào cả.
- **Hậu quả:** Đi ngược lại mục *10. Quy tắc vàng (Application = first defense, Database = final defense)* của tài liệu Mapping. Nếu Backend bị bug Concurrency, DB sẽ lưu 2 session trùng lịch của 1 user.
- **Khuyến nghị bổ sung:** Tạo thêm `BEFORE INSERT OR UPDATE TRIGGER` gọi thẳng tới cái Function `check_session_overlap` hiện có để chặn đứng thao tác ghi đè song song trực tiếp dưới DB. 

### ❌ Lỗi 4: Mất tích Tracking Metrics (Value Objects)
- **Thiếu sót:** Mặc dù Use Case "End/CompleteWalkSession" có nhắc đến thu thập số liệu (Duration, Steps, Distance, Calories), bảng `walk_session` **trắng trơn** các Value Objects này. Điểm này vi phạm nguyên tắc "Value Objects -> Embedded Columns".
- **Khuyến nghị bổ sung:** Cần nhúng thêm các cột như:
   `total_steps INTEGER`, `total_distance_meters DOUBLE PRECISION`, `total_calories INTEGER`.

---

## 🎯 Tổng kết

Bản DB `db.sql` hiện tại đã đi đúng triết lý Domain-Driven Design được ~70%, đặc biệt làm tốt phần ENUM, Check Constraints cơ bản và Trigger lưu log (bước 1, 2, 6). 

Tuy nhiên, do bỏ quên thiết kế **Stress Test phòng thủ Concurrency** (Cột `version`), và **bỏ lỏng State Transition / Overlap Invariants** dưới DB (buông Guardrail cho App Layer), DB này đang vi phạm triết lý **"Database = Final Defense"** của tài liệu hướng dẫn Mapping. Cần update lại schema để đóng các lỗ hổng này.
