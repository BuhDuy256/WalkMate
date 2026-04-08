# Gamification Problems - Current Status

Updated: 2026-04-08
Scope: Session lifecycle -> Gamification reward/penalty -> Badge persistence

## 1) HIGH - Race condition khi ghi GPS chunk theo session chung

### Vấn đề
Tracking chunk index đang được cấp theo session chung (không theo user). Nếu 2 user cùng push GPS gần như đồng thời:
- Cả 2 request có thể lấy cùng 1 chunk_index.
- Insert thứ 2 sẽ đụng UNIQUE(session_id, chunk_index).
- Dữ liệu route có thể thiếu hoặc bị fail ngẫu nhiên theo timing.

### Bằng chứng
- backend/src/main/java/com/walkmate/application/tracking/TrackingCommandService.java:88
- backend/src/main/java/com/walkmate/application/tracking/TrackingCommandService.java:89
- backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java:18
- backend/src/main/java/com/walkmate/infrastructure/repository/tracking/TrackingChunkJdbcRepository.java:46
- backend/src/main/resources/db/migration/V1__init.sql:410

### Ảnh hưởng
- Mất/chồng chunk GPS.
- Session distance không ổn định.
- Điểm và badge có thể sai do thiếu dữ liệu route.

## 2) HIGH - Rủi ro tính distance/points bị đội khi gom polyline toàn session

### Vấn đề
Gamification đang cộng tổng tất cả polylines theo session. Nếu cả 2 user đều upload route riêng, tổng distance có thể bị cộng 2 lần (hoặc lệch đáng kể).

### Bằng chứng
- backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java:146
- backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java:149
- backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java:89

### Ảnh hưởng
- total_distance_km của session sai.
- totalPoints và badge milestone theo distance bị sai.
- Leaderboard bị lệch.

## 3) MEDIUM - Trust badge có thể cấp chậm hơn kỳ vọng

### Vấn đề
Badge trust (TRUSTED_WALKER, HIGHLY_TRUSTED) được evaluate trong flow session complete, nhưng trust score lại có thể thay đổi ở flow review.
Nếu user vừa đạt ngưỡng trust sau review, badge có thể chưa được cấp ngay, phải chờ lần complete session tiếp theo.

### Bằng chứng
- backend/src/main/java/com/walkmate/domain/gamification/BadgePolicy.java:34
- backend/src/main/java/com/walkmate/domain/gamification/BadgePolicy.java:35
- backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java:122
- backend/src/main/java/com/walkmate/application/review/ReviewCommandService.java:81
- backend/src/main/java/com/walkmate/application/review/ReviewCommandService.java:82

### Ảnh hưởng
- UX không nhất quán: trust đã tăng nhưng badge chưa xuất hiện ngay.

## 4) MEDIUM - Inconsistency giữa enum SessionOutcome và luồng penalty thực tế

### Vấn đề
SessionOutcome có CANCELLED(-5), nhưng GamificationCommandService hiện chỉ apply penalty cho NO_SHOW và ABORTED qua event listener.
Flow cancelSession không publish event penalty.

### Bằng chứng
- backend/src/main/java/com/walkmate/domain/review/SessionOutcome.java:13
- backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java:66
- backend/src/main/java/com/walkmate/application/gamification/GamificationCommandService.java:77
- backend/src/main/java/com/walkmate/application/session/SessionCommandService.java:93

### Ảnh hưởng
- Rule business khó hiểu: enum định nghĩa penalty nhưng runtime không áp dụng cho CANCELLED.

## 5) MEDIUM - Thiếu test tự động cho gamification/session lifecycle

### Vấn đề
Hiện không có test backend riêng cho:
- SessionCompleted/NoShow/Aborted events.
- Award badge theo milestones.
- Race/concurrency khi upload tracking chunks.

### Bằng chứng
- Không tìm thấy test trong backend/src/test/java cho các thành phần trên.

### Ảnh hưởng
- Dễ regress logic khi refactor.
- Khó chứng minh tính đúng của điểm/badge/trust flow.

## 6) NOTE (không phải lỗi runtime) - user_badge và badge không nối FK là thiết kế hiện tại

### Trạng thái
Schema hiện tại cố ý lưu badge_name trực tiếp trong user_badge (map từ Java enum), không dùng badge_id FK sang bảng badge.

### Bằng chứng
- backend/src/main/resources/db/migration/V1__init.sql:160
- backend/src/main/resources/db/migration/V1__init.sql:164
- backend/src/main/resources/db/migration/V1__init.sql:165
- backend/src/main/resources/db/migration/V1__init.sql:243
- backend/src/main/java/com/walkmate/infrastructure/repository/gamification/UserBadgeJdbcRepository.java:37

### Ý nghĩa
- Chạy được và phù hợp với code hiện tại.
- Nhưng dễ gây nhầm lẫn vì tồn tại đồng thời bảng badge metadata mà không được join/reference trong Java layer.

## Verification snapshot

- Backend compile status: PASS
- Command: ./gradlew :backend:compileJava
- Result: BUILD SUCCESSFUL

