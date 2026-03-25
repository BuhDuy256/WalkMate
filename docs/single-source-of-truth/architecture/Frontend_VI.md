# Kiến Trúc Frontend

Dự án WalkMate Android (Java Thuần)

## Tổng Quan & Tech Stack Chốt Hạ (SSOT)

Dựa trên thực tiễn code module GPS Path Tracing và Authentication, kiến trúc Frontend đã được vạch rõ ranh giới để đạt độ tinh gọn tối đa, bảo vệ bộ nhớ và phù hợp nhất với phong cách lập trình Java Android:

1. **Kiến trúc tổng thể:** MVVM (tại UI) + DDD-lite (tại Domain/Data).
2. **Luồng dữ liệu (Asynchronous):** Sử dụng `LiveData` (để UI theo dõi State) + Java `ExecutorService` (cho Background Worker/thực thi luồng phụ). **Tuyệt đối không dùng RxJava hay Coroutines** để tránh learning curve quá dốc và rủi ro Memory Leak.
3. **Dependency Injection (DI):** Manual DI (Service Locator Pattern) thông qua class `WalkMateApplication`. Các Database (Room) và Repository cốt lõi sẽ nằm đây dưới dạng Singleton. Không cài cắm Hilt/Dagger sinh file rác.
4. **Local Storage (Offline-first):** Bắt buộc dùng **Room Database**. Đây là "trái tim" để hứng mảng tọa độ GPS (Polyline) liên tục 3s/lần trong tình huống rớt mạng (Offline-first).
5. **State Management:** MVVM tinh gọn. UI chỉ render một chiều thông qua `LiveData<UiState>`. User action (click) gọi trực tiếp hàm của ViewModel. (Đã gọt bỏ vĩnh viễn khái niệm MVI cồng kềnh như `UiEvent` & `UiEffect`).

---

## 1. Cấu Trúc Thư Mục Chuẩn

```text
frontend/src/main/java/com/walkmate/
├── core/
│   ├── common/
│   ├── util/
│   └── designsystem/
├── ui/
│   ├── main/
│   │   └── MainActivity.java
│   └── <feature-name>/
│       ├── <Feature>Activity.java / <Feature>Fragment.java
│       ├── <Feature>ViewModel.java
│       ├── <Feature>ViewModelFactory.java (Tùy chọn DI)
│       └── <Feature>UiState.java
├── domain/
│   ├── <domain-name>/
│   │   ├── <Domain>.java (Lightweight Model)
│   │   ├── <Domain>Repository.java (Interface)
│   │   ├── <Domain>ErrorCode.java
│   │   └── <Domain>Service.java
│   └── shared/
│       ├── DomainCallback.java
│       └── exception/
└── data/
    ├── datasource/
    │   ├── remote/
    │   │   ├── api/
    │   │   └── dto/
    │   └── local/
    │       ├── dao/
    │       └── entity/ (Room Entities)
    ├── mapper/
    └── repository/
        └── <Domain>RepositoryImpl.java
```

## 2. Trách Nhiệm Từng Layer

| Layer     | Định hướng       | Trách nhiệm                                                                                  |
| --------- | ---------------- | -------------------------------------------------------------------------------------------- |
| `ui/`     | Feature-oriented | Render giao diện tuân theo `UiState`. Bắt sự kiện Click và đẩy thẳng vào Method của ViewModel. Không nhét Logic/Validate. |
| `domain/` | Domain-oriented  | Chứa Business Rule bề mặt (Client-side), Repository Interface (`UserRepository`), Các Model được gọt giũa nhẹ nhất. |
| `data/`   | Technical        | Triển khai logic chọc REST API (Retrofit), lưu Room DB (SQLite), Cấu hình SharedPref. |
| `core/`   | Shared technical | Các helper dùng chung, Constants, Theme. |

## 3. Quy Ước Đặt Tên (MVVM Tinh Gọn)

Bộ khung MVVM đã được cắt tỉa triệt để, xóa sổ hoàn toàn rác boilerplate như `<Feature>ViewData`, `<Feature>UiEvent`, và `<Feature>UiEffect`.

| Thành phần | Mẫu tên                   | Ví dụ                  |
| ---------- | ------------------------- | ---------------------- |
| View       | `<Feature>Activity.java`  | `LoginActivity.java`   |
| ViewModel  | `<Feature>ViewModel.java` | `LoginViewModel.java`  |
| State      | `<Feature>UiState.java`   | `LoginUiState.java`    |
| DI Factory | `<Feature>ViewModelFactory.java`| `LoginViewModelFactory.java`|

## 4. Contract Chuẩn cho UiState (Immutable)

`UiState` phải là Immutable (bất biến) và là Nguồn chân lý duy nhất để vẽ giao diện tại thời điểm hiện tại:

```java
public class IntentUiState {
    private final boolean isLoading;
    private final boolean isSuccess;
    private final String error; // Thường dùng cho lỗi One-time như Toast. Sau khi hiển thị sẽ gọi hàm consumeError() trong ViewModel để gỡ xuống.
    // Các Data Models cần render...

    public IntentUiState(...) { ... }
    
    // Thuần Getters...
}
```

## 5. Luồng Request / Async Chuẩn (Data Flow)

```text
User Action (VD: Nhấn nút Start Tracking)
-> Activity trích xuất dữ liệu, gọi thẳng hàm `viewModel.startTracking()`
-> ViewModel mở `ExecutorService` thả việc nặng xuống Background
-> Gọi API thông qua `Repository` / Ghi dữ liệu Polyline xuống Room DB
-> Kết quả trả về qua `DomainCallback<T>` (Hoặc fetch từ DB lên)
-> ViewModel bắn lệnh `.postValue(new UiState(loading=false, success...))`
-> Activity đang Observe `LiveData<UiState>` lập tức chạy lệnh Update UI tự động.
```

## 6. Các Ràng Buộc Kiến Trúc Cốt Lõi (Hard Constraints)

Đây là những luật thép bắt buộc phải tuân theo khi đóng góp code cho dự án WalkMate:

| Ràng buộc | Trạng thái | Yêu cầu Kỷ luật |
| --------- | ---------- | --------------- |
| **View tự chọc API/Database?** | ❌ NGHIÊM CẤM | View/Activity **tuyệt đối cấm** import các thư viện như `Retrofit` hay `Room`. Mọi xử lý Data phải đi vòng qua `ViewModel` để nhờ `Repository` xử lý hộ. Cầm View đi sửa DB là chém không tha.
| **Đẻ thêm class MVI (UiEvent / UiEffect)?** | ❌ CẤM DÙNG | Ứng dụng vẽ bằng Java XML truyền thống, hãy dùng hàm trực tiếp. Việc áp dụng triết lý MVI đẻ ra quá nhiều class Action Event gây rác codebase và phí phạm thời gian.
| **Giải quyết Dependency Injection (DI)** | ⚙️ BẮT BUỘC | Khai sinh các cục Dependencies khổng lồ (`RoomDatabase`, `AuthRepository`) tại 1 instance độc tôn (Singleton) ở cấp `Application` class (Service Locator Pattern). Cấm cài cắm Hilt/Dagger vào hệ thống.
| **Xử lý Thread/Async Mượt mà** | ⚙️ BẮT BUỘC | Luôn phải tạo luồng phụ `ExecutorService.execute()` khi insert mảng tọa độ vào Room hoặc call HTTP Network. Tỉ lệ rớt frame sẽ về 0. Cấm dùng các lib ngoài chuẩn như RxJava.
