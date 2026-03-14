# Báo cáo đối chiếu DB Schema với Domain Lifecycle cho Walk Intent

Dựa trên hướng dẫn từ `3-from-lifecycle-spec-to-data-model.md` và tài liệu thiết kế nghiệp vụ của `WalkIntent` (`domain-lifecycle-stresstest.md`, `use-cases.md`), tôi đã tiến hành kiểm tra (validate) bảng `walk_intent` và các ràng buộc liên quan trong file `current-db.sql`.

Mục tiêu là đảm bảo cơ sở dữ liệu (Database) thực sự bảo vệ được các Invariants cuối cùng của Domain, chứ không chỉ là nơi lưu trữ data.

Dưới đây là kết quả đối chiếu:

## 1. MAPPING DOMAIN CHUẨN XÁC
*   **Aggregate Root:** Bảng `walk_intent` đã được tạo tương ứng thành Main Table (có `intent_id` làm khóa chính).
*   **Value Objects:**
    *   `Location Snapshot` được map rất tốt sang 3 cột: `location` dạng `GEOGRAPHY(POINT, 4326)`, `location_lat`, và `location_lng`.
    *   `Time Window` được map chặt chẽ sang 2 cột `time_window_start` và `time_window_end`.
    *   `Matching Constraints` được lưu dưới dạng cột `matching_constraints` kiểu `JSONB` rât linh hoạt cho các bộ lọc giới tính, tuổi tác, tags... mà không cần làm phình to DB.

## 2. STATE MACHINE VÀ LIFECYCLE (CẦN FIX NHẸ)
*   **Thiết kế Lifecycle:** Yêu cầu các state `DRAFT`, `OPEN`, `CONSUMED`, `CANCELLED`, `EXPIRED`.
*   **Thực tế DB (Dòng 123):** 
    ```sql
    CREATE TYPE intent_status AS ENUM ('OPEN', 'EXPIRED', 'CANCELLED', 'CONSUMED');
    ```
*   **Nhận xét:** Đã map đúng và đủ các terminal states theo Enum. Mặc dù thiếu `DRAFT`, nhưng `DRAFT` thường không cần thiết phải lưu DB nếu nó chỉ nằm trên cache local của mobile app trước khi người dùng bấm "Find a Buddy" (hay hành động `SubmitWalkIntent`). Tuy nhiên, nếu user story yêu cầu lưu nháp trên server để làm tiếp trên thiết bị khác, ta sẽ phải bổ sung `DRAFT` vào DB. Hiện tại, coi như việc DRAFT chỉ là state phía Frontend. => **Pass**.

## 3. INVARIANTS VÀ CONSTRAINTS (DB BẢO VỆ DOMAIN TUYỆT VỜI)

*   **Logic Constraint cơ bản (Dòng 140-146):**
    *   `valid_time_window`: `time_window_end > time_window_start` -> **Pass**.
    *   `future_time_window`: `time_window_start > created_at` (Bảo vệ rule gốc `I-6` điểm neo thời gian tương lai) -> **Pass**.
    *   `valid_coordinates`: Lat/Lng hợp lệ -> **Pass**.

*   **(QUAN TRỌNG NHẤT) Invariant I-1: Chống trùng lịch OPEN Intent (Dòng 149-156)**
    *   **Spec:** Một user không được có nhiều WalkIntent ở trạng thái `OPEN` đè lên nhau về thời gian.
    *   **Thực tế DB:**
        ```sql
        ALTER TABLE walk_intent ADD CONSTRAINT no_overlapping_open_intents 
            EXCLUDE USING gist (
                user_id WITH =, 
                tsrange(time_window_start, time_window_end) WITH &&
            ) 
            WHERE (status = 'OPEN');
        ```
    *   **Nhận xét:** Đây là đỉnh cao của Aggregate protection. Bất chấp Application code lỗi, có bypass check cỡ nào, DB Constraint cấp độ GiST EXCLUDE này đảm bảo I-1 sẽ không bao giờ vỡ dẫu cho có bị race-condition. -> **Perfect 10/10**.

## 4. STRESS TEST VÀ CONCURRENCY (THIẾU SÓT OMINOUS)

*   **Invariant I-7 (Expiry Lock Safety):** Khi quét chuyển trạng thái từ `OPEN` sang `EXPIRED`, tài liệu Domain yêu cầu dùng `SELECT ... FOR UPDATE SKIP LOCKED` để tránh đua lệnh với luồng Consume từ MatchProposal. Dù lệnh này nằm ở tầng Application, nhưng cần một cơ chế để bảo vệ state mutation tổng thể hơn (tấn công State Mutation).
*   **Vấn đề:** Bảng `walk_intent` **ĐANG THIẾU CỘT VERSION** để hỗ trợ Optimistic Locking. Trong ví dụ của WalkSession ở cuối file SQL (Dòng 744: "Add optimistic locking version to walk_session"), chúng ta đã cẩn thận sửa bảng Session. Nhưng `walk_intent` cũng đối mặt với Race Condition y hệt (ví dụ, User vừa bấm Cancel thì Cronjob vừa chạy Expire, hoặc Proposal vừa Confirm = Consume).
*   **Kiến nghị (Take Action):** Cần update file `current-db.sql` bổ sung cột `version BIGINT NOT NULL DEFAULT 0` cho bảng `walk_intent` để Application BE có thể query theo kiểu `UPDATE ... SET status='CONSUMED', version=version+1 WHERE id=? AND version=?`.

---
**KÊT LUẬN:** 
Database schema cho bảng `walk_intent` hiện tại rất khỏe, chuẩn xác tới 90% so với thiết kế Domain Driven Design. GiST constraint hoạt động như một "khiên chặn sát thương" triệt để cho rule khó nhất là I-1 (Chống trùng lịch hẹn mở). Khuyết điểm duy nhất là **cần bổ sung Version column cho Optimistic locking** để chống race condition cho các State Mutation.
