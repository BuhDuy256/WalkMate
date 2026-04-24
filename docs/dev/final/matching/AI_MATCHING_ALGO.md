# AI Matching Algo: Thiết Kế Kiến Trúc và Toán Học

Việc thiết kế kiến trúc và hiểu rõ bản chất thuật toán trước khi code là hướng đi đúng của một Tech Lead. Ở giai đoạn hiện tại, khi hệ thống mới có dữ liệu nền từ onboarding và chưa có lịch sử hành vi lớn, AI Matching phù hợp nhất là:

**Dynamic Weighted Content-Based Filtering**

Tức là hệ gợi ý dựa trên nội dung, có trọng số động, và tự điều chỉnh theo hành vi người dùng.

---

## 1. Công Thức Cốt Lõi (Scoring Formula)

Thay vì chỉ dùng thời gian như trước, điểm cuối của một ứng viên là tổng hợp của 3 yếu tố, mỗi yếu tố nhân với một trọng số cá nhân hóa lấy từ bảng `matching_preference_model`.

$$
TotalScore = (W_{time} \times S_{time}) + (W_{interest} \times S_{tags}) + (W_{behavior} \times S_{trust})
$$

Trong đó:

- $W$ (Weights): trọng số ưu tiên của user (tổng bằng 1.0 hoặc 100%).
- $S$ (Scores): điểm thành phần của ứng viên theo từng tiêu chí (chuẩn hóa về thang 0-100).

---

## 2. Phân Tách Các Điểm Thành Phần (S Variables)

Khi hiện thực trong Java, thuật toán cần tính 3 biến điểm cho mỗi cặp người tìm - ứng viên.

### 2.1 Điểm Thời Gian ($S_{time}$)

Dựa trên số phút trùng thời gian rảnh (overlap minutes).

- Nếu trùng từ 60 phút trở lên: đạt tối đa 100 điểm.
- Nếu dưới 60 phút: tính theo tỷ lệ.

$$
S_{time} = \min\left(\frac{OverlapMinutes}{60} \times 100, 100\right)
$$

### 2.2 Điểm Sở Thích/Tags ($S_{tags}$)

Dùng **Jaccard Similarity** để so sánh hai tập tag.

$$
S_{tags} = \frac{|Tags_{A} \cap Tags_{B}|}{|Tags_{A} \cup Tags_{B}|} \times 100
$$

Ví dụ:

- User A: `[Đi dạo, Chó mèo]`
- User B: `[Chó mèo, Nói chuyện]`
- Giao nhau: `1` tag
- Hợp: `3` tag

Kết quả: $S_{tags} = (1/3) \times 100 = 33.3$

### 2.3 Điểm Uy Tín ($S_{trust}$)

Quy đổi Trust Score từ thang `0-1000` sang thang `0-100`.

$$
S_{trust} = \frac{CandidateTrustScore}{10}
$$

---

## 3. Yếu Tố "AI" Nằm Ở Đâu? (Feedback Loop)

Nếu chỉ có công thức trên thì đây mới là thuật toán tính điểm tĩnh. Phần học máy nằm ở **vòng lặp phản hồi** để cập nhật trọng số theo hành vi thực tế.

### 3.1 Khởi Động Lạnh (Cold Start)

Khi user mới tạo tài khoản, dùng trọng số mặc định:

- $W_{time} = 0.4$
- $W_{interest} = 0.3$
- $W_{behavior} = 0.3$

### 3.2 Vòng Lặp Phản Hồi Ngầm (Implicit Feedback Loop)

Ví dụ mỗi lần hệ thống gợi ý 5 ứng viên, AI theo dõi user bấm "Gửi lời mời" cho ai.

Nếu user thường xuyên chọn người có $S_{tags}$ cao (dù $S_{time}$ không cao), một cronjob chạy đêm sẽ cập nhật trọng số trong `matching_preference_model`:

- tăng $W_{interest}$ lên `0.5`
- giảm $W_{time}$ xuống `0.2`

Kết quả: các lần matching sau, hệ thống tự ưu tiên ứng viên hợp sở thích hơn.

---

## 4. Mô Phỏng Trực Quan Thuật Toán (Interactive Simulation)

Để dễ hình dung ảnh hưởng của trọng số đến thứ hạng ứng viên, có thể dựng bộ mô phỏng slider cho $W_{time}$, $W_{interest}$, $W_{behavior}$.

Giả sử có 3 ứng viên mẫu:

- **Ứng viên A**: thời gian trùng cao, nhưng khác sở thích.
- **Ứng viên B**: sở thích trùng rất cao, nhưng thời gian trùng thấp.
- **Ứng viên C**: trust score rất cao (elite), các chỉ số còn lại trung bình.

Khi kéo trọng số, thứ hạng thay đổi tương ứng. Đây là cách kiểm chứng trực quan trước khi triển khai production.

---

## 5. Tóm Tắt Triển Khai

- Ngắn hạn: triển khai đúng công thức 3 thành phần với trọng số động.
- Trung hạn: hoàn thiện feedback loop cập nhật trọng số tự động.
- Dài hạn: khi có đủ dữ liệu hành vi lớn, mới cân nhắc nâng cấp sang mô hình học sâu.