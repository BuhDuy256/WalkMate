Tuyệt vời! Nếu Logcat của bạn đã nhảy tọa độ liên tục thì xin chúc mừng, bạn đã vượt qua một trong những rào cản "khó chịu" nhất của lập trình Android (chạy ngầm và xin quyền). Thiết bị của bạn giờ đã chính thức giao tiếp được với vệ tinh/mạng di động.

Bây giờ, chúng ta bước vào **Bước 4: Trái tim nghiệp vụ - Bộ Não (Domain Tracking Service)**.

### Mục tiêu của Bước 4

Trong Bước 3, `WalkTrackerService` cứ có tọa độ là in ra, không cần biết đúng sai. Nhưng thực tế, GPS đôi khi bị "lạc" (nhảy sai vị trí hàng trăm mét do mây mù, nhà cao tầng).
Ở Bước 4 này, chúng ta sẽ tạo ra một "bộ lọc" ở tầng Domain. Cụ thể:

1. Nhận tọa độ thô từ Service.
2. Kiểm tra xem điểm đó có đủ độ chính xác (Accuracy) không. Nếu nhiễu -> Vứt.
3. Nếu ngon -> Đẩy xuống Repository để lưu vào Room DB.
4. Đếm xem đã đủ 50 điểm chưa để chuẩn bị cho Bước 5 (Gọi API).

---

### 4.1. Tạo Policy lọc nhiễu GPS (Tầng Domain)

Theo đúng tinh thần DDD, các "luật lệ" (Business Rules) nên được tách ra thành các class Policy rõ ràng.

Bạn tạo file: `domain/session/LocationFilterPolicy.java`

```java
package com.walkmate.domain.session;

public class LocationFilterPolicy {
    // Chỉ chấp nhận các điểm có bán kính sai số dưới 20 mét
    private static final float MAX_ACCEPTABLE_ACCURACY_METERS = 20.0f;

    public boolean isValid(RoutePoint point) {
        // Có thể thêm các rule phức tạp hơn ở đây trong tương lai
        // (Ví dụ: vận tốc không được vượt quá tia chớp v.v...)
        return point.getAccuracy() <= MAX_ACCEPTABLE_ACCURACY_METERS;
    }
}
```

### 4.2. Tạo Session Tracking Service (Tầng Domain)

Đây là "người nhạc trưởng" điều phối logic của một buổi đi bộ.

Bạn tạo file: `domain/session/SessionTrackingService.java`

```java
package com.walkmate.domain.session;

public class SessionTrackingService {

    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final SessionRepository sessionRepository;
    private final LocationFilterPolicy filterPolicy;

    // Biến đếm số điểm đã lưu nhưng chưa được gửi lên server
    private int unsyncedPointsCount = 0;

    // Constructor nhận vào Repository (Inversion of Control)
    public SessionTrackingService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
        this.filterPolicy = new LocationFilterPolicy();
    }

    // Hàm này sẽ được WalkTrackerService gọi mỗi 2 giây
    public void processNewLocation(RoutePoint point) {
        // 1. Kiểm tra bằng Policy (Lọc nhiễu)
        if (!filterPolicy.isValid(point)) {
            System.out.println("SessionTracking: Bỏ qua điểm nhiễu (Accuracy = " + point.getAccuracy() + "m)");
            return; // Vứt bỏ, kết thúc luồng
        }

        // 2. Điểm hợp lệ -> Lưu xuống Database
        sessionRepository.saveRoutePoint(point);
        System.out.println("SessionTracking: Đã lưu 1 điểm hợp lệ vào Local DB.");

        // 3. Logic gom nhóm (Batching)
        unsyncedPointsCount++;
        if (unsyncedPointsCount >= BATCH_SIZE_THRESHOLD) {
            triggerBatchSync();
        }
    }

    private void triggerBatchSync() {
        System.out.println("SessionTracking: 💥 ĐÃ ĐỦ " + BATCH_SIZE_THRESHOLD + " ĐIỂM! Chuẩn bị đồng bộ...");
        // (Chúng ta sẽ viết logic gọi API lên Server ở Bước 5 vào đây)

        // Tạm thời reset đếm lại từ đầu
        unsyncedPointsCount = 0;
    }
}
```

_(Lưu ý: Tôi dùng `System.out.println` thay vì `android.util.Log` để file này giữ được độ tinh khiết 100% Java thuần theo đúng triết lý DDD)._

### 4.3. Ráp nối Bước 3 và Bước 4 lại với nhau

Bây giờ, hãy mở class `WalkTrackerService.java` (mà bạn vừa làm ở Bước 3) và sửa lại để nó truyền tọa độ cho `SessionTrackingService`.

**1. Khai báo biến ở đầu class `WalkTrackerService`:**

```java
// Thêm biến này vào dưới chỗ khai báo FusedLocationProviderClient
private com.walkmate.domain.session.SessionTrackingService sessionTrackingService;
```

**2. Khởi tạo nó trong hàm `onCreate()`:**

```java
@Override
public void onCreate() {
    super.onCreate();
    Log.d(TAG, "Service Created");

    // --- THÊM ĐOẠN NÀY ĐỂ KHỞI TẠO DOMAIN SERVICE ---
    // (Giả định bạn có một class WalkMateDatabase chứa Room DB, hãy sửa lại cho đúng tên class của bạn nhé)
    com.walkmate.data.datasource.local.dao.RoutePointDao dao =
            WalkMateDatabase.getInstance(this).routePointDao();

    com.walkmate.domain.session.SessionRepository repository =
            new com.walkmate.data.repository.SessionRepositoryImpl(dao);

    sessionTrackingService = new com.walkmate.domain.session.SessionTrackingService(repository);
    // ------------------------------------------------

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    // ... (Giữ nguyên đoạn tạo locationCallback của bạn)
}
```

**3. Đẩy tọa độ vào bộ não trong `LocationCallback`:**
Bên trong hàm `onLocationResult`, thay vì chỉ in Log, ta map nó sang Domain Model và truyền đi:

```java
locationCallback = new LocationCallback() {
    @Override
    public void onLocationResult(LocationResult locationResult) {
        if (locationResult == null) return;

        for (android.location.Location location : locationResult.getLocations()) {
            // Bước Mapper nhanh: Chuyển Android Location -> Domain RoutePoint
            // (Dùng Constructor số 2 mà chúng ta đã chốt: không truyền ID)
            com.walkmate.domain.session.RoutePoint domainPoint = new com.walkmate.domain.session.RoutePoint(
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getTime(),
                    location.getAccuracy()
            );

            // Bơm vào cho Bộ não xử lý!
            sessionTrackingService.processNewLocation(domainPoint);
        }
    }
};
```

---

### Chạy và Kiểm tra (Dry-Run Logic)

Khi bạn chạy lại app và bấm nút Start:

1. Nếu bạn ngồi yên trong phòng, GPS yếu (Accuracy > 20m), Logcat sẽ báo: `SessionTracking: Bỏ qua điểm nhiễu...` (Nó không hề bị lưu vào DB).
2. Nếu bạn ra ngoài trời (hoặc fake GPS), nó sẽ báo `Đã lưu 1 điểm hợp lệ vào Local DB`.
3. Cứ khoảng 100 giây (nếu lấy 2s/lần), bạn sẽ thấy nó nổ dòng chữ `💥 ĐÃ ĐỦ 50 ĐIỂM! Chuẩn bị đồng bộ...`

Hãy tích hợp đoạn code này vào. Nếu bạn gặp lỗi đỏ ở dòng `WalkMateDatabase.getInstance(this)` do chưa biết cách lấy instance của Room, hãy bảo tôi nhé! Xong bước này là chúng ta có thể sang **Bước 5: Gọi Retrofit để bắn data lên Server**.

### Câu hỏi phản biện:

Chào D!, sau khi đọc kỹ các tài liệu liên quan đến Step 4, Flow Design, Frontend Architecture và tham khảo code base hiện tại, tôi có một số câu hỏi làm rõ (clarifying questions) về những vấn đề tiềm ẩn trong bản kế hoạch (plan) của Bước 4 để đảm bảo app hoạt động ổn định và đúng architecture:

1. Vấn đề về Khởi tạo & Dependency Injection (DI) trong Service: Trong code giả định của WalkTrackerService ở Bước 4, ta đang khởi tạo trực tiếp WalkMateDatabase, SessionRepositoryImpl (thuộc tầng Data) ngay trong class của tầng core/service. Điều này tạo ra sự kết dính chặt (tight coupling) và vi phạm nhẹ rule chia layer. 👉 Câu hỏi: Project của bạn có dự định sử dụng thư viện DI như Hilt/Dagger để inject SessionTrackingService vào WalkTrackerService không? Nếu chỉ dùng manual DI, liệu mình có nên tạo một ServiceLocator hoặc DependencyProvider riêng thay vì khởi tạo ngay trong service?

2. Quản lý trạng thái (State) của unsyncedPointsCount khi Service khởi động lại: WalkTrackerService đang dùng START_STICKY. Nghĩa là nếu hệ điều hành thiếu RAM và kill service, sau đó chạy lại, biến in-memory unsyncedPointsCount trong SessionTrackingService sẽ bị reset về 0. Tuy nhiên, trong local DB có thể vẫn còn 30 điểm isSynced = false chưa gửi. 👉 Câu hỏi: Khi khởi tạo SessionTrackingService, mình có nên thiết kế hàm query xuống DB để đếm (COUNT) số lượng điểm chưa sync làm giá trị khởi tạo cho unsyncedPointsCount không? (Thay vì luôn bắt đầu từ 0).

3. Rủi ro của việc reset counter khi Batch Sync thất bại: Trong SessionTrackingService, khi đếm đủ 50 điểm sẽ trigger sync và ngay lập tức unsyncedPointsCount = 0. Giả sử lúc gọi API bị mất mạng (lỗi ở Bước 5), 50 điểm đó vẫn chưa được gửi. Do counter bị reset, hệ thống sẽ đợi thêm 50 điểm "mới" nữa (tổng cộng 100 điểm) mới trigger sync đợt tiếp theo. 👉 Câu hỏi: Liệu mình có nên cải tiến rule này thành đếm theo tổng số điểm isSynced = false hiện có trong DB mỗi lần có tọa độ mới không, thay vì đếm số điểm mới lưu thêm vào ở session này? Hoặc chỉ giảm counter đi sau khi API trả về HTTP 200?

4. Khuyết thiếu bối cảnh Session (sessionId): Cấu trúc RoutePoint và RoutePointEntity hiện tại chỉ lưu lat, lng, timestamp và accuracy. Khi user kết thúc một lượt đi bộ và hôm sau đi bộ lượt mới, tất cả data đều nằm chung trong bảng route_points. 👉 Câu hỏi: Làm sao để ta phân biệt được các điểm này thuộc về Walk Session nào? Mình có nên bổ sung sessionId vào Domain Model và Entity ngay từ bây giờ trước khi viết batching không?

5. Vấn đề Memory Leak với ExecutorService ở Data Layer: SessionRepositoryImpl tự khởi tạo một ExecutorService tốn tài nguyên chạy nền. 👉 Câu hỏi: Khi kết thúc tracking và WalkTrackerService bị destroy (ở Bước 3 có gọi fusedLocationClient.removeLocationUpdates), mình có nên thêm một hàm cleanUp() hoặc shutdown() vào contract của SessionRepository để đóng ExecutorService lại tránh rò rỉ bộ nhớ không?

Bạn hãy xem qua những vấn đề này, vì giải quyết từ Bước 4 sẽ giúp Bước 5 (call API) và Bước 6 (UI) chuẩn xác hơn rất nhiều!

### Response từ Geminni

1. Vấn đề DI (Dependency Injection) trong Service
   Đánh giá: Bạn hoàn toàn đúng. Khởi tạo RepositoryImpl và Database trực tiếp trong Service là một "code smell" (mùi code xấu), vi phạm nguyên tắc Đảo ngược phụ thuộc (Dependency Inversion) của SOLID và DDD.

Giải pháp (Manual DI): Nếu bạn chưa dùng Hilt/Dagger, chúng ta KHÔNG NÊN nhét Hilt vào ngay lúc này vì nó sẽ làm dự án cồng kềnh. Hãy dùng Manual DI (Service Locator Pattern).

Cách làm: Ta sẽ tạo một class WalkMateApplication kế thừa android.app.Application. Class này chạy đầu tiên khi app mở và sống suốt vòng đời app. Nó sẽ đóng vai trò là "Container" chứa các instance (Database, Repository).

Java
// Khi cần lấy Repository trong Service, ta chỉ cần gọi:
SessionRepository repository = ((WalkMateApplication) getApplication()).getSessionRepository();
=> Kết quả: Service bị mù hoàn toàn về Data Layer, chỉ biết giao tiếp qua interface SessionRepository. Hoàn hảo cho DDD.

2 & 3. Quản lý biến đếm unsyncedPointsCount và Rủi ro khi Call API xịt
Đánh giá: Đây là 2 lỗ hổng chí mạng của việc "Lưu trạng thái trên RAM (In-memory state)". Nếu service bị kill (START_STICKY) hoặc rớt mạng, biến đếm sẽ sai bét, dẫn đến việc data bị kẹt không bao giờ được gửi.

Giải pháp (Single Source of Truth): Hãy vứt bỏ hoàn toàn biến int unsyncedPointsCount trong RAM. Chúng ta sẽ dùng chính Local DB làm Nguồn Sự Thật Duy Nhất.

Cách làm: 1. Thêm một hàm đếm vào RoutePointDao:
@Query("SELECT COUNT(id) FROM route_points WHERE isSynced = 0") int getUnsyncedCount(); 2. Mỗi khi SessionTrackingService lưu 1 điểm mới, nó gọi hàm getUnsyncedCount() này. Nếu kết quả >= 50, nó mới trigger Batch Sync. SQLite thực thi lệnh COUNT trong chưa tới 1 mili-giây, nên performance không hề bị ảnh hưởng.
=> Kết quả: Dù app bị crash, tắt máy khởi động lại, hay mất mạng 10 lần... cứ có mạng và DB đếm ra >= 50 điểm chưa sync, nó sẽ gửi. Cực kỳ "trâu bò" (robust).

4. Khuyết thiếu bối cảnh Session (sessionId)
   Đánh giá: 10/10 điểm cho phát hiện này. Không có sessionId, ngày mai user đi bộ, bản đồ sẽ nối 1 đường thẳng từ điểm kết thúc của ngày hôm qua tới điểm bắt đầu của ngày hôm nay (vẽ một đường cắt ngang thành phố).

Giải pháp: Phải bổ sung sessionId (kiểu String dạng UUID hoặc long timestamp) ngay lập tức.

Cách làm: 1. Thêm private final String sessionId; vào RoutePoint và RoutePointEntity. 2. Khi user bấm nút "Start" trên màn hình, UI sẽ sinh ra một UUID.randomUUID().toString() và truyền nó qua Intent để khởi động WalkTrackerService. Service sẽ dùng ID này gắn vào mọi điểm GPS nhận được.

5. Memory Leak với ExecutorService
   Đánh giá: Một lo ngại rất chuyên nghiệp. Khởi tạo ExecutorService vô tội vạ sẽ làm cạn kiệt tài nguyên luồng (Thread pool exhaustion) của hệ điều hành.

Giải pháp: Phụ thuộc vào cách ta giải quyết Câu số 1 (DI).

Nếu ta dùng WalkMateApplication (Service Locator) để tạo ra một Singleton Repository (chỉ có 1 instance duy nhất tồn tại trong suốt vòng đời app), thì ExecutorService bên trong nó cũng là duy nhất.

Khi app sống, ThreadPool sống để phục vụ DB. Khi user tắt hẳn app, hệ điều hành sẽ tự thu hồi RAM. Ta KHÔNG CẦN phải viết hàm shutdown() hay dọn dẹp nữa, vì chi phí tạo lại ThreadPool mỗi lần start/stop tracking còn tốn kém hơn việc giữ 1 thread ngầm.

### Implementation Plan

Implementation Plan: Step 4 - GPS Path Tracing (Domain Tracking Service)
This implementation plan incorporates the architectural refinements for WalkMate: Manual DI (Service Locator), DB-based Batch Counting (Single Source of Truth), and Session Context Awareness.

User Review Required
Before proceeding into Execution Mode, please verify that the approaches (especially the integration of WalkMateApplication and Session ID) align perfectly with your expectations. Check out the Manual DI Setup section.

Proposed Changes
Domain Layer (Models & Contract)
[MODIFY]
RoutePoint.java
(Domain Model)
Add private final String sessionId; to represent the Walk Session context.
Update both constructors and add getSessionId() getter.
[MODIFY]
SessionRepository.java
(Interface)
Add int getUnsyncedCount(); to allow the Domain Service to query the current batch limit without keeping it in memory.
Data Layer (Entity, DAO, Repository)
[MODIFY]
RoutePointEntity.java
(Room Entity)
Add public String sessionId; field.
Update the constructor.
[MODIFY]
RoutePointDao.java
Add @Query("SELECT COUNT(id) FROM route_points WHERE isSynced = 0") int getUnsyncedCount();
[MODIFY]
SessionRepositoryImpl.java
Update the domain-to-entity and entity-to-domain mapping logic to include sessionId.
Implement getUnsyncedCount() by synchronously calling the DAO.
Core Layer (Manual DI & Framework Services)
[NEW] WalkMateApplication.java
Create a custom Application class.
Initialize
WalkMateDatabase
and SessionRepositoryImpl as singletons here.
Expose public SessionRepository getSessionRepository().
[MODIFY]
AndroidManifest.xml
Register android:name=".WalkMateApplication" within the <application> tag.
[MODIFY]
WalkTrackerService.java
Read sessionId from intent.getStringExtra("SESSION_ID") in
onStartCommand()
.
Obtain
SessionRepository
via
((WalkMateApplication) getApplication()).getSessionRepository()
.
Instantiate SessionTrackingService.
Inside LocationCallback, pass sessionId when mapping android.location.Location to
RoutePoint
and trigger the Domain Service.
Domain Service (Step 4 Logic)
[NEW] LocationFilterPolicy.java
Implement rule: Accept GPS point only if accuracy <= 20.0f.
[NEW] SessionTrackingService.java
Take
SessionRepository
and LocationFilterPolicy in the constructor.
processNewLocation(RoutePoint point):
Validate with LocationFilterPolicy.
If valid, sessionRepository.saveRoutePoint(point).
Query sessionRepository.getUnsyncedCount(). If >= 50, call triggerBatchSync(). (The actual Retrofit logic for triggerBatchSync will be done in Step 5).
Verification Plan
Automated/Compilation Tests
Rely on standard Java compilation (./gradlew assembleDebug or similar if wrapper is set up) to ensure no errors in WalkMateApplication, DB mapping, or domain logic.
Manual Verification
Test App Boot: Ensure the app builds and runs without crashing (verifying that WalkMateApplication works correctly).
Test GPS Service Start: Pass a mock SESSION_ID via Intent to
WalkTrackerService
.
Test Batch Threshold Log: Start the service and spoof locations in the Android Emulator. Verify that Logcat prints SessionTracking: 💥 ĐÃ ĐỦ 50 ĐIỂM! Chuẩn bị đồng bộ... exactly when the database surpasses 50 unsynced points, resisting resets if the service is stopped/started

### Review from Gemini

Bản kế hoạch (Implementation Plan) của bạn thực sự xuất sắc. Nó giống hệt một tài liệu thiết kế hệ thống (System Design Document) chuẩn mực trước khi bước vào giai đoạn code thực tế. Bạn đã tóm gọn và hệ thống hóa toàn bộ các bản vá kiến trúc (Manual DI, Session ID, DB-based Counter) một cách hoàn hảo.

Tôi **DUYỆT** bản kế hoạch này về mặt tổng thể. Tuy nhiên, với tư cách là người review code cho bạn, tôi phát hiện ra **1 lỗ hổng kỹ thuật chí mạng về Đa luồng (Threading & Race Condition)** ẩn sâu trong luồng logic của Bước 4 có thể làm app bị Crash ngay lập tức khi chạy.

Dưới đây là phân tích và đề xuất điều chỉnh trước khi bạn gõ phím:

### 🚨 Lỗ hổng kỹ thuật: Cạm bẫy Đa luồng (Race Condition & Main Thread Exception)

Hãy nhìn kỹ vào luồng chạy dự kiến của bạn trong `SessionTrackingService`:

```java
// Logic dự kiến của bạn:
if (valid) {
    sessionRepository.saveRoutePoint(point); // (1) Gọi hàm lưu
    int count = sessionRepository.getUnsyncedCount(); // (2) Gọi hàm đếm
    if (count >= 50) triggerBatchSync();
}
```

**Vấn đề số 1 (Crash Room DB):**
Hàm `LocationCallback` ở Bước 3 đang được cấu hình chạy trên luồng chính (`Looper.getMainLooper()`). Do đó, `SessionTrackingService` cũng đang chạy trên luồng chính. Nếu hàm `getUnsyncedCount()` gọi trực tiếp lệnh DAO để truy vấn SQLite đồng bộ (synchronous), Room sẽ ném ra lỗi kinh điển: `java.lang.IllegalStateException: Cannot access database on the main thread`.

**Vấn đề số 2 (Race Condition - Chạy đua thời gian):**
Ở Bước 2, chúng ta đã thiết kế `sessionRepository.saveRoutePoint()` sử dụng `ExecutorService` để chạy ngầm (Asynchronous).
Khi bạn gọi dòng (1), nó chỉ _ra lệnh_ cho luồng phụ bắt đầu lưu DB, và code lập tức nhảy sang dòng (2) ngay khi việc lưu _chưa kịp hoàn thành_. Kết quả: Hàm đếm `getUnsyncedCount()` sẽ luôn trả về số lượng điểm cũ (thiếu mất điểm vừa lưu).

---

### 🛠️ Phương án điều chỉnh (Sửa đổi bản kế hoạch)

Để giải quyết triệt để vấn đề này mà vẫn giữ nguyên vẹn triết lý kiến trúc DDD của bạn, chúng ta cần đồng bộ hóa (synchronize) quá trình Lưu và Đếm.

**Đề xuất thay đổi:** Chuyển `ExecutorService` từ tầng Data (`SessionRepositoryImpl`) lên tầng Core/Domain (`WalkTrackerService` hoặc `SessionTrackingService`).

Cụ thể, bạn hãy cập nhật bản kế hoạch của mình như sau:

**[MODIFY] `SessionRepositoryImpl.java`**

- Tháo bỏ `ExecutorService` bên trong class này.
- Biến hàm `saveRoutePoint` và `getUnsyncedCount` thành các hàm chạy đồng bộ (Synchronous) thuần túy (tức là gọi DAO trực tiếp không cần bọc background thread). Tầng Data chỉ làm đúng nhiệm vụ thao tác DB, việc luồng nào gọi nó là trách nhiệm của tầng trên.

**[MODIFY] `SessionTrackingService.java` (Hoặc `WalkTrackerService`)**

- Đưa `ExecutorService` (loại SingleThread) vào đây.
- Đẩy toàn bộ khối logic xử lý vào luồng phụ để đảm bảo tính tuần tự.

Đoạn code định hướng cho `SessionTrackingService` sẽ trông như thế này:

```java
private final ExecutorService executorService = Executors.newSingleThreadExecutor();

public void processNewLocation(RoutePoint point) {
    // Ném toàn bộ công việc tính toán, lưu, và đếm sang một luồng phụ duy nhất
    executorService.execute(() -> {
        if (!filterPolicy.isValid(point)) return;

        // Luồng phụ này sẽ đứng đợi (block) cho đến khi lưu xong
        sessionRepository.saveRoutePoint(point);

        // Sau khi lưu xong chắc chắn 100%, mới tiến hành đếm
        int count = sessionRepository.getUnsyncedCount();
        if (count >= BATCH_SIZE_THRESHOLD) {
            triggerBatchSync();
        }
    });
}
```

### Kết luận

Ngoài vấn đề đa luồng trên, toàn bộ các setup về `WalkMateApplication` (Manual DI), cách chèn `sessionId` vào Intent và Entity của bạn đều đạt điểm 10 tuyệt đối về mặt System Design.

Bạn đã nắm rõ nguyên nhân và cách khắc phục của cái bẫy đa luồng này chưa? Bạn có muốn tôi cung cấp luôn đoạn code hoàn chỉnh cho `SessionTrackingService` và `WalkMateApplication` để bạn bắt đầu **Execution Mode** ngay bây giờ không?

### WalkThrough

Walkthrough: GPS Path Tracing - Step 4 Implementation
Tôi đã hoàn tất toàn bộ việc triển khai (Implementation) cho Bước 4 dựa trên bản kế hoạch đã thống nhất, đặc biệt chú trọng vào việc vá lỗ hổng Đa luồng (Threading), thiết lập Manual DI và củng cố Single Source of Truth cho Batching.

Những Thay Đổi Chính Đã Thực Hiện

1. Kiến trúc Manual DI (Service Locator)
   Đã tạo
   WalkMateApplication.java
   kế thừa
   Application
   .
   WalkMateApplication
   khởi tạo
   WalkMateDatabase
   và
   SessionRepositoryImpl
   như một Singleton và duy trì chúng suốt vòng đời của app.
   File
   AndroidManifest.xml
   đã được cập nhật (android:name=".WalkMateApplication") để trỏ đến custom Application class này.
   Tại sao? Điều này giúp API của
   SessionRepository
   rạch ròi.
   WalkTrackerService
   hoàn toàn không biết đến RoomDatabase hay implementation cụ thể ở tầng Data, tuân thủ chặt chẽ DDD.
2. An Toàn Đa Luồng (Thread Safety & No MainThreadException)
   Tầng Data:
   SessionRepositoryImpl
   đã bị tước bỏ ExecutorService. Phương thức
   saveRoutePoint
   và
   getUnsyncedCount
   hiện tại chạy hoàn toàn đồng bộ (synchronous).
   Tầng Domain:
   SessionTrackingService
   được trang bị một SingleThreadExecutor. Khi có điểm
   RoutePoint
   mới, service này đẩy toàn bộ logic từ lọc điểm (filterPolicy), gọi Reposiotry lưu DB, và truy vấn số lượng DB (
   getUnsyncedCount
   ) vào Executor này.
   Tại sao? Việc đồng bộ hóa này bóp nghẹt nguy cơ Race Condition. Tọa độ lưu vào hệ thống luôn đảm bảo đã nằm gọn trong file SQLite vật lý trước khi tiến hành đếm số lượng, tuyệt đối không bị dính ngoại lệ MainThread của Room.
3. Đồng bộ hóa bối cảnh Session (sessionId)
   RoutePoint
   (Domain Model) và
   RoutePointEntity
   (Entity) đã được bổ sung sessionId thông qua constructor.
   SessionRepositoryImpl
   đã được cấu hình Mapper chuyển đổi qua lại hai object này, mang theo ID một cách toàn vẹn.
   Ở tầng giao tiếp ngoài cùng,
   WalkTrackerService
   đã đọc SESSION_ID từ Intent.getStringExtra() lúc
   onStartCommand
   và gán nó vào các điểm GPS thô thu được.
4. "Nguồn Sự Thật Duy Nhất" cho Batching
   Tạm biệt vòng lặp trên RAM.
   RoutePointDao
   nay đã có câu query chuyên dụng siêu nhẹ: @Query("SELECT COUNT(id) FROM route_points WHERE isSynced = 0") int getUnsyncedCount();.
   Chừng nào hệ thống đếm ra >= 50 điểm chưa đi, nó sẽ kích hoạt Log: 💥 ĐÃ ĐỦ 50 ĐIỂM! Chuẩn bị đồng bộ....
   Hướng dẫn Test Nhanh (Verify)
   Bạn có thể chạy thử đoạn code này để xác nhận kiến trúc:

Tạo một Nút Start ở bất kỳ màn hình nào với logic chuyển Intent có chứa SESSION_ID:
java
Intent serviceIntent = new Intent(this, WalkTrackerService.class);
serviceIntent.putExtra("SESSION_ID", UUID.randomUUID().toString()); // Gắn Session ID giả định
ContextCompat.startForegroundService(this, serviceIntent);
Quan sát Logcat, bạn sẽ thấy SessionTracking: Đã lưu 1 điểm... Tiền trình ID: [UUID].
Stop app (giả lập bị Crash hoặc Kill) và chạy lại. Logcat khi đếm mốc 50 điểm sẽ kích hoạt chính xác bất kể việc app có khởi động lại hay không!
