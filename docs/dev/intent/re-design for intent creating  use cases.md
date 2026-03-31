### 1\. Use Case specification "Create Intent"

| Use Case | Create Intent (Tạo yêu cầu tìm bạn đi bộ) |
| :--- | :--- |
| **Brief Descriptions** | Chức năng này bắt đầu khi người dùng muốn thiết lập thời gian, địa điểm và sở thích (tags) để hệ thống bắt đầu tìm kiếm người đồng hành phù hợp. |
| **Main Flow** | 1. Hệ thống hiển thị bản đồ ở tab Explore cùng Welcome Bottom Sheet chứa thanh tìm kiếm và các Hotspots gợi ý.<br>2. Người dùng chọn một Hotspot (bằng cách tương tác map, search, hoặc bấm gợi ý).<br>3. Hệ thống hiển thị Bottom Sheet "Create Intent" để người dùng thiết lập bộ lọc (thời gian, khoảng tuổi, giới tính, tags).<br>4. Người dùng hoàn tất thiết lập và ra lệnh "Set Walking Intent" (Tìm kiếm).<br>5. Hệ thống thu nhỏ Bottom Sheet, hiển thị giao diện quét Radar trên bản đồ, đưa Intent vào trạng thái tìm kiếm ngầm và thêm vào danh sách tab Matches/Finding. |
| **Alternative Flows** | **3a. Người dùng hủy tạo Intent**<br>  1. Tại bước 3, người dùng nhấn nút "Back" trên màn hình.<br>  2. Hệ thống đóng Bottom Sheet "Create Intent" và quay lại trạng thái Welcome mặc định.<br>**5a. Không tìm thấy người phù hợp lập tức**<br>  1. Radar quét 10 giây nhưng không có Match.<br>  2. Hệ thống thông báo đưa Intent vào Waitlist (chạy ngầm). Người dùng có thể tiếp tục sử dụng ứng dụng bình thường. |
| **Pre-conditions** | Người dùng đã đăng nhập thành công. Ứng dụng đang ở tab Explore. |
| **Special Requirements** | - Giao diện chọn điểm và cấu hình Intent (Bottom Sheet) phải mượt mà, không làm giật lag bản đồ nền.<br>- Các lựa chọn bộ lọc (Tags, Time) phải dùng dạng Chip/Slider, không bắt người dùng gõ chữ tự do. |

-----

### 2\. Use Case specification "Interact with Finding tab"

| Use Case | Interact with Finding tab (Quản lý yêu cầu đang tìm kiếm) |
| :--- | :--- |
| **Brief Descriptions** | Chức năng này cho phép người dùng xem, hủy yêu cầu tìm kiếm đang chạy ngầm, hoặc tiếp nhận một đề xuất ghép đôi (Proposal) từ hệ thống. |
| **Main Flow** | 1. Người dùng truy cập vào tab "Matches", chọn sub-tab "Finding".<br>2. Hệ thống liệt kê các Intent đang ở trạng thái tìm kiếm (Waitlist).<br>3. Hệ thống tìm thấy một đối tượng phù hợp (Match) cho một Intent.<br>4. Hệ thống tự động gỡ Intent đó khỏi tab "Finding", chuyển thành một Proposal bên sub-tab "Proposal".<br>5. Hệ thống gửi Push Notification để thông báo cho người dùng.<br>6. Người dùng truy cập tab Proposal để xem thông tin đối phương. |
| **Alternative Flows** | **2a. Người dùng chủ động hủy tìm kiếm**<br>  1. Tại sub-tab Finding, người dùng nhấn "Remove intent" (Hủy) trên một thẻ Intent.<br>  2. Hệ thống xóa Intent khỏi hệ thống và cập nhật lại danh sách hiển thị.<br>**6a. Người dùng từ chối (Pass) Proposal**<br>  1. Khi xem Proposal, người dùng nhấn "Pass".<br>  2. Hệ thống ẩn Proposal này đi, tự động khôi phục Intent đưa lại vào sub-tab "Finding" để tiếp tục tìm người khác mà không bắt người dùng tạo lại Intent từ đầu. |
| **Pre-conditions** | Người dùng có ít nhất một Intent đang hoạt động (trạng thái OPEN) trong hệ thống. |
| **Special Requirements** | - Việc chuyển đổi thẻ từ tab Finding sang Proposal phải được cập nhật theo thời gian thực (Real-time/Websocket) mà không cần người dùng tải lại trang. |

-----

### 3\. Use Case specification "Accept Proposal to Trigger Session"

| Use Case | Accept Proposal to Trigger Session (Chốt kèo và Bắt đầu phiên đi bộ) |
| :--- | :--- |
| **Brief Descriptions** | Chức năng này bắt đầu khi người dùng xem một Proposal và quyết định chấp nhận (Accept). Hệ thống sẽ kiểm tra phía đối phương để tạo một phiên đi bộ chung (Session). |
| **Main Flow** | 1. Hệ thống hiển thị thông tin Proposal (Avatar, điểm chung, khoảng thời gian khớp) tại tab Matches/Proposal.<br>2. Người dùng nhấn nút "Accept".<br>3. Hệ thống ghi nhận hành động xác nhận của người dùng.<br>4. Hệ thống kiểm tra và xác định đối phương cũng đã nhấn "Accept".<br>5. Hệ thống thực hiện chuyển đổi trạng thái Proposal thành CONFIRMED.<br>6. Hệ thống tạo một Walk Session và chuyển thẻ hiển thị sang sub-tab "Session", mở khóa tính năng nhắn tin (Chat) và định vị điểm hẹn. |
| **Alternative Flows** | **4a. Đối phương chưa phản hồi**<br>  1. Hệ thống đổi giao diện Proposal của người dùng thành trạng thái "Đang chờ đối phương xác nhận...".<br>  2. Người dùng tiếp tục chờ (hoặc có quyền rút lại lời mời nếu đợi quá lâu).<br>**4b. Đối phương từ chối hoặc hủy Intent đột ngột**<br>  1. Hệ thống thông báo đối phương không thể tham gia.<br>  2. Hệ thống hủy Proposal hiện tại và tự động đẩy Intent của người dùng quay lại tab "Finding" để tiếp tục tìm. |
| **Pre-conditions** | Có một Proposal hợp lệ (trạng thái PENDING) đang tồn tại giữa hai người dùng. |
| **Special Requirements** | - Bước 4 và 5 phải được thực thi bằng giao dịch nguyên tử (Atomic Transaction) ở Backend để đảm bảo không bao giờ xảy ra tình trạng 1 người vào Session còn 1 người bị kẹt ở Proposal. |

### 4\. Use Case specification "Cancel Session"

| Use Case | Cancel Session (Hủy phiên đi bộ đã chốt) |
| :--- | :--- |
| **Brief Descriptions** | Chức năng này cho phép người dùng hủy bỏ một phiên đi bộ (Walk Session) đã được xác nhận thành công (trước khi gặp mặt hoặc trong khi đang đi), giải phóng trạng thái cho cả hai bên. |
| **Main Flow** | 1. Người dùng truy cập vào tab "Matches", chọn sub-tab "Session".<br>2. Hệ thống hiển thị chi tiết phiên đi bộ hiện tại (bản đồ điểm hẹn, khung chat, thông tin đối phương).<br>3. Người dùng nhấn chọn nút "Hủy phiên" (Cancel Session).<br>4. Hệ thống hiển thị Popup yêu cầu xác nhận và chọn lý do hủy (ví dụ: Có việc bận đột xuất, Đợi quá lâu...).<br>5. Người dùng xác nhận Hủy.<br>6. Hệ thống cập nhật trạng thái WalkSession thành CANCELLED.<br>7. Hệ thống đóng phòng Chat, chuyển thẻ Session vào lịch sử (History).<br>8. Hệ thống gửi Push Notification/Popup báo ngay cho đối phương biết kèo đã bị hủy. |
| **Alternative Flows** | **4a. Người dùng đổi ý không hủy nữa**<br>  1. Tại bước 4, người dùng nhấn "Đóng" hoặc chạm ra ngoài vùng Popup.<br>  2. Hệ thống giữ nguyên trạng thái phiên đi bộ đang diễn ra.<br>**8a. Đối phương là người chủ động hủy trước**<br>  1. Hệ thống ngắt kết nối Chat ngay lập tức.<br>  2. Hệ thống văng thông báo cho người dùng: *"Đối phương đã hủy lịch hẹn. Bạn có muốn tìm một WalkMate khác ngay bây giờ không?"*<br>  3. Nếu người dùng chọn "Có", hệ thống lập tức mở lại Welcome Bottom Sheet ở tab Explore để tạo Intent mới. |
| **Pre-conditions** | Hai người dùng đang có một Walk Session chung (trạng thái PENDING - chờ gặp, hoặc ACTIVE - đang đi) tại tab Session. |
| **Special Requirements** | - **Hệ thống đánh giá (Penalty):** Nếu người dùng hủy kèo quá sát giờ hẹn (ví dụ \< 15 phút) hoặc hủy nhiều lần liên tục, hệ thống cần ghi nhận để trừ "Chỉ số tin cậy" (Trust Score), tránh tình trạng spam/phá rối.<br>- **Bảo mật:** Phòng Chat (Chat room) phải bị khóa chức năng gửi tin nhắn NGAY LẬP TỨC sau khi 1 trong 2 người nhấn Hủy để tránh việc nhắn tin trách móc/quấy rối nhau. |
