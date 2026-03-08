Rất chuẩn 👍
Dưới đây là phiên bản **viết lại toàn bộ danh sách câu hỏi và câu trả lời**, đã chỉnh lại để phù hợp với nguyên tắc mới:

> ❌ Không phải “1 active session duy nhất”
> ✔ Mà là “Không được có 2 session trùng time window”

Tất cả nội dung được điều chỉnh để phản ánh đúng invariant này.

---

# 📘 Domain Boundary & Maintainability Analysis – Scheduled Discovery (Non-Overlapping Sessions)

---

# 1. Vì sao phải tách `UserAccount` và `UserProfile`?

## 1.1. Identity ≠ Profile

`UserAccount` thuộc **Identity & Access context**:

- email
- password
- provider
- account status
- login

Trả lời câu hỏi:

> Người này có được phép vào hệ thống không?

---

`UserProfile` thuộc **Product Domain**:

- full_name
- bio
- avatar
- sở thích
- bán kính tìm kiếm

Trả lời câu hỏi:

> Người này là ai trong ngữ cảnh sản phẩm?

Hai thứ này không cùng bounded context.

---

## 1.2. Khác tốc độ thay đổi

- Profile thay đổi thường xuyên
- Account hiếm khi thay đổi

Nếu gộp chung:

- Update profile có thể ảnh hưởng identity
- Khó tách Identity service sau này

---

## 1.3. Khác concern bảo mật

UserAccount chứa thông tin nhạy cảm.
UserProfile chứa dữ liệu hiển thị công khai.

Gộp chung làm tăng rủi ro bảo mật.

---

## 1.4. Maintainability

Tách sẵn giúp:

- Dễ scale
- Dễ tách service
- Dễ evolve lâu dài

---

# 2. Vì sao `WalkIntent` không liên kết trực tiếp với `WalkSession`?

## 2.1. Hai phase khác bản chất

```text
Coordination Phase
    WalkIntent

Lifecycle Phase
    WalkSession
```

WalkIntent là ý định trong tương lai.
WalkSession là cam kết đã được xác nhận.

Không phải mọi intent đều tạo session.

---

## 2.2. Nếu liên kết trực tiếp sẽ gây coupling

Nếu đặt quan hệ cứng:

- Intent EXPIRED thì session xử lý sao?
- Intent CANCELLED thì session xử lý sao?
- Phải tạo session sớm (sai domain)
- Hoặc để nullable (thiết kế yếu)

---

## 2.3. Intent không sở hữu Session

Theo DDD:

- Aggregate không giữ reference object chéo
- Chỉ tham chiếu bằng ID

Session được tạo bởi Domain Service khi proposal CONFIRMED.

Sau đó:

- Intent có thể archive
- Session sống lifecycle độc lập

---

# 3. Vì sao thiết kế này là Maintainability-oriented?

Vì nó tối ưu cho:

- Thay đổi business rule
- Giảm cognitive load
- Tách context rõ ràng
- Event-driven sạch
- Evolvability dài hạn

---

# 4. AI Matching có thuộc WalkIntent không?

Không.

WalkIntent chịu trách nhiệm:

- Time window
- Location
- Filter
- State

AI Matching là:

- Ranking logic
- Phụ thuộc embedding
- Phụ thuộc behavior
- Phụ thuộc trust score

Nếu đặt trong WalkIntent:

- Vi phạm SRP
- Aggregate phình to
- Khó test
- Khó scale AI

Boundary đúng:

```text
WalkIntent (Aggregate)
MatchingDomainService
AIService
```

AI chỉ tính score, không thay đổi state.

---

# 5. Badge là gì?

Badge là:

- Mốc thành tích
- Gamification
- Tín hiệu xã hội
- Feature đầu vào cho AI

Badge ≠ TrustScore.

Badge:

- Immutable
- Mang tính ghi nhận

TrustScore:

- Tăng/giảm
- Mang tính đánh giá

Badge thuộc Trust & Reputation context.
WalkSession chỉ phát event.

---

# 6. Các câu hỏi boundary quan trọng (phiên bản non-overlapping session)

---

## 6.1. ChatRoom thuộc ai?

Chat chỉ tồn tại trong context WalkSession.

Nếu độc lập:

- Có thể nhắn ngoài session
- Sai boundary

---

## 6.2. TrustScore có nên nằm trong UserProfile?

Không.

TrustScore là derived aggregate,
bị thay đổi bởi domain event,
cần audit rõ ràng.

---

## 6.3. ReviewTag có nên là JSON?

JSON tiện nhưng:

- Khó query
- Khó thống kê
- Khó train AI

Entity rõ ràng dễ evolve hơn.

---

## 6.4. Ai sở hữu việc tính Badge?

Không phải WalkSession.

Badge nên lắng nghe event từ Session.

Nếu nhét vào Session:

- Phình aggregate
- Khó maintain

---

## 6.5. Reliability nên tính realtime hay event-driven?

Event-driven:

- Decoupled
- Dễ mở rộng

Tính trực tiếp trong session:

- Coupling cao
- Khó rollback

---

## 6.6. WalkSession nên giữ User object hay user_id?

Chỉ giữ ID.

Không giữ entity chéo aggregate.

---

## 6.7. WalkIntent có nên chứa candidate list?

Không.

Candidate list là query result,
không phải state domain.

---

## 6.8. Invariant về session nên là gì?

Không phải:

> 1 user chỉ có 1 active session.

Mà phải là:

> 1 user không được có 2 session có time window overlap với status ∈ (PENDING, ACTIVE).

Nếu không xác định rõ:

- Có thể tạo 2 session trùng giờ
- Race condition xảy ra

---

## 6.9. MatchProposal có nên là aggregate riêng?

Có.

Vì:

- Có lifecycle riêng
- Có timeout
- Không nên nhét vào WalkIntent

---

## 6.10. AI có nên điều chỉnh TrustScore?

Không.

AI là consumer.
TrustScore là authority.

---

## 6.11. Embedding có nên update trong transaction WalkSession?

Không.

Nên async để:

- Tránh transaction dài
- Tránh coupling ML layer

---

## 6.12. WalkSession có nên mutable?

Terminal states nên immutable:

- COMPLETED
- NO_SHOW
- CANCELLED

Giữ lịch sử chính xác.

---

## 6.13. Có nên cho sửa Review?

Cho sửa:

- AI nhiễu

Không cho sửa:

- Người dùng không chỉnh được sai sót

Đây là policy decision.

---

## 6.14. Nếu user có nhiều session tương lai thì sao?

Hợp lệ.

Chỉ cần:

- Không trùng time window

Domain phải cho phép nhiều session ở các thời điểm khác nhau.

---

## 6.15. Nếu thêm trạng thái RESCHEDULED thì sao?

Scheduled system rất dễ phát sinh:

- Dời giờ
- Đổi địa điểm

State machine phải đủ linh hoạt
nhưng không được phá invariant “no overlap”.

---

# 7. Meta-question quan trọng nhất

> Entity nào quyết định sự thật cuối cùng của một buổi đi dạo?

Trong mô hình này:

WalkSession là single source of truth cho lifecycle.

Nhưng invariant đúng là:

> Không có 2 WalkSession trùng thời gian cho cùng một user.

Nếu không rõ:

- Domain sẽ rối
- Penalty sai
- TrustScore sai
- Concurrency bug xuất hiện

---

# 8. Kết luận

Các câu hỏi trên tập trung vào:

1. Boundary giữa context
2. Ownership của lifecycle
3. Reference vs coupling
4. Invariant placement
5. Correct definition của “active session”

Thiết kế theo DDD (Scheduled-only, Non-overlapping Sessions) giúp:

- Tránh double source of truth
- Tránh state duplication
- Cho phép nhiều session tương lai
- Ngăn trùng giờ
- Giữ aggregate boundary sạch
- Dễ evolve
- Dễ thêm AI mà không phá lifecycle
