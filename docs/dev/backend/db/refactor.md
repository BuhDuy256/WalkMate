# HANDOFF DOCUMENT: WalkMate Database Schema & Backend Architecture Update
**Date:** [Điền ngày hôm nay]
**Subject:** Database Normalization, JDBC Synchronization, and Core Architecture Decisions.

---

## 📌 Executive Summary
Tài liệu này ghi nhận lại toàn bộ những thay đổi quan trọng về mặt Database Schema và Backend Architecture (Java/Spring Boot) cho dự án WalkMate. Mục tiêu của đợt refactor này là:
1. Đồng bộ hóa cấu trúc Database với các thao tác thực tế trong `JdbcRepository`.
2. Khắc phục các lỗi Data Integrity và Runtime Exception (Thiếu cột, sai kiểu dữ liệu).
3. Chốt hạ 3 quyết định kiến trúc cốt lõi (ADRs) liên quan đến Gamification, AI Vector Matching và Geolocation.

---

## 🛠 PHASE 1: JDBC & Schema Synchronization (Sửa lỗi Runtime & Toàn vẹn dữ liệu)

Các thay đổi dưới đây nhằm đảm bảo Database khớp 100% với code Java hiện tại, ngăn chặn các lỗi sập DB khi `INSERT/UPDATE`:

1. **Chuẩn hóa Bảng `walk_session`:**
   - Đổi `user1_id`, `user2_id` thành `user_id_a`, `user_id_b`.
   - Đổi `actual_start_time` thành `started_at`, `actual_end_time` thành `ended_at`.
   - Bổ sung `meeting_point_lat` và `meeting_point_lng` (Java Entity yêu cầu khi tạo session).

2. **Xử lý `ON CONFLICT DO NOTHING` (UPSERT):**
   - Thêm `UNIQUE(follower_id, followee_id)` cho bảng `follow_relation`.
   - Thêm `UNIQUE(blocker_id, blocked_id)` cho bảng `block_relation`.
   *Lý do:* Nếu không có UNIQUE constraint, lệnh upsert trong SocialJdbcRepository sẽ bị lỗi fail ngầm.

3. **Tối ưu Bảng Danh mục (Lookup Tables):**
   - Loại bỏ bảng `badge`. Đổi khóa ngoại `badge_id` trong `user_badge` thành cột `badge_name (varchar)` ánh xạ trực tiếp từ Java Enum.
   - Áp dụng pattern tương tự cho `profile_tag` và `review_tag`. Giảm tải JOIN không cần thiết.

4. **Bảo vệ Toàn vẹn Dữ liệu (Data Integrity):**
   - Thêm `UNIQUE(session_id, chunk_index)` vào `session_point_chunks` để chặn spam GPS data.
   - Thêm `UNIQUE(session_id, reviewer_id)` vào `walk_review`.
   - Thêm Check Constraints: `CHECK (follower_id <> followee_id)`, `CHECK (blocker_id <> blocked_id)` để tránh user tự thao tác với chính mình.

5. **Fix Type & Enum:**
   - Map toàn bộ các custom type `USER-DEFINED` sang các Enum cụ thể trên DB (`walk_session_status`, `intent_status`, v.v.) khớp với Java Enum.
   - Sửa lỗi thiếu cột `read_at` trong bảng `notification`.
   - Sửa cột `followed_at` trong `follow_relation` (cũ là `created_at` gây lỗi ORDER BY trong code).

---

## 🏗 PHASE 2: Core Architecture Decisions (ADRs)

Đây là 3 quyết định kiến trúc quan trọng nhất đã được Lead/Dev team chốt sau quá trình phản biện:

### ADR 01: Xử lý vòng đời Gamification & Session Metrics
* **Vấn đề:** Ban đầu, `total_distance` và `total_duration` bị xóa khỏi DB vì không có lệnh INSERT trong code. Lỗi thực sự nằm ở `GamificationCommandService` chỉ tính toán điểm trong RAM ở phase `AFTER_COMMIT` mà không lưu lại quãng đường vào session. Hệ quả là mọi session đều có quãng đường = 0.
* **Quyết định (Đã fix):**
  - **DB:** Khôi phục `total_distance_km numeric(10,3)` và `total_duration_seconds bigint`.
  - **Java Entity (`WalkSession.java`):** Thêm method `recordFinalDistance(double km)`. Hàm `complete()` tự động tính duration từ `startedAt` đến `now()`.
  - **Flow mới (Dual-Transaction):** 1. `completeSession()` -> Gọi `save()` (Lưu duration - TX1).
    2. Event `AFTER_COMMIT` trigger `GamificationCommandService` tính toán polyline.
    3. `Gamification` gọi `session.recordFinalDistance(km)` -> Gọi `sessionRepository.save()` (Lưu distance - TX2).

### ADR 02: Chiến lược lưu trữ AI Vector Embeddings (`user_embedding`)
* **Vấn đề:** Cột vector bị đổi thành mảng `double precision[]` thuần túy, làm mất khả năng query AI Matching (Cosine Similarity).
* **Quyết định (Zero-Dependency & Native Extension):**
  - **DB:** Sử dụng extension `pgvector`. Kiểu dữ liệu chốt là `vector(768)`.
  - **Index:** Dùng chỉ mục **HNSW** (`vector_cosine_ops`) thay vì `IVFFlat` để tối ưu Recall rate cho lượng data vừa/nhỏ mà không cần phải chạy VACUUM/Train trước.
  - **Java Backend:** **KHÔNG** cài thêm thư viện third-party (như `pgvector-java`). Sử dụng `org.postgresql.util.PGobject` có sẵn của JDBC driver để thao tác. Dữ liệu được parse qua lại dưới dạng string `[x1, x2, ..., xn]`.
  - Tạo mới `UserEmbeddingJdbcRepository.java` chứa hàm `findKNearestUsers()` dùng toán tử `<=>`.

### ADR 03: Geolocation (PostGIS vs. Float) - YAGNI Principle
* **Vấn đề:** Có nên chuyển tọa độ từ 2 cột `double precision` sang dạng `GEOMETRY(Point, 4326)` của PostGIS ngay bây giờ không?
* **Quyết định: KHÔNG CHUYỂN NGAY (YAGNI).**
  - *Lý do:* Hiện tại, logic matching hoàn toàn dựa vào `hotspot_id` (So sánh chuỗi UUID). Tọa độ lat/lng chỉ dùng để Frontend render UI. Không có bất kỳ truy vấn không gian nào (như `ST_DWithin`) trong codebase.
  - Việc đưa PostGIS vào lúc này làm tăng độ phức tạp vận hành và bắt buộc phải viết parser WKT/WKB bên trong Java mà không mang lại giá trị thực tế.
  - *Migration Path:* Vẫn giữ 2 cột `double precision`. Đã note lại script nâng cấp trong `V0_init.sql` để dùng sau này khi tính năng "Tìm quanh đây" (Radius Search) chính thức được yêu cầu.

---

## 🚀 Action Items (For Next Developer)
Trước khi merge và deploy bản cập nhật này lên Production, vui lòng kiểm tra các Checklist sau:
- [ ] Chạy file SQL Migration mới nhất, đảm bảo extension `vector` và `citext` đã được Enable thành công trên Supabase/PostgreSQL.
- [ ] Kiểm tra class `GamificationCommandService.java`: Đảm bảo hàm listener của `AFTER_COMMIT` được đánh annotation `@Transactional(propagation = Propagation.REQUIRES_NEW)` (hoặc chạy `@Async`) để đảm bảo không dính dáng đến Transaction chính.
- [ ] Chạy Unit/Integration Test cho class `UserEmbeddingJdbcRepository.java` để xác nhận `PGobject` parse mảng Float thành chuỗi Vector hợp lệ.


● Verify lại toàn bộ:

  Searched for 2 patterns (ctrl+o to expand)

● Sạch hoàn toàn. Đây là tóm tắt:

  ---
  Tóm tắt 3 fix

  Fix 1 — Swagger @Tag không resolve (build.gradle)

  Nguyên nhân: springdoc-openapi-starter-webmvc-ui:2.6.0 không compatible với Spring Boot 3.5.x (Spring Boot 3.5 yêu cầu Spring Framework 6.2.x, springdoc 2.6 hỗ trợ đến
  Spring Boot 3.2).

  Fix: Nâng lên 2.8.0 — đây là version tối thiểu hỗ trợ Spring Boot 3.4/3.5. Import path io.swagger.v3.oas.annotations.tags.Tag trong controllers là đúng, không cần đổi.

  ▎ Sau khi sửa: chạy Gradle → Reload All Gradle Projects trong IDE để sync classpath.

  ---
  Fix 2 — Arrays.stream(float[]) không tồn tại (UserEmbeddingJdbcRepository.java)

  Nguyên nhân: Java chỉ có Arrays.stream(double[]), Arrays.stream(int[]), Arrays.stream(long[]) — không có overload cho float[] (primitive).

  Fix: Dùng IntStream.range(0, vector.length) để iterate theo index, lấy vector[i] từng phần tử:
  IntStream.range(0, vector.length)
      .mapToObj(i -> String.valueOf(vector[i]))
      .collect(Collectors.joining(",", "[", "]"));
  Đồng thời xóa import Arrays không còn dùng, thêm IntStream.

  ---
  Fix 3 — Xóa deprecated MATCHED & ACCEPTED khỏi toàn bộ codebase

  3 file Java đã thay đổi:

  ┌─────────────────────┬──────────────────────────────────────────────────────────────────────────┐
  │        File         │                                 Thay đổi                                 │
  ├─────────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ IntentStatus.java   │ Xóa @Deprecated MATCHED                                                  │
  ├─────────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ WalkIntent.java     │ Xóa || this.status == IntentStatus.MATCHED khỏi cả cancel() và consume() │
  ├─────────────────────┼──────────────────────────────────────────────────────────────────────────┤
  │ ProposalStatus.java │ Xóa @Deprecated ACCEPTED (không được dùng ở bất kỳ logic nào)            │
  └─────────────────────┴──────────────────────────────────────────────────────────────────────────┘

  V0_init.sql — 2 enum type đã được clean:
  -- Trước (sẽ gây lỗi nếu code gọi IntentStatus.MATCHED):
  CREATE TYPE intent_status AS ENUM ('OPEN', 'CONSUMED', 'CANCELLED', 'EXPIRED', 'MATCHED');

  -- Sau (sạch, khớp 100% với Java enum):
  CREATE TYPE intent_status  AS ENUM ('OPEN', 'CONSUMED', 'CANCELLED', 'EXPIRED');
  CREATE TYPE proposal_status AS ENUM ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED');

  Vì bạn vừa reset DB hoàn toàn, chỉ cần chạy lại từ V0_init.sql — không cần migration ALTER TYPE riêng.

✻ Crunched for 2m 59s
p