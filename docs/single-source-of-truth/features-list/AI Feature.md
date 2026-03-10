# 🎯 AI System – Domain-Aligned Design (Final Version)

---

# I. Vai trò của AI trong hệ thống

AI trong WalkMate không thay thế cơ chế ghép cặp ngẫu nhiên.

AI chỉ đóng vai trò:

- Tinh chỉnh thứ tự ưu tiên trong tập ứng viên hợp lệ
- Cá nhân hóa trải nghiệm người dùng
- Tăng xác suất COMPLETED session
- Gợi ý nội dung trò chuyện sau khi session được tạo

AI **không có quyền**:

- Tạo WalkSession
- Thay đổi trạng thái aggregate
- Áp dụng penalty
- Override domain invariant

AI chỉ cung cấp score và suggestion.

---

# II. AI Matching – Intent-Level Personalization

AI hoạt động trong Coordination Phase (WalkIntent).

---

## 1️⃣ Phạm vi hoạt động

AI chỉ chạy khi:

- WalkIntent.status = OPEN
- UserPresence.availability = AVAILABLE
- Không có BlockRelation
- Candidate đã thỏa điều kiện matching cơ bản:
  - Time window overlap
  - WalkPurpose trùng khớp
  - Matching constraints hợp lệ

AI chỉ ranking trong tập candidate hợp lệ.

---

# III. Nguồn dữ liệu AI sử dụng

AI không truy cập trực tiếp state machine.
AI đọc từ read model / projection.

---

## 1️⃣ Lịch sử tương tác

- WalkIntent đã ACCEPT
- MatchProposal đã CONFIRMED
- Session COMPLETED

---

## 2️⃣ Chất lượng trải nghiệm

- Rating 5 sao
- Following
- Tần suất completion

---

## 3️⃣ Hành vi thực tế (Lifecycle-based)

Từ WalkSession COMPLETED:

- Thời lượng đi bộ trung bình
- Tốc độ di chuyển trung bình (derived from trace)
- Tỷ lệ No-show
- Tỷ lệ Cancel

---

## 4️⃣ Pattern hoạt động

- Khung giờ thường active
- Thời điểm thường tạo WalkIntent
- Thời gian thường activate session

---

# IV. Mô hình AI – 3 bước chuẩn DDD-safe

---

## Bước 1 – User Embedding

Aggregate: UserEmbedding

Hệ thống xây dựng vector số hóa:

```text
Vector(User) =
[
  time_preference_pattern,
  average_walk_duration,
  average_speed,
  favorite_tags_distribution,
  reliability_score,
  acceptance_pattern,
  cancellation_pattern
]
```

Embedding được cập nhật khi:

- Session COMPLETED
- Rating được tạo
- Follow được thêm
- No-show xảy ra

Embedding không ảnh hưởng trực tiếp đến aggregate.

---

## Bước 2 – Compatibility Scoring

AI tính:

```text
Score(IntentA, IntentB) =
w1 * InterestSimilarity +
w2 * TimeOverlapStrength +
w3 * BehavioralSimilarity +
w4 * ReliabilityFactor
```

Trong đó:

- InterestSimilarity → tag similarity
- TimeOverlapStrength → mức overlap của time window
- BehavioralSimilarity → tốc độ + duration + pattern
- ReliabilityFactor → TrustScore

Trọng số được điều chỉnh dựa trên:

- Lịch sử accept/reject
- Rating 5 sao
- Following

AI chỉ trả về score.
MatchingService quyết định thứ tự gợi ý.

---

## Bước 3 – Context Awareness

AI phân tích ngữ cảnh hiện tại:

- Thời tiết
- Loại địa điểm (park, mall…)
- Thời gian trong ngày

Ví dụ:

- Nếu user thường chọn indoor khi trời mưa
  → Ưu tiên Intent gần indoor location

AI chỉ điều chỉnh ranking, không tạo proposal.

---

# V. Cold Start Strategy

Giai đoạn khởi tạo (chưa đủ dữ liệu):

AI fallback:

- Matching theo:
  - Time overlap
  - Purpose match
  - Distance proximity (geo-hash level, không radius scanning liên tục)

Sau 3–5 COMPLETED session:

- Bật UserEmbedding personalization

---

# VI. Reliability-Aware Matching

AI ưu tiên:

- Người có tỷ lệ COMPLETED cao
- Ít NO_SHOW
- Ít cancel phút chót

Nhưng AI không tự áp penalty.
Penalty vẫn do WalkSession aggregate + TrustScore xử lý.

---

# VII. AI Conversation Assistant (Chat Suggestion)

Phạm vi:

- Chỉ hoạt động khi WalkSession.status ∈ (PENDING, ACTIVE)
- Chỉ là UI suggestion
- Không lưu vào domain aggregate

---

## Nguồn dữ liệu

AI sử dụng:

1️⃣ Tag chung
2️⃣ Context (thời tiết, địa điểm)
3️⃣ Thành tích gần đây
4️⃣ WalkPurpose

---

## 3S Framework

AI quét:

- Shared Interests
- Situational Context
- Session Goal

---

## Ví dụ gợi ý

### Dựa trên sở thích

“Cả hai đều thích dắt thú cưng, bạn thường đi vòng nào trong công viên này?”

### Dựa trên ngữ cảnh

“Hôm nay thời tiết đẹp quá, mình đi hết một vòng hồ nhé?”

### Dựa trên mục tiêu

“Mục tiêu hôm nay của mình là 2km trong 20 phút, bạn cùng thử không?”

---

## UX Flow

Match confirmed
→ WalkSession created
→ ChatRoom OPEN
→ AI hiển thị 2–3 gợi ý
→ User tap để gửi

AI không tự động gửi tin nhắn.

---

# VIII. AI Boundary Protection

AI không:

- Gọi WalkSession.activate()
- Gọi WalkSession.complete()
- Tạo WalkSession
- Thay đổi TrustScore

AI chỉ:

- Read projection
- Compute score
- Emit suggestion

---

# IX. Event-Driven Integration

AI phản ứng với Domain Events:

- WalkSessionCompleted
- PartnerNoShowReported
- SessionCancelled
- WalkReviewCreated
- FollowRelationCreated

Projection service cập nhật embedding.

---

# X. Alignment Check

| Rule                     | Align |
| ------------------------ | ----- |
| Không thay state machine | ✅    |
| Không tạo session        | ✅    |
| Chỉ ranking              | ✅    |
| Dựa trên COMPLETED       | ✅    |
| Không phá invariant      | ✅    |
| Chat chỉ suggestion      | ✅    |
| Cold start defined       | ✅    |
| Reliability-aware        | ✅    |

---

# XI. Tóm tắt

AI trong WalkMate:

- Không thay thế matching
- Không phá domain boundary
- Không can thiệp lifecycle
- Không mutate aggregate
- Chỉ cá nhân hóa ranking và trải nghiệm

Coordination vẫn thuộc WalkIntent
Lifecycle vẫn thuộc WalkSession
AI chỉ là personalization layer.
