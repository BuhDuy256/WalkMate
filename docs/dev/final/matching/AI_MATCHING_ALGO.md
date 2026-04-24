# AI Matching: Vòng Đời Học Hỏi và Tự Điều Chỉnh

Để dễ hiểu, hãy nhớ nguyên tắc cốt lõi:

- **Trust Score (Điểm Uy tín)**: thước đo **khách quan** đánh giá một người "Tốt hay Xấu".
- **AI Weights (Trọng số)**: thước đo **chủ quan** xem người đó có "Hợp với gu của bạn" hay không.

Dưới đây là **4 giai đoạn** trong vòng đời AI học hỏi và tự điều chỉnh.

## 1. Giai đoạn 1: Khởi tạo (The Cold Start)

Khi một người dùng mới hoàn thành onboarding (chọn giới tính và tags), hệ thống tạo bản ghi trong bảng `matching_preference_model`.

Vì AI chưa biết gu thực sự của họ, trọng số ban đầu được chia đều:

- `weight_time_overlap` (Trọng số Thời gian): **33%**
- `weight_interest` (Trọng số Sở thích): **33%**
- `weight_behavior` (Trọng số Uy tín/Hành vi): **33%**

## 2. Giai đoạn 2: Ghép đôi ngầm (The Silent Matchmaker)

Hệ thống Auto-Match chạy ngầm ở backend, quét các cặp `WalkIntent` đang `OPEN` và tính tổng điểm:

$$
TotalScore = (W_{time} \times Điểm_{ThờiGian}) + (W_{interest} \times Điểm_{SởThích}) + (W_{behavior} \times Điểm_{TrustScore})
$$

Lưu ý:

- `Điểm_Sở_Thích` tính bằng **Jaccard Similarity** (tỷ lệ % tag trùng nhau giữa 2 người).
- `Điểm_Trust_Score` lấy từ hệ thống trust score thang **0-1000**.

Nếu `TotalScore` vượt ngưỡng (ví dụ **70/100**), AI sẽ tạo `WalkSession` cho 2 người.

## 3. Giai đoạn 3: Thu thập tín hiệu (Structured Feedback)

Sau buổi đi bộ, màn hình review xuất hiện. Đây là lúc AI nhận dữ liệu phản hồi.

Ví dụ:

- User A đánh giá User B **5 sao** kèm tag **"Enjoyable chat"**.

Khi đó:

- Backend lưu tag vào bảng `walk_review_tag_map`.
- User B được cộng ngay **+10 trust score** (phần này đã hoàn thành).

## 4. Giai đoạn 4: AI học hỏi (The Weight Adjustment)

Mỗi đêm, CronJob của AI quét dữ liệu review/tag và cập nhật `matching_preference_model` của User A.

### 4.1 Tín hiệu tích cực

- User A rate 5 sao và khen "Enjoyable chat".

Suy luận:

> User A rất coi trọng người có sở thích trò chuyện tương đồng.

Hành động:

- Tăng `weight_interest` từ **33%** lên **45%**.

### 4.2 Tín hiệu tiêu cực

- Vào lần khác, User A rate 2 sao và chọn tag "Arrived late".

Suy luận:

> User A không chấp nhận sự trễ giờ; bộ lọc uy tín cần được ưu tiên cao hơn.

Hành động:

- Tăng `weight_behavior` lên **50%**.
- Giảm các trọng số còn lại.

## Kết quả

Ở lần Auto-Match tiếp theo (quay lại Giai đoạn 2), công thức điểm của User A đã thay đổi.

Hệ thống sẽ:

- Loại bỏ ứng viên có trust score thấp (vì `W_behavior` cao).
- Ưu tiên ứng viên có tags sở thích tương đồng (vì `W_interest` cao).