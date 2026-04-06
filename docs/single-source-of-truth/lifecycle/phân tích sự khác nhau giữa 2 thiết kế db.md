# Kiến trúc Cơ sở dữ liệu WalkMate: Phân tích và Đánh đổi (Architecture & Trade-offs)

Tài liệu này trình bày hai trường phái thiết kế cơ sở dữ liệu cho hệ thống WalkMate, bao gồm lược đồ rút gọn, triết lý cốt lõi và bài toán đánh đổi (Trade-offs) của từng phương pháp.

---

## 1. Hai Trường phái Thiết kế (Database Schemas)

### Thiết kế 1: Database-Centric (Lịch trình tập trung - Centralized Schedule)

_Kiểu thiết kế này tách biệt logic khóa thời gian ra một bảng riêng (`user_schedule`) làm Nguồn sự thật duy nhất (SSOT)._

- **`user_schedule`**`(id UUID, user_id UUID, hotspot_id UUID, start_time TIMESTAMP, end_time TIMESTAMP, status ENUM [ACTIVE, RELEASED], intent_id UUID NULL, session_id UUID NULL)` _(Constraint: intent_id XOR session_id)_
- **`walk_intent`**`(id UUID, user_id UUID, hotspot_id UUID, status ENUM [OPEN, MATCHING, CONSUMED, CANCELLED, EXPIRED], pref_min_age INT, pref_max_age INT, pref_gender ENUM, is_private BOOL, invited_friend_id UUID NULL, description TEXT, has_pet BOOL, created_at TIMESTAMP, expired_at TIMESTAMP)`
- **`match_proposal`**`(id UUID, intent_a_id UUID, intent_b_id UUID, status ENUM [PENDING, CONFIRMED, REJECTED, EXPIRED], accepted_by_a BOOL, accepted_by_b BOOL, created_at TIMESTAMP, confirmed_at TIMESTAMP NULL, expired_at TIMESTAMP)`
- **`walk_session`**`(id UUID, proposal_id UUID, status ENUM [PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED, ABORTED], activated_by_a BOOL, activated_by_b BOOL, created_at TIMESTAMP, started_at TIMESTAMP NULL, ended_at TIMESTAMP NULL, distance_meters FLOAT, path_data_url VARCHAR)`

### Thiết kế 2: Domain-Driven Design (Hướng Nghiệp vụ / Snapshotting)

_Kiểu thiết kế này coi mỗi thực thể là một Aggregate độc lập. Thời gian và địa điểm được "chụp" (snapshot) và lưu trữ trực tiếp vào từng giai đoạn._

- **`walk_intent`**`(intent_id UUID, hotspot_id UUID, user_id UUID, time_window_start TIMESTAMP, time_window_end TIMESTAMP, matching_constraints JSONB, status ENUM, created_at TIMESTAMP, expires_at TIMESTAMP, version BIGINT)`
- **`match_proposal`**`(proposal_id UUID, intent_id_a UUID, intent_id_b UUID, proposed_start_time TIMESTAMP, proposed_end_time TIMESTAMP, proposed_location_lat FLOAT, proposed_location_lng FLOAT, accepted_by_a BOOL, accepted_by_b BOOL, status ENUM, created_at TIMESTAMP, expires_at TIMESTAMP, confirmed_at TIMESTAMP)`
- **`walk_session`**`(session_id UUID, proposal_id UUID, user_id_a UUID, user_id_b UUID, meeting_point_lat FLOAT, meeting_point_lng FLOAT, scheduled_start TIMESTAMP, scheduled_end TIMESTAMP, status ENUM, created_at TIMESTAMP, started_at TIMESTAMP, ended_at TIMESTAMP, user_a_activated_at TIMESTAMP, user_b_activated_at TIMESTAMP, cancellation_reason VARCHAR, cancelled_by UUID, abort_reason VARCHAR, version BIGINT, total_distance_km NUMERIC, total_duration_seconds BIGINT, source_intent_id_a UUID, source_intent_id_b UUID)`

---

## 2. Triết lý Thiết kế (Design Philosophies)

### Triết lý của Thiết kế 1 (Database-Centric)

- **"Database là lá chắn cuối cùng"**: Mọi logic về toàn vẹn dữ liệu (đặc biệt là không được trùng lịch) phải được giải quyết ở tầng vật lý của CSDL.
- **Exclusive Arc (Cung loại trừ)**: Sử dụng bảng `user_schedule` làm một "Siêu kiểu" (Supertype) để khóa không gian/thời gian. Nó ép Database phải đảm bảo một người không bao giờ xuất hiện ở 2 nơi cùng lúc bằng các ràng buộc (Constraints) cứng.

### Triết lý của Thiết kế 2 (Domain-Driven Design)

- **"Nghiệp vụ (Code) là trung tâm"**: Database chỉ là nơi lưu trữ trạng thái (Persistence Layer). Mọi logic phức tạp phải được xử lý ở tầng Backend (Domain Services).
- **Decoupling & Event Sourcing (Giảm phụ thuộc & Lưu vết)**: Mỗi bảng có thể tự sống độc lập. Dữ liệu được mang theo (snapshot) từ Intent sang Proposal và chốt cứng ở Session. Nếu sau này có thay đổi về Hotspot, lịch sử chuyến đi cũ không bị ảnh hưởng.

---

## 3. Phân tích Đánh đổi (Trade-offs)

| Tiêu chí Đánh giá                       | Thiết kế 1 (Database-Centric)                                                                            | Thiết kế 2 (Domain-Driven Design)                                                                                                      |
| :-------------------------------------- | :------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------- |
| **Kiểm tra trùng lịch (Overlap Check)** | **Tối ưu cực đại.** Chỉ tốn 1 câu lệnh SQL query vào bảng `user_schedule` (kết hợp GiST Index).          | **Phức tạp.** Tầng code (Backend) phải query đồng thời vào bảng `walk_intent` và `walk_session` rồi tự so sánh.                        |
| **An toàn Dữ liệu (Data Anomaly)**      | **Cao.** Không bao giờ có lỗi lọt khe (Race Condition) sinh ra 2 lịch trùng nhau nhờ SQL Constraint.     | **Trung bình.** Dễ bị lọt khe nếu Backend code thiếu Transaction hoặc không Lock đúng cách.                                            |
| **Mapping với Code (ORM Friendliness)** | **Thấp.** Bảng `user_schedule` tạo ra sự phụ thuộc chéo. Dùng JPA/Hibernate sẽ rất khó cấu hình Cascade. | **Tuyệt vời.** Các Entity độc lập, thuộc tính rõ ràng. Áp dụng Khóa lạc quan (Optimistic Locking) cực kỳ mượt mà qua trường `version`. |
| **Tính Lưu vết & Lịch sử (Auditing)**   | **Trung bình.** Phải JOIN nhiều bảng để ra thông tin chuyến đi.                                          | **Cực cao.** Mọi thông tin (thời gian, tọa độ, người hủy, lý do) được snapshot thẳng vào bảng `walk_session`.                          |
| **Sẵn sàng cho Microservices**          | **Kém.** Bảng `user_schedule` là cổ chai. Khó tách Service.                                              | **Tốt.** Dễ dàng tách `IntentService` và `SessionService` ra 2 Database khác nhau nếu cần scale.                                       |

---

## 4. Kết luận & Ứng dụng thực tế

- **Chọn Thiết kế 1 (Database-Centric)**: Nếu hệ thống được viết bằng các framework cũ, hoặc team Backend yếu về xử lý đồng thời (Concurrency), muốn đẩy toàn bộ gánh nặng bảo vệ dữ liệu cho PostgreSQL xử lý.
- **Chọn Thiết kế 2 (Domain-Driven Design)**: Nếu dự án được xây dựng theo kiến trúc Software Engineering hiện đại (Java Spring Boot, NodeJS, Microservices). Đây là **thiết kế được khuyên dùng cho Đồ án**, vì nó thể hiện tư duy hướng đối tượng (OOP) sắc bén, cấu trúc ORM sạch sẽ, và khả năng lưu vết sự cố (Auditing) hoàn hảo phục vụ cho vận hành thực tế.
