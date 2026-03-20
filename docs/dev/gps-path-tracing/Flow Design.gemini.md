Lựa chọn chiến lược **Batching (Gom nhóm)** là một quyết định rất chuẩn xác cho các ứng dụng cần tracking theo thời gian thực (như Strava, Grab), vì nó cân bằng được giữa trải nghiệm người dùng (thấy data liên tục) và tối ưu tài nguyên (pin, băng thông). Việc backend của bạn có cơ chế encode để tiết kiệm storage lại càng làm cho luồng này tối ưu hơn.

Việc bạn cảm thấy lúng túng khi không rõ nên mô tả flow như thế nào là rất bình thường. Trong Android, khi đụng đến các tác vụ chạy ngầm và phần cứng, cách tư duy tốt nhất là mô tả theo **Dòng chảy dữ liệu (Data Flow)**: *Từ lúc Sensor phát hiện thay đổi -> Lưu trữ -> Xử lý định kỳ -> Đẩy lên UI và Server.*

Dưới đây là mô tả chi tiết quy trình hoạt động (Workflow) cho chiến lược Batching của bạn, được chia theo từng giai đoạn và định rõ công cụ cần dùng.

## Kiến Trúc Luồng Chạy (Batching GPS Workflow)

### Bước 1: Khởi tạo và Giữ "mạng sống" cho App (Lifecycle & Foreground)
Để hệ điều hành Android không tắt app của bạn khi người dùng tắt màn hình, bạn phải khai báo cho hệ thống biết app đang làm một việc quan trọng.

* **Mục đích:** Đảm bảo quá trình tracking không bị gián đoạn.
* **Dùng gì?:** Một class kế thừa từ `Service` (ví dụ: `WalkTrackerService`). Cần chuẩn bị thêm `NotificationCompat.Builder` để tạo thông báo hiển thị trên thanh trạng thái.
* **Gọi gì từ Android Engine?:**
    * Gọi `ContextCompat.startForegroundService()` từ UI để kích hoạt service.
    * Bên trong Service, gọi `startForeground(ID, notification)` để báo cho Android OS biết đây là tác vụ ưu tiên cao, yêu cầu OS không cấp phát bộ nhớ của app này cho app khác.

### Bước 2: Lấy tọa độ liên tục và Lọc nhiễu (Data Acquisition & Filtering)
Đây là lúc thiết bị giao tiếp với vệ tinh hoặc mạng di động để lấy vị trí.

* **Mục đích:** Nhận tọa độ MỚI NHẤT, loại bỏ các tọa độ sai lệch.
* **Dùng gì?:** `FusedLocationProviderClient` (thuộc Google Play Services). Khởi tạo `LocationRequest` với `setInterval(2000)` (lấy mỗi 2 giây chẳng hạn) và `setPriority(Priority.PRIORITY_HIGH_ACCURACY)`.
* **Gọi gì từ Android Engine?:**
    * Giao tiếp với Hardware GPS thông qua `LocationManager` ngầm của thiết bị.
    * Sử dụng `LocationCallback` để Android Engine "đẩy" tọa độ mới (`Location` object) về cho bạn mỗi khi có thay đổi.
* **Logic xử lý (Domain/Service):** Khi nhận `Location` từ callback, kiểm tra `location.getAccuracy()`. Nếu độ sai lệch > 20 mét (tùy bạn cấu hình), bỏ qua điểm đó.

### Bước 3: Lưu trữ Offline-First (Local DB)
Tuyệt đối không đẩy thẳng dữ liệu lấy được lên Server ngay. Phải cất vào kho cục bộ trước để đề phòng rớt mạng.

* **Mục đích:** Lưu trữ an toàn, làm nguồn sự thật duy nhất (Single Source of Truth) cho cả UI và Network.
* **Dùng gì?:** **Room Database**.
    * Tạo `RoutePointEntity` có một field trạng thái: `isSynced = false`.
    * Tạo `RoutePointDao` với hàm `insertPoint()`.
* **Gọi gì từ Android Engine?:** Giao tiếp với `SQLite` engine của thiết bị để ghi dữ liệu xuống ổ cứng vật lý.

### Bước 4: Kiểm tra điều kiện Batching & Đồng bộ (Sync Logic)
Đây là trái tim của chiến lược bạn đã chọn. Logic này nằm trong `WalkTrackerService` (hoặc một Domain Service được gọi bởi Tracker Service).

* **Mục đích:** Cứ đủ 50 điểm hoặc đủ 1 phút thì gom lại thành 1 cục (Batch) để gửi.
* **Dùng gì?:**
    * **Logic gom nhóm:** Dùng biến đếm (counter) hoặc `Timer`/`Coroutine delay` để check định kỳ.
    * **Lấy data:** Gọi Room `Dao.getUnsyncedPoints()` (lấy danh sách các điểm có `isSynced = false`).
    * **Gửi data:** Dùng thư viện **Retrofit** map list data thành `AppendSessionPointsRequest` và POST lên Server.
* **Gọi gì từ Android Engine?:** `ConnectivityManager` để check xem máy có đang kết nối Internet hay không trước khi gọi Retrofit.
* **Xử lý kết quả:**
    * Thành công (HTTP 200): Gọi Room update lại lô điểm vừa rồi thành `isSynced = true` (hoặc xóa đi tùy chiến lược lưu trữ của bạn).
    * Thất bại/Mất mạng: Giữ nguyên `isSynced = false`. Lần gom nhóm tiếp theo sẽ lấy lại và gửi gộp.

### Bước 5: Cập nhật Giao diện (UI Update)
Trong lúc Bước 2, 3, 4 đang âm thầm chạy ở Service/Background, UI của bạn vẫn phải vẽ đường đi liên tục cho user xem.

* **Mục đích:** Hiển thị tọa độ mới nhất lên bản đồ.
* **Dùng gì?:**
    * View/Fragment quan sát (Observe) dữ liệu từ `ViewModel`.
    * `ViewModel` đọc data trực tiếp từ Room DB bằng `Flow<List<RoutePointEntity>>` (hoặc LiveData).
    * Dùng object `Polyline` của Google Maps SDK.
* **Gọi gì từ Android Engine?:** Gọi render lên **Main Thread (UI Thread)**. Cứ mỗi lần Room DB có điểm mới được insert ở Bước 3, Flow sẽ tự động trigger `ViewModel` update lại `UiState`, view nhận State mới sẽ lấy tập điểm hiện tại vẽ lại `Polyline` nối tiếp.

---

Với luồng này, app của bạn có thể hoạt động hoàn hảo ngay cả khi người dùng chạy bộ vào vùng sâu vùng xa không có 4G. Dữ liệu cứ âm thầm lưu vào Room DB, và khi có mạng lại, Bước 4 sẽ gom toàn bộ điểm `isSynced = false` gửi lên server một cách trơn tru.

Bạn muốn bắt đầu implement module nào trước? Chúng ta có thể setup **Room Database (Local Storage)** trước để làm nền móng lưu trữ, hoặc thiết lập **WalkTrackerService (Foreground Service)** để giải quyết bài toán chạy ngầm lấy GPS.