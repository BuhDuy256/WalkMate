Tuyệt vời. Áp dụng chính xác cấu trúc **MVVM + UiState + DDD-lite** của bạn cùng với luồng **Batching Sync** và **Offline-first**, đây là đề xuất chi tiết cách bố trí các file cho tính năng GPS Path Tracing.

Tôi sẽ lấy tên feature là `tracking` (cho UI) và `session` (cho Domain) để minh họa.

```text
frontend/src/main/java/com/walkmate/
├── core/
│   ├── util/
│   │   ├── LocationPermissionHelper.java (Android ContextCompat, ActivityCompat)
│   │   └── NotificationUtil.java (NotificationCompat - Tạo thông báo cho Foreground Service)
│   └── service/
│       └── WalkTrackerService.java (Android Service, FusedLocationProviderClient - Chạy ngầm lấy GPS)
│
├── ui/
│   └── tracking/
│       ├── TrackingScreen.java (Google Maps SDK, Polyline, Material Design Components)
│       ├── TrackingViewModel.java (ViewModel - Lấy Flow/LiveData từ Repository)
│       ├── TrackingViewData.java (POJO chứa data đã format để render)
│       ├── TrackingUiState.java (Immutable class: loading, isTracking, list points...)
│       ├── TrackingUiEvent.java (Nút Start, Pause, End)
│       └── TrackingUiEffect.java (Show Toast mất GPS, Navigate qua màn hình kết quả)
│
├── domain/
│   ├── session/
│   │   ├── RoutePoint.java (Domain Model - POJO thuần Java)
│   │   ├── WalkSession.java (Domain Model - Chứa danh sách RoutePoint, tổng quãng đường, trạng thái)
│   │   ├── SessionRepository.java (Interface - Contract giao tiếp với DB/Network)
│   │   ├── SessionTrackingService.java (Domain Service - Chứa logic nghiệp vụ: gom batching, lọc nhiễu GPS)
│   │   └── LocationFilterPolicy.java (Domain Policy - Khai báo rule: accuracy < 20m mới nhận)
│   └── shared/
│
└── data/
    ├── datasource/
    │   ├── remote/
    │   │   ├── api/
    │   │   │   └── SessionApi.java (Retrofit interface - Chứa @POST("/sessions/points"))
    │   │   └── dto/
    │   │       ├── AppendSessionPointsRequest.java (Jackson/Gson - DTO body có List<RoutePointDto>)
    │   │       └── RoutePointDto.java (Jackson/Gson - DTO của từng điểm)
    │   └── local/
    │       ├── dao/
    │       │   └── RoutePointDao.java (Room @Dao - @Insert, @Query lấy các điểm isSynced = false)
    │       └── entity/
    │           └── RoutePointEntity.java (Room @Entity - Có thêm column isSynced)
    ├── mapper/
    │   ├── EntityToDomainMapper.java (MapStruct/Manual - Map RoutePointEntity -> RoutePoint)
    │   └── DomainToDtoMapper.java (MapStruct/Manual - Map RoutePoint -> RoutePointDto)
    └── repository/
        └── SessionRepositoryImpl.java (Room + Retrofit - Thực thi SessionRepository interface)
```

### Giải thích vai trò của các file then chốt trong Flow:

**1. `core/service/WalkTrackerService.java` (Tầng Framework/Core)**

- **Tại sao lại ở `core/`?** Vì `Service` là một component gốc của Android (tương đương Activity). Nó không thuộc về UI rendering (`ui/`), không chứa business rule (`domain/`), và không phải data storage (`data/`).
- **Nhiệm vụ:** Gọi `startForeground()` để sống dai. Khởi tạo `FusedLocationProviderClient`. Khi nhận được tọa độ, nó gọi xuống `SessionTrackingService` (thuộc Domain) để xử lý.

**2. `domain/session/SessionTrackingService.java` (Tầng Domain)**

- **Nhiệm vụ:** Trái tim của nghiệp vụ.
  - Nhận tọa độ thô từ `WalkTrackerService`.
  - Dùng `LocationFilterPolicy` để kiểm tra (Vd: Sai số > 20m -> Vứt).
  - Gọi `SessionRepository.savePoint()` để lưu vào DB.
  - Kiểm tra logic Batching: _Đã đủ 50 điểm chưa?_ Nếu đủ, gọi `SessionRepository.syncBatch()`.

**3. `data/repository/SessionRepositoryImpl.java` (Tầng Data)**

- **Nhiệm vụ:** Orchestrator của data.
  - Khi gọi `savePoint()`: Insert `RoutePointEntity` vào Room DB với `isSynced = false`.
  - Khi gọi `syncBatch()`: Đọc DB lấy danh sách chưa sync -> Map sang `AppendSessionPointsRequest` -> Gọi `SessionApi` gửi lên server = Retrofit -> Thành công thì update DB `isSynced = true`.
  - Trả về `Flow<List<RoutePoint>>` (tự động cập nhật mỗi khi DB thay đổi) lên cho ViewModel.

**4. `ui/tracking/TrackingViewModel.java` (Tầng UI)**

- **Nhiệm vụ:** Không cần biết Service chạy ngầm ra sao hay Retrofit gửi API thế nào. Nó chỉ `collect()` cái `Flow<List<RoutePoint>>` từ `SessionRepository`. Có điểm mới -> Cập nhật `TrackingUiState` -> Đẩy ra `TrackingScreen` vẽ `Polyline`.

Bạn cảm thấy cấu trúc này đã "khớp" với tư duy thiết kế của team bạn chưa? Nếu rồi, chúng ta có thể bắt đầu đi sâu vào code của **Room DB (Data Layer)** hoặc **WalkTrackerService (Framework Layer)** trước.
