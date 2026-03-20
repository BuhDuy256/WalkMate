Tuyệt vời! Việc bạn đã thiết lập xong phần nền móng Database (Entity và DAO) có nghĩa là app của bạn đã sẵn sàng để lưu trữ dữ liệu offline.

Bây giờ, chúng ta sẽ bước sang **Bước 2: Xây dựng cầu nối (Contract & Repository)**.

Mục tiêu của Bước 2 là tạo ra một "bản hợp đồng" (Interface) ở tầng `domain/` để định nghĩa CÁC HÀNH ĐỘNG mà app có thể làm với dữ liệu GPS. Sau đó, chúng ta sẽ viết class thực thi (Implementation) ở tầng `data/` để hoàn thành bản hợp đồng đó bằng cách gọi xuống Room DB.

_(Lưu ý nhỏ: Để phục vụ cho logic đồng bộ (Sync) sau này, bạn hãy mở file `RoutePoint.java` ở Bước 1 và thêm trường `private final long id;` vào giúp tôi nhé. Chúng ta cần ID để biết điểm nào đã gửi, điểm nào chưa)._

---

### 1. Khai báo Contract ở tầng Domain

**Tại sao cần?** Tầng Domain (nghiệp vụ) và UI không cần quan tâm bạn lưu dữ liệu bằng SQLite, Room, hay Firebase. Nó chỉ quan tâm: "Cho tôi hàm để lưu 1 điểm" và "Cho tôi hàm để lấy danh sách điểm ra vẽ".

Bạn tạo file: `domain/session/SessionRepository.java`

```java
package com.walkmate.domain.session;

import androidx.lifecycle.LiveData;
import java.util.List;

// Đây là bản hợp đồng nghiệp vụ
public interface SessionRepository {

    // Dùng cho WalkTrackerService: Ném 1 điểm GPS vào DB
    void saveRoutePoint(RoutePoint point);

    // Dùng cho TrackingViewModel (UI): Lắng nghe liên tục các điểm trong DB để vẽ Polyline
    // (Đây chính là sự châm chước nguyên tắc DDD mà chúng ta đã thống nhất: cho phép dùng LiveData ở Domain)
    LiveData<List<RoutePoint>> getPointsOfCurrentSession();

    // Dùng cho Sync Logic (Bước 5): Lấy ra các điểm chưa được gửi lên Server
    List<RoutePoint> getUnsyncedPoints();

    // Dùng cho Sync Logic (Bước 5): Đánh dấu các điểm đã gửi thành công
    void markPointsAsSynced(List<Long> pointIds);
}
```

### 2. Thực thi Contract ở tầng Data

**Tại sao cần?** Đây là nơi "làm việc tay chân". Class này sẽ nhận `RoutePoint` từ Domain, biến nó thành `RoutePointEntity` rồi nhét vào Room DB. Ngược lại, nó lấy `RoutePointEntity` từ Room DB, biến thành `RoutePoint` rồi đẩy lên cho Domain/UI.

Bạn tạo file: `data/repository/SessionRepositoryImpl.java`

```java
package com.walkmate.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.walkmate.data.datasource.local.dao.RoutePointDao;
import com.walkmate.data.datasource.local.entity.RoutePointEntity;
import com.walkmate.domain.session.RoutePoint;
import com.walkmate.domain.session.SessionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionRepositoryImpl implements SessionRepository {

    private final RoutePointDao routePointDao;

    // Vì ta không dùng RxJava hay Coroutines, ta dùng ExecutorService (Java thuần)
    // để chạy các tác vụ thêm/sửa/xóa DB ở background, tránh làm đơ Main Thread (UI).
    private final ExecutorService executorService;

    // Constructor (Sau này nếu bạn dùng Dagger/Hilt thì sẽ thêm @Inject ở đây)
    public SessionRepositoryImpl(RoutePointDao routePointDao) {
        this.routePointDao = routePointDao;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void saveRoutePoint(RoutePoint point) {
        // Chạy ngầm: Tránh lỗi "Cannot access database on the main thread" của Room
        executorService.execute(() -> {
            // Bước Mapper: Chuyển từ Domain Model sang Entity để Room hiểu
            RoutePointEntity entity = new RoutePointEntity(
                    point.getLat(),
                    point.getLng(),
                    point.getTimestamp(),
                    point.getAccuracy()
            );
            routePointDao.insertPoint(entity);
        });
    }

    @Override
    public LiveData<List<RoutePoint>> getPointsOfCurrentSession() {
        // Room trả về LiveData<List<RoutePointEntity>>.
        // Nhưng UI và Domain chỉ muốn nhận LiveData<List<RoutePoint>>.
        // Giải pháp: Dùng Transformations.map để "dịch" (map) dữ liệu ngay khi Room nhả ra.
        return Transformations.map(routePointDao.getAllPointsFlow(), entities -> {
            List<RoutePoint> domainPoints = new ArrayList<>();
            for (RoutePointEntity entity : entities) {
                // Bước Mapper: Chuyển từ Entity ngược lại thành Domain Model
                domainPoints.add(new RoutePoint(
                        entity.id, // Nhớ thêm id vào class RoutePoint ở Bước 1 nhé
                        entity.lat,
                        entity.lng,
                        entity.timestamp,
                        entity.accuracy
                ));
            }
            return domainPoints;
        });
    }

    @Override
    public List<RoutePoint> getUnsyncedPoints() {
        // Lưu ý: Hàm này sẽ được gọi từ Background Thread trong logic Sync sau này,
        // nên ta gọi trực tiếp DB mà không cần ExecutorService bọc ngoài.
        List<RoutePointEntity> entities = routePointDao.getUnsyncedPoints();
        List<RoutePoint> domainPoints = new ArrayList<>();
        for (RoutePointEntity entity : entities) {
            domainPoints.add(new RoutePoint(entity.id, entity.lat, entity.lng, entity.timestamp, entity.accuracy));
        }
        return domainPoints;
    }

    @Override
    public void markPointsAsSynced(List<Long> pointIds) {
        executorService.execute(() -> {
            routePointDao.markAsSynced(pointIds);
        });
    }
}
```

### Điểm nhấn quan trọng trong đoạn code trên:

1. **`ExecutorService`**: Room Database cấm bạn gọi lệnh `insert`, `update`, `delete` trên Main Thread vì nó sẽ làm màn hình bị giật lag. Bằng cách nhét code vào `executorService.execute()`, ta đẩy việc nặng đó ra một luồng phụ. Đây chính là cách giải quyết bài toán đa luồng bằng Java thuần an toàn nhất.
2. **`Transformations.map`**: Hàm `getAllPointsFlow()` của bạn ở Bước 1 trả về `LiveData<Entity>`. Phép thuật của `Transformations.map` là nó đứng ở giữa, chộp lấy `Entity`, biến nó thành `RoutePoint` (Domain Model), rồi mới trả về `LiveData<RoutePoint>` cho UI. Nhờ vậy, Data Layer không bao giờ "leak" (rò rỉ) cái `Entity` ra ngoài màn hình. Tuân thủ 100% luật kiến trúc của bạn.

Bạn hãy đưa đoạn code này vào project. Nếu mọi thứ xanh mượt và không báo lỗi, bạn có muốn chúng ta chuyển sang **Bước 3: Khởi tạo Trái tim hệ thống - Cài đặt Foreground Service để xin quyền và lấy tọa độ GPS thô** không?
