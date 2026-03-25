# Nghiên cứu về Walk Intent Matching Frontend + Backend

## Abstract View về những gì feature này có

---

### 1. Matching Input (Nguyên liệu đầu vào)

Khi người dùng tạo một **WalkIntent**, hệ thống sẽ thu thập 4 nhóm thông tin chính. Hãy coi đây là chiếc thẻ "đăng ký tìm bạn đồng hành":

- **Không gian (Where):** Điểm đến cụ thể (Hotspot ID). _Ví dụ: Công viên Tao Đàn._
- **Thời gian (When):** Một khung thời gian linh hoạt (Time Window) từ mốc A đến mốc B. _Ví dụ: "Tôi rảnh trong khoảng 17:00 đến 19:00, và tôi muốn đi bộ 30 phút"._
- **Bộ lọc cứng (Who - Hard Constraints):** Giới hạn bắt buộc về đối tác. _Ví dụ: Chỉ nữ, từ 20-25 tuổi._
- **Nhãn sở thích (What - Soft Constraints):** Các thẻ (Tags) mô tả mục đích chuyến đi. _Ví dụ: [Chạy bộ], [Nghe podcast], [Dắt chó]._

---

### 2. Matching Logic cho MVP (Giai đoạn quy tắc tĩnh - Rule-Based)

Ở bản MVP, hệ thống hoạt động như một cái phễu lọc cơ học gồm 2 bước: **Lọc loại trừ** và **Chấm điểm xếp hạng**.

**Bước 1: Phễu lọc cứng (Filtering)**
Hệ thống sẽ lấy Intent của User A đi quét trong database và **loại bỏ ngay lập tức** những Intent không thỏa mãn các điều kiện sinh tử:

- **Phải** cùng một Hotspot.
- **Phải** có khoảng thời gian giao thoa hợp lệ. (Khoảng rảnh của A và khoảng rảnh của B phải đan vào nhau đủ lâu để tạo thành một session 30 phút).
- **Phải** khớp tuyệt đối các điều kiện về Giới tính và Độ tuổi (nếu có thiết lập).
- **Tuyệt đối không** nằm trong danh sách Block của nhau.

**Bước 2: Hệ thống chấm điểm (Scoring Engine)**
Với những Intent đã lọt qua Bước 1 (những ứng viên hợp lệ), hệ thống bắt đầu cộng điểm để xem ai là người phù hợp nhất:

- **Sở thích:** Trùng 1 tag (+10 điểm), trùng 2 tag (+20 điểm).
- **Mạng xã hội:** Là người đang Follow nhau (+50 điểm - ưu tiên cực cao).
- **Độ tin cậy:** TrustScore cao (+ điểm), từng có lịch sử No-show (- điểm).

**Kết quả MVP:** Hệ thống trả ra một danh sách được sắp xếp từ điểm cao xuống thấp và gửi **MatchProposal** cho người đứng đầu. Nếu bị từ chối, gửi tiếp cho người thứ hai.

---

### 3. Nâng cấp lên AI Matching (Giai đoạn Học máy - Machine Learning)

Khi dữ liệu (lịch sử WalkSession, Rating, TrustScore) đã đủ lớn, cỗ máy MVP sẽ bộc lộ nhược điểm là các "điểm số" bị fix cứng bởi con người. Lúc này, AI sẽ vào cuộc để thay đổi cách tư duy.

**A. Chuyển từ "Cộng điểm tĩnh" sang "Trọng số động" (Dynamic Weighting)**
Ở MVP, bạn quy định trùng tag [Chạy bộ] được cộng 10 điểm. Nhưng AI sẽ tự phân tích hàng ngàn chuyến đi thực tế và phát hiện ra: _"Những người cùng độ tuổi thường đi dạo thành công hơn là những người trùng tag sở thích"_. AI sẽ tự động điều chỉnh trọng số ưu tiên Độ tuổi cao hơn Sở thích mà bạn không cần sửa code.

**B. Gợi ý theo hành vi (Collaborative Filtering)**
Giống như thuật toán của Netflix hay Spotify. AI sẽ nhận diện các cụm người dùng: _"User A có hành vi đi bộ giống User B. User B đã đi bộ rất vui vẻ với User C. Vậy hệ thống sẽ ưu tiên ghép User A với User C"_, dù có thể profile của họ nhìn lướt qua không quá khớp nhau.

**C. Dự đoán rủi ro (Risk Prediction)**
Đây là tính năng cực kỳ giá trị. Thay vì đợi xảy ra No-show mới trừ điểm, AI sẽ đánh giá một Intent ngay khi nó vừa được tạo: _"User này đặt lịch vào chiều thứ 6, trời sắp mưa, lịch sử người này hay hủy kèo vào cuối tuần -> Rủi ro No-show là 80%"_. Hệ thống sẽ hạ độ ưu tiên của user này xuống, hoặc ghép họ với những người có độ bao dung cao (hay rate 5 sao).

**D. Phân tích ngữ nghĩa (Semantic Matching)**
Ở MVP, tag [Chạy bộ] và [Jogging] là hai tag khác nhau. AI sẽ tự hiểu ngữ cảnh và gom cụm chúng lại, giúp việc matching linh hoạt và thông minh hơn, không bị trói buộc bởi những từ khóa cứng nhắc.

---

## Thuật toán tính khoảng time giao thoa

Tuyệt vời. Bài toán **"Time Overlap" (Giao thoa thời gian)** tưởng chừng đơn giản nhưng lại là trái tim của hệ thống Matching và rất dễ trở thành nút thắt hiệu năng (bottleneck) nếu xử lý không khéo khi dữ liệu lớn lên.

Dưới đây là thiết kế trừu tượng (Abstract Logic) cho thuật toán này, đi từ tư duy toán học đến cách áp dụng vào Database mà không cần đụng đến code cụ thể.

---

### 1. Tư duy Toán học cơ bản (The Core Formula)

Giả sử chúng ta có 2 Intent rảnh rỗi:

- **User A:** rảnh từ $Start_A$ đến $End_A$
- **User B:** rảnh từ $Start_B$ đến $End_B$

Để tìm ra khoảng thời gian chung mà cả hai có thể đi bộ cùng nhau, hệ thống sẽ đi tìm 2 mốc thời gian mới:

1.  **Điểm bắt đầu chung (Overlap Start):** Là mốc thời gian **muộn nhất** trong 2 mốc bắt đầu.
    - Công thức: $Overlap\_Start = \max(Start_A, Start_B)$
2.  **Điểm kết thúc chung (Overlap End):** Là mốc thời gian **sớm nhất** trong 2 mốc kết thúc.
    - Công thức: $Overlap\_End = \min(End_A, End_B)$

**Điều kiện tồn tại:**
Hai người này CÓ giao thoa thời gian nếu và chỉ nếu khoảng thời gian chung mang giá trị dương:
$$Overlap\_End > Overlap\_Start$$

---

### 2. Áp dụng Business Rule (Domain Invariants)

Chỉ có giao thoa thôi là chưa đủ để tạo thành một `WalkSession`. Bạn cần áp dụng các quy tắc nghiệp vụ (Business Rules) của WalkMate:

**Quy tắc: Thời gian đi bộ tối thiểu (Minimum Walk Duration)**
Hệ thống không thể ghép 2 người chỉ có 3 phút rảnh rỗi chung. Chúng ta cần một hằng số $Min\_Duration$ (ví dụ: 15 phút).

- **Logic chốt hạ:**
  $$Overlap\_End - Overlap\_Start \ge Min\_Duration$$

Nếu điều kiện này True $\rightarrow$ Gửi `MatchProposal`.
Lúc này, thời gian dự kiến của `WalkSession` (nếu họ đồng ý) chính là từ $Overlap\_Start$ đến $Overlap\_End$.

---

### 3. Các kịch bản thực tế (Trực quan hóa Logic)

Dựa vào công thức trên, hệ thống sẽ tự động cover được tất cả các trường hợp (Edge Cases) ngoài đời thực:

- **Kịch bản 1: Bao hàm (Subset).**
  - A rảnh cả buổi chiều (13h - 18h). B chỉ rảnh lúc (15h - 16h).
  - $\max(13, 15) = 15$ | $\min(18, 16) = 16$.
  - _Kết quả:_ Đi chung từ 15h đến 16h.
- **Kịch bản 2: Giao nhau một phần (Partial Overlap).**
  - A rảnh (16h - 18h). B rảnh (17h - 19h).
  - $\max(16, 17) = 17$ | $\min(18, 19) = 18$.
  - _Kết quả:_ Đi chung từ 17h đến 18h.
- **Kịch bản 3: Sượt qua nhau (Touching boundaries).**
  - A rảnh (15h - 16h). B rảnh (16h - 17h).
  - $\max(15, 16) = 16$ | $\min(16, 17) = 16$.
  - _Kết quả:_ $16 - 16 = 0$ (Không đủ Minimum Duration $\rightarrow$ Bỏ qua).

---

### 4. Bí kíp tối ưu ở tầng Cơ sở dữ liệu (Database Query Logic)

**Sai lầm phổ biến:** Rất nhiều hệ thống kéo toàn bộ `WalkIntent` từ Database về bộ nhớ RAM (Backend), sau đó chạy vòng lặp `for` để tính $\max$, $\min$ rồi mới loại trừ. Khi có 10.000 người, server sẽ sập.

**Tối ưu chuẩn Abstract:**
Bạn phải ép Database làm việc này trước khi trả dữ liệu về. Công thức vàng để kiểm tra 2 khoảng thời gian CÓ giao thoa thẳng trong Database là:

> _(Start_A < End_B) **AND** (Start_B < End_A)_

**Diễn giải logic cho Worker Service (Hệ thống quét tự động):**
Khi User A tạo Intent mới, Worker sẽ đi tìm các User B tiềm năng bằng một bộ lọc trừu tượng như sau:

1.  Lọc `Hotspot_ID` giống nhau.
2.  Lọc trạng thái `OPEN`.
3.  **Lọc Thời gian:**
    - `Start_B` phải nhỏ hơn `(End_A - Min_Duration)`
    - `End_B` phải lớn hơn `(Start_A + Min_Duration)`

_Ghi chú: Phép tính trừ đi `Min_Duration` ngay trong câu query đảm bảo rằng phần giao thoa chắc chắn lớn hơn thời gian đi bộ tối thiểu. Dữ liệu kéo về RAM lúc này chỉ còn vài chục người thực sự phù hợp, Backend chỉ việc tính điểm (Scoring)._
