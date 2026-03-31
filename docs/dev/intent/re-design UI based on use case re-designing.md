### 1. Thành phần Toàn cục (Global UI)
* **Bottom Tab Bar:** Luôn cố định ở dưới cùng màn hình với 3 Tabs chính: **Explore**, **Matches**, và **Profile**.
* **Top Bar (Mặc định):** Hiển thị Logo WalkMate, biểu tượng Chuông thông báo (Notification Bell), và Nút Avatar người dùng.

---

### 2. Tab 1: EXPLORE (Không gian Khởi tạo & Tìm kiếm)
Đây là màn hình "Trang chủ" đích thực, tập trung hoàn toàn vào Bản đồ và việc thiết lập yêu cầu đi bộ (Create Intent).

* **Giao diện cốt lõi:** Bản đồ Google Map toàn màn hình hiển thị vị trí hiện tại và các điểm Hotspot xung quanh.
* **Trạng thái 1 - Welcome (Chưa chọn điểm):**
    * Một "Welcome Bottom Sheet" nằm ở nửa dưới màn hình: *"Ready to walk, [Tên]?"*.
    * Chứa thanh tìm kiếm (Search bar) và danh sách gợi ý nhanh 5 Hotspots (dạng Chips).
* **Trạng thái 2 - Setup (Đã chọn điểm):**
    * Bản đồ zoom vào Hotspot được chọn.
    * Bottom Sheet chuyển sang form "Create Intent": Kéo thanh trượt chọn thời gian, khoảng tuổi, chọn tag sở thích.
    * Nút Call-to-Action: **"Set Walking Intent"** (Tìm kiếm).
* **Trạng thái 3 - Scanning (Đang quét ngầm):**
    * Bottom sheet thu nhỏ lại. Bản đồ hiển thị hiệu ứng Radar quét tại điểm Hotspot.
    * Xuất hiện một thẻ nhỏ (Floating Card) lơ lửng trên đỉnh bản đồ báo hiệu hệ thống đang quét. Dữ liệu Intent lúc này chính thức được đẩy sang Waitlist bên tab **Matches**.

---

### 3. Tab 2: MATCHES (Không gian Quản lý & Tương tác)
Khi người dùng bấm sang tab này từ Bottom Tab Bar, bản đồ biến mất, nhường chỗ cho giao diện dạng Danh sách (List/Cards). Trên cùng màn hình sẽ xuất hiện một **Top Bar phụ (Sub-navigation)** gồm 3 sub-tabs:

* **Sub-tab 2.1: FINDING (Danh sách chờ - Waitlist)**
    * **Nội dung:** Hiển thị danh sách các Intent mà người dùng vừa tạo bên tab Explore đang chạy ngầm nhưng chưa có ai khớp.
    * **Tương tác:** Người dùng xem lại được thông số mình đã cài đặt và có duy nhất một hành động: **Hủy Intent (Remove/Cancel)**. Nếu có người khớp, thẻ Intent ở đây tự động biến mất và nảy sang tab Proposal.
* **Sub-tab 2.2: PROPOSAL (Đề xuất Ghép đôi)**
    * **Nội dung:** Hiển thị thẻ thông tin (Match Card) của đối phương do hệ thống đề xuất. Bao gồm: Avatar, Tên, Tuổi, Điểm tin cậy, Thời gian khớp, và các Tag chung.
    * **Tương tác:** * Nút **Pass (Bỏ qua):** Thẻ Proposal biến mất, Intent tự động được đẩy ngược về tab *Finding* để tiếp tục tìm người khác.
        * Nút **Accept (Chấp nhận):** Xác nhận chốt kèo. Chờ đối phương cũng Accept thì thẻ này sẽ chuyển sang tab Session.
* **Sub-tab 2.3: SESSION (Phiên đi bộ hiện hành)**
    * **Nội dung:** Chứa các cuộc hẹn đã "Khớp lệnh" thành công. Hiển thị một bản đồ nhỏ chỉ đường đến điểm hẹn, thông tin người đồng hành.
    * **Tương tác:** Mở khung Chat an toàn và nút **Hủy phiên (Cancel Session)** nếu có việc bận đột xuất.

---

### 4. Tab 3: PROFILE (Không gian Cá nhân)
* **Trạng thái:** *[Blocked] - Tạm thời đóng băng, chưa định nghĩa ở phase này.*
