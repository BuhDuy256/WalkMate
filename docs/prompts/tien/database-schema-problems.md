# Phân Tích Kiến Trúc Database WalkMate

## Vì sao thiết kế hiện tại chưa phù hợp với Domain-Driven Design (DDD)

---

# 1. Vấn đề cốt lõi không nằm ở SQL

Thiết kế hiện tại có nhiều điểm mạnh:

- Chuẩn hóa dữ liệu tốt (3NF tương đối sạch)
- Constraint đầy đủ (CHECK, UNIQUE, self-exclusion)
- Business rules được mô tả chi tiết
- Có tư duy production-ready

Vấn đề không nằm ở kỹ thuật SQL.

Vấn đề nằm ở **cách mô hình hóa domain**.

---

# 2. Sai lệch quan trọng: Trộn Coordination Phase và Lifecycle Phase

Hiện tại tồn tại ba thực thể chính liên quan đến việc đi dạo:

```text
instant_walk
scheduled_walk
walking_sessions
```

Trong đó:

- instant_walk và scheduled_walk có đầy đủ state machine
- walking_sessions cũng có trạng thái riêng

Điều này dẫn đến:

> Một hành vi domain nhưng có nhiều nơi lưu trạng thái.

---

## 2.1. Phân tích theo bản chất domain

Trong hệ thống này thực chất có 2 giai đoạn khác nhau:

### Giai đoạn 1 – Coordination (Ý định)

- Tìm người
- Match
- Confirm thời gian
- Có thể hủy trước khi diễn ra

### Giai đoạn 2 – Lifecycle (Thực thi)

- Bắt đầu đi bộ
- Đang diễn ra
- Hoàn thành / No-show / Cancel

Hai giai đoạn này khác bản chất:

- Coordination là pre-value
- Lifecycle là value realization

Thiết kế hiện tại gộp hai phase này vào cùng một thực thể (instant_walk).

Đây là điểm lệch với DDD.

---

# 3. Double Source of Truth (Lỗi thiết kế nghiêm trọng)

Hiện tại tồn tại hai nơi lưu trạng thái thực thi:

```text
instant_walk.status
walking_sessions.status
```

Ví dụ:

- instant_walk.status = IN_PROGRESS
- walking_sessions.status = IN_PROGRESS

Nếu một trong hai update thất bại:

- Trạng thái hệ thống không còn nhất quán
- Có thể tính điểm sai
- Có thể mở chat sai
- Có thể cho phép review sai

Trong DDD:

> Một aggregate phải là single source of truth cho lifecycle của nó.

Thiết kế hiện tại vi phạm nguyên tắc này.

---

# 4. Không có Aggregate Root rõ ràng

Câu hỏi kiến trúc quan trọng:

> Entity nào bảo vệ invariant: "Một user chỉ có 1 active session"?

Hiện tại invariant này phải check qua:

- instant_walk
- scheduled_walk
- walking_sessions
- session_participants

Invariant không nằm trong một aggregate cụ thể.

Điều này dẫn đến:

- Logic phân tán
- Phụ thuộc transaction cross-table
- Dễ bug khi concurrent

Trong DDD:

> Aggregate Root phải chịu trách nhiệm bảo vệ invariant của nó.

---

# 5. Các Bug Consistency Có Thể Xảy Ra

## 5.1. Mismatch trạng thái

Trường hợp:

- instant_walk.status = COMPLETED
- walking_sessions.status = IN_PROGRESS

Hậu quả:

- Chat chưa đóng
- Review đã được tạo
- Reliability đã được cộng
- Session vẫn đang active

---

## 5.2. Double penalty

No-show xảy ra.

- instant_walk xử lý penalty
- walking_sessions xử lý penalty

Reliability bị trừ hai lần.

---

## 5.3. Vi phạm invariant 1 active session

Do state phân tán, nếu transaction race condition xảy ra:

- User có thể tham gia 2 session cùng lúc.

---

## 5.4. Review được tạo sai

Nếu chỉ check instant_walk.status = COMPLETED nhưng session thực tế đã CANCELLED:

Review vẫn được tạo.

---

# 6. Vì sao cần phân tích theo DDD

DDD không phải là “phức tạp hóa thiết kế”.

DDD nhằm trả lời 3 câu hỏi:

1. Đâu là ranh giới của một hành vi domain?
2. Đâu là entity chịu trách nhiệm bảo vệ invariant?
3. Đâu là single source of truth?

Trong hệ thống này:

- WalkIntent thuộc coordination
- WalkSession thuộc lifecycle

Khi tách đúng:

```text
WalkIntent → chỉ xử lý matching & confirm
WalkSession → quản lý toàn bộ lifecycle
```

Khi đó:

- Không còn duplicate state
- Không còn double source of truth
- Invariant nằm đúng chỗ
- Event-driven rõ ràng
- Dễ scale

---

# 7. Vấn đề của thiết kế hiện tại không phải là "sai SQL"

Thiết kế hiện tại:

- Đúng theo hướng database-first
- Đúng theo flow UI
- Đúng theo cách xây dựng feature

Nhưng:

- Không align theo domain-first
- Không xác định aggregate boundary
- Không tách phase logic

Khi hệ thống nhỏ → ít vấn đề.
Khi hệ thống lớn, concurrent cao → bug consistency xuất hiện.

---

# 8. Kết luận kỹ thuật

Thiết kế hiện tại có chất lượng SQL tốt nhưng có các vấn đề kiến trúc sau:

1. Trộn Coordination và Lifecycle.
2. Duplicate lifecycle state.
3. Không có Aggregate Root rõ ràng.
4. Double source of truth.
5. Invariant bị phân tán.
6. Nguy cơ race condition và inconsistency khi scale.

Phân tích theo DDD là cần thiết vì:

- DDD buộc phải xác định rõ ranh giới hành vi.
- DDD buộc phải xác định rõ aggregate chịu trách nhiệm.
- DDD loại bỏ duplicate state.
- DDD giảm rủi ro inconsistency ở mức kiến trúc.
