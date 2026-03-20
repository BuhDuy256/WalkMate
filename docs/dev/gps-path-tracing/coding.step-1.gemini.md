Tư duy "vừa code vừa hiểu" và xây dựng từng bước (incremental development) của bạn cực kỳ chuẩn. Đối với một tính năng phức tạp như GPS Tracking chạy ngầm, nếu chúng ta code toàn bộ từ trên xuống dưới rồi mới chạy thử, khi có bug sẽ rất khó debug vì không biết lỗi ở UI, ở Service hay ở Database.

Nguyên tắc của workflow này là **"Xây từ móng lên mái" (Inside-Out)**: Chúng ta sẽ đi từ lõi dữ liệu, đến logic nghiệp vụ, lấy GPS thực tế, và cuối cùng mới là vẽ lên UI. Ở mỗi bước, tôi sẽ giải thích _tại sao_ chúng ta lại viết đoạn code đó.

Dưới đây là **Lộ trình 6 Bước** để hoàn thiện tính năng này. Hôm nay, chúng ta sẽ đi vào **Bước 1**.

---

### Lộ Trình Triển Khai (Coding Roadmap)

- **Bước 1: Nền móng Dữ liệu (Domain Model & Local DB).** Định nghĩa đối tượng `RoutePoint` và tạo bảng SQLite (Room) để lưu trữ. (Không có chỗ lưu thì không thể tracking).
- **Bước 2: Contract & Repository (Data Layer).** Khai báo Interface ở Domain và implement nó ở Data để có thể thêm/đọc dữ liệu từ DB.
- **Bước 3: Trái tim hệ thống (Foreground Service & FusedLocation).** Cài đặt Service chạy ngầm, xin quyền, và lấy tọa độ GPS thô in ra Logcat.
- **Bước 4: Bộ não (Domain Tracking Service).** Lắp bộ lọc (bỏ nhiễu GPS) và logic lưu vào DB. Kết nối Bước 3 với Bước 2.
- **Bước 5: Đồng bộ (Batching Sync & Network).** Cài đặt logic "đủ 50 điểm thì gửi Retrofit" và cập nhật trạng thái `isSynced`.
- **Bước 6: Giao diện (UI & Map).** ViewModel đọc dữ liệu từ DB và vẽ Polyline lên Google Maps.

---

### Bắt đầu BƯỚC 1: Nền móng Dữ liệu (Models & Database)

Ở bước này, mục tiêu là định nghĩa hình hài của "1 điểm GPS" trong app của bạn. Theo kiến trúc DDD-lite của bạn, một thực thể sẽ có 2 phiên bản: 1 cho Domain (nghiệp vụ thô) và 1 cho Room (database).

#### 1. Tạo Domain Model (Lõi nghiệp vụ)

**Tại sao cần?** Đây là object thuần Java, không phụ thuộc vào bất kỳ thư viện Android hay Room nào. UI và Domain logic chỉ nói chuyện bằng object này.

Bạn tạo file: `domain/session/RoutePoint.java`

```java
package com.walkmate.domain.session;

public class RoutePoint {
    private final double lat;
    private final double lng;
    private final long timestamp;
    private final float accuracy; // Rất quan trọng để lọc nhiễu

    public RoutePoint(double lat, double lng, long timestamp, float accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }

    // Các getter: getLat(), getLng(), getTimestamp(), getAccuracy()
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public long getTimestamp() { return timestamp; }
    public float getAccuracy() { return accuracy; }
}
```

#### 2. Tạo Room Entity (Ánh xạ xuống Database)

**Tại sao cần?** Để lưu xuống SQLite, Android cần biết tên bảng, khóa chính là gì. Ta thêm trường `isSynced` ở đây để phục vụ chiến lược Batching đã chốt.

Bạn tạo file: `data/datasource/local/entity/RoutePointEntity.java`

```java
package com.walkmate.data.datasource.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "route_points")
public class RoutePointEntity {
    @PrimaryKey(autoGenerate = true)
    public long id; // Khóa chính tự tăng của DB

    public double lat;
    public double lng;
    public long timestamp;
    public float accuracy;

    // Cờ đánh dấu điểm này đã được gửi lên server chưa
    public boolean isSynced = false;

    // Constructor, getters, setters (nếu cần)
    public RoutePointEntity(double lat, double lng, long timestamp, float accuracy) {
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }
}
```

#### 3. Tạo DAO (Data Access Object)

**Tại sao cần?** Đây là nơi định nghĩa các câu lệnh SQL để tương tác với bảng `route_points`.

Bạn tạo file: `data/datasource/local/dao/RoutePointDao.java`

```java
package com.walkmate.data.datasource.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;
import kotlinx.coroutines.flow.Flow; // Nếu bạn dùng Kotlin Coroutines/Flow sau này, hoặc dùng LiveData/RxJava tùy tech stack Java của bạn

@Dao
public interface RoutePointDao {

    // Lưu 1 điểm mới lấy từ GPS
    @Insert
    void insertPoint(RoutePointEntity point);

    // Dùng cho Bước 6 (UI): Lấy toàn bộ điểm của session hiện tại để vẽ Map
    // (Giả sử ta chỉ quan tâm các điểm của lần chạy hiện tại, sau này bạn có thể thêm session_id)
    @Query("SELECT * FROM route_points ORDER BY timestamp ASC")
    Flow<List<RoutePointEntity>> getAllPointsFlow(); // Đổi thành LiveData<List<RoutePointEntity>> nếu bạn dùng thuần Java/Android AAC

    // Dùng cho Bước 5 (Sync): Lấy các điểm chưa đồng bộ để gom Batch
    @Query("SELECT * FROM route_points WHERE isSynced = 0 ORDER BY timestamp ASC")
    List<RoutePointEntity> getUnsyncedPoints();

    // Dùng cho Bước 5 (Sync): Đánh dấu các ID đã gửi thành công
    @Query("UPDATE route_points SET isSynced = 1 WHERE id IN (:pointIds)")
    void markAsSynced(List<Long> pointIds);
}
```

---

**Nhiệm vụ của bạn lúc này:**

1. Khởi tạo 3 file trên trong project của bạn.
2. Thiết lập (setup) `RoomDatabase` trong module Di/Core (nếu bạn chưa làm). Đảm bảo app build thành công không có lỗi syntax.

Hãy đọc kỹ 3 file trên. Bạn có thắc mắc gì về việc tại sao lại tách ra `RoutePoint` và `RoutePointEntity`, hay câu query nào trong `Dao` chưa rõ ràng không? Nếu mọi thứ đã thông suốt và code đã build được, hãy báo lại để tôi dắt bạn sang **Bước 2**.
