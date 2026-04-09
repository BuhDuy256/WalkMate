# Kiến Trúc Frontend

Dự án WalkMate Android (Java Thuần)

## Tổng Quan & Tech Stack Chốt Hạ (SSOT)

Dựa trên thực tiễn các module hiện tại, kiến trúc Frontend đã được vạch rõ ranh giới để đạt độ tinh gọn tối đa, bảo vệ bộ nhớ và phù hợp nhất với phong cách lập trình Java Android:

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
│       ├── <Feature>PagerAdapter.java (Tùy chọn nếu có tab)
│       ├── <sub-feature-name>/ (Tùy chọn)
│       │   ├── <SubFeature>Fragment.java
│       │   ├── <SubFeature>ViewModel.java
│       │   ├── <SubFeature>ViewModelFactory.java
│       │   └── <SubFeature>UiState.java
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
    │   │       ├── request/
    │   │       │   └── <domain-name>/
    │   │       │       └── <Verb><Domain>Request.java
    │   │       └── response/
    │   │           ├── ApiResponse.java (Generic Response Wrapper)
    │   │           └── <domain-name>/
    │   │               └── <Domain>Response.java
    │   └── local/
    │       ├── dao/
    │       └── entity/ (Room Entities)
    ├── mapper/
    └── repository/
        └── <Domain>RepositoryImpl.java
```

Ví dụ áp dụng thực tế (tham khảo):

1. `ui/auth/` có container `AuthActivity` + `AuthPagerAdapter`, sub-feature `login/`, `register/`.
2. `ui/intent/` có container `IntentActivity` + `IntentPagerAdapter`, sub-feature `create/`, `matching/`, `result/`.

## 2. Trách Nhiệm Từng Layer

| Layer     | Định hướng       | Trách nhiệm                                                                                                               |
| --------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `ui/`     | Feature-oriented | Render giao diện tuân theo `UiState`. Bắt sự kiện Click và đẩy thẳng vào Method của ViewModel. Không nhét Logic/Validate. |
| `domain/` | Domain-oriented  | Chứa Business Rule bề mặt (Client-side), Repository Interface (`UserRepository`), Các Model được gọt giũa nhẹ nhất.       |
| `data/`   | Technical        | Triển khai logic chọc REST API (Retrofit), lưu Room DB (SQLite), Cấu hình SharedPref.                                     |
| `core/`   | Shared technical | Các helper dùng chung, Constants, Theme.                                                                                  |

## 3. Quy Ước Đặt Tên (MVVM Tinh Gọn)

Bộ khung MVVM đã được cắt tỉa triệt để, xóa sổ hoàn toàn rác boilerplate như `<Feature>ViewData`, `<Feature>UiEvent`, và `<Feature>UiEffect`.

| Thành phần | Mẫu tên                                                | Ví dụ                                     |
| ---------- | ------------------------------------------------------ | ----------------------------------------- |
| View       | `<Feature>Activity.java` hoặc `<Feature>Fragment.java` | `AuthActivity.java`, `LoginFragment.java` |
| ViewModel  | `<Feature>ViewModel.java`                              | `LoginViewModel.java`                     |
| State      | `<Feature>UiState.java`                                | `LoginUiState.java`                       |
| DI Factory | `<Feature>ViewModelFactory.java`                       | `LoginViewModelFactory.java`              |

Quy ước package để dễ reuse và scale:

1. Feature root luôn nằm dưới `ui/<feature-name>/`.
2. Nếu có nhiều luồng con, tách thành sub-feature dưới `ui/<feature-name>/<sub-feature-name>/`.
3. Mỗi sub-feature nên self-contained: Fragment + ViewModel + Factory + UiState.
4. Không đặt class UI của feature con trực tiếp dưới `ui/` (tránh flat package khó bảo trì).

Quy ước đặt tên package:

| Loại package        | Mẫu                                            | Ví dụ                |
| ------------------- | ---------------------------------------------- | -------------------- |
| Feature root UI     | `ui.<feature>`                                 | `ui.auth`            |
| Sub-feature UI      | `ui.<feature>.<subFeature>`                    | `ui.auth.login`      |
| Domain              | `domain.<domain>`                              | `domain.user`        |
| Remote DTO request  | `data.datasource.remote.dto.request.<domain>`  | `...request.user`    |
| Remote DTO response | `data.datasource.remote.dto.response.<domain>` | `...response.user`   |
| Repository impl     | `data.repository`                              | `UserRepositoryImpl` |
| Mapper              | `data.mapper`                                  | `UserMapper`         |

### 3.1 Quy Ước DTO Remote (Đồng bộ với Backend)

DTO ở frontend phải bám chuẩn naming theo domain để mapping 1-1 với API contract của backend.

| Thành phần                    | Mẫu tên                      | Ví dụ                            |
| ----------------------------- | ---------------------------- | -------------------------------- |
| Request DTO                   | `<Verb><Domain>Request.java` | `LoginUserRequest.java`          |
| Response DTO (wrapper)        | `ApiResponse.java`           | `ApiResponse<LoginUserResponse>` |
| Response DTO (domain payload) | `<Domain>Response.java`      | `LoginUserResponse.java`         |

Quy tắc sử dụng:

1. DTO chỉ sống trong `data/datasource/remote/dto/`, không được leak vào `ui/` và `domain/`.
2. `repository/` chịu trách nhiệm map từ `ApiResponse<<Domain>Response>` sang model/domain object của frontend.
3. Mapping bắt buộc đi qua `data/mapper/` (vd: `UserMapper`) để cắt phụ thuộc của ViewModel với schema API.
4. Không parse thủ công JSON ngay tại `Activity`/`ViewModel`; mọi xử lý contract HTTP phải đi qua `remote/api` + `remote/dto`.

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
-> Repository dùng `Mapper` để đổi DTO (`ApiResponse<T>`) thành Domain Object trước khi callback về ViewModel
-> Kết quả trả về qua `DomainCallback<T>` (Hoặc fetch từ DB lên)
-> ViewModel bắn lệnh `.postValue(new UiState(loading=false, success...))`
-> Activity đang Observe `LiveData<UiState>` lập tức chạy lệnh Update UI tự động.
```

Lưu ý cho flow dạng Container + Sub-feature khi scale:

1. Activity/Fragment ở root chỉ đóng vai trò container điều hướng, không giữ business form của sub-feature.
2. Mỗi sub-feature phải có layout fragment độc lập; không `include` lại layout của activity legacy.
3. Khi refactor UI, ưu tiên sửa trong sub-feature layout thay vì sửa container.
4. Package bắt buộc theo chuẩn module hóa: `ui/<feature>/<sub-feature>/*`.

## 6. Các Ràng Buộc Kiến Trúc Cốt Lõi (Hard Constraints)

Đây là những luật thép bắt buộc phải tuân theo khi đóng góp code cho dự án WalkMate:

| Ràng buộc                                   | Trạng thái    | Yêu cầu Kỷ luật                                                                                                                                                                                               |
| ------------------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **View tự chọc API/Database?**              | ❌ NGHIÊM CẤM | View/Activity **tuyệt đối cấm** import các thư viện như `Retrofit` hay `Room`. Mọi xử lý Data phải đi vòng qua `ViewModel` để nhờ `Repository` xử lý hộ. Cầm View đi sửa DB là chém không tha.                |
| **Đẻ thêm class MVI (UiEvent / UiEffect)?** | ❌ CẤM DÙNG   | Ứng dụng vẽ bằng Java XML truyền thống, hãy dùng hàm trực tiếp. Việc áp dụng triết lý MVI đẻ ra quá nhiều class Action Event gây rác codebase và phí phạm thời gian.                                          |
| **Giải quyết Dependency Injection (DI)**    | ⚙️ BẮT BUỘC   | Khai sinh các cục Dependencies khổng lồ (`RoomDatabase`, `<Feature>Repository`) tại 1 instance độc tôn (Singleton) ở cấp `Application` class (Service Locator Pattern). Cấm cài cắm Hilt/Dagger vào hệ thống. |
| **Xử lý Thread/Async Mượt mà**              | ⚙️ BẮT BUỘC   | Luôn phải tạo luồng phụ `ExecutorService.execute()` khi insert mảng tọa độ vào Room hoặc call HTTP Network. Tỉ lệ rớt frame sẽ về 0. Cấm dùng các lib ngoài chuẩn như RxJava.                                 |
| **API Response Boundary rõ ràng?**          | ⚙️ BẮT BUỘC   | Response từ backend phải đi qua `ApiResponse<T>` ở tầng `data`. Chỉ dữ liệu đã map mới được đưa sang `domain/ui`. Không cho `ui/` phụ thuộc trực tiếp vào schema JSON trả về từ server.                       |
| **Repository phải chặt DTO boundary?**      | ⚙️ BẮT BUỘC   | Repository bắt buộc map DTO -> Domain bằng Mapper trước khi trả về `DomainCallback<T>`. Tuyệt đối không trả `ApiResponse`/DTO ra ngoài tầng `data`.                                                           |
| **Fragment được reuse layout Activity?**    | ❌ CẤM DÙNG   | Fragment không được `include` layout activity legacy. Mỗi fragment/sub-feature phải sở hữu layout riêng để tách biệt container và UI business.                                                                |

## 8. Custom View Standards (Mandatory)

### 8.1 Mục đích

Fragment và Adapter phải là "thin" — chỉ bind data và forward click. Mọi UI logic phức tạp (validation, animation, state toggling) phải được đóng gói trong **Custom View** nằm tại `core/designsystem/view/`.

### 8.2 Khi nào bắt buộc tạo Custom View

| Điều kiện                                                     | Quyết định       |
| ------------------------------------------------------------- | ---------------- |
| Pattern lặp lại **≥ 3 lần** trên các màn hình khác nhau      | ✅ Tạo Custom View |
| View chứa **state nội bộ** (visibility toggle, error display) | ✅ Tạo Custom View |
| Thay thế bằng XML `<include>` là đủ (không có logic)          | ⚠️ Dùng `<include>` |
| Render một lần, không tái sử dụng                             | ❌ Không cần tách |

### 8.3 Custom View vs. XML `<include>`

| Tiêu chí               | XML `<include>`                           | Custom View (`View` subclass)                         |
| ---------------------- | ----------------------------------------- | ----------------------------------------------------- |
| Logic nội bộ           | ❌ Không thể — Fragment phải tự xử lý    | ✅ Đóng gói hoàn toàn                                |
| Reuse API              | Phải bind lại mọi `findViewById` mỗi lần | `view.setError(msg)`, `view.getText()` — 1 dòng       |
| Thuộc tính XML custom  | ❌ Không hỗ trợ `declare-styleable`      | ✅ `app:wm_label`, `app:wm_hint`, `app:wm_icon`      |
| Tách biệt trách nhiệm  | Fragment vẫn biết về cấu trúc bên trong  | Fragment không biết gì về internals                   |
| Kiểm thử độc lập       | ❌ Phải test qua Fragment                | ✅ Unit-testable độc lập                              |

**Kết luận:** Dùng `<include>` cho pure-layout reuse (static, không logic). Dùng Custom View cho bất kỳ pattern nào có **state** hoặc **xử lý sự kiện nội bộ**.

### 8.4 Quy Ước Tạo Custom View

```text
core/designsystem/view/
└── <ComponentName>.java          ← Custom View class

res/layout/
└── view_<component_name>.xml     ← Layout dùng <merge> root

res/values/
└── attrs.xml                     ← declare-styleable cho tất cả Custom Views
```

**Quy tắc layout XML:**
1. Root phải là `<merge>` — tránh thêm ViewGroup thừa khi inflate vào parent.
2. Dùng `ConstraintLayout` phẳng bên trong nếu có nhiều hơn 2 view ngang hàng.
3. Tất cả dynamic views phải có `tools:text` / `tools:visibility` để preview đúng.

**Quy tắc Java class:**
1. Extend `LinearLayout` (vertical) cho compound view dạng stack (label + field + error).
2. Extend `ConstraintLayout` cho compound view dạng flat (avatar + text + badge).
3. Đọc attributes qua `TypedArray` trong `init()`, luôn gọi `a.recycle()` trong `finally`.
4. Public API chỉ expose những gì Fragment cần — không expose inner views.
5. Xử lý toggle/state nội bộ — Fragment không được viết logic liên quan đến internals.

### 8.5 Catalogue Custom Views Hiện Có

| Class                 | Package                  | Mô tả                                                    | Thuộc tính XML chính                                                   |
| --------------------- | ------------------------ | -------------------------------------------------------- | ---------------------------------------------------------------------- |
| `WalkMateInputField`  | `core.designsystem.view` | Label + EditText + password toggle + error line          | `wm_label`, `wm_hint`, `wm_icon`, `wm_inputType`, `wm_passwordToggle` |
| `WalkMateButton`      | `core.designsystem.view` | Orange pill button — FILLED/OUTLINED + loading state     | `wm_buttonStyle`, `wm_text`                                            |
| `AvatarInitialView`   | `core.designsystem.view` | Circular avatar: Glide photo → initials fallback + dot   | `wm_avatarName`, `wm_showOnlineStatus`, `wm_initialTextSize`           |
| `WalkMateStatColumn`  | `core.designsystem.view` | Vertical icon/emoji → value (bold) → label (muted)       | `wm_statIcon`, `wm_statEmoji`, `wm_statValue`, `wm_statLabel`, `wm_statValueSize` |
| `TagChipGroup`        | `core.designsystem.view` | ChipGroup subclass: `setTags(List<String>)` single call  | `wm_chipStyle` (display / selectable)                                  |
| `WalkMateCardHeader`  | `core.designsystem.view` | Flat ConstraintLayout header: emoji + title + chevron?   | `wm_headerEmoji`, `wm_headerTitle`, `wm_navigable`                     |
| `OtpInputView`        | `core.designsystem.view` | 6-digit OTP input: auto-focus-advance + backspace-to-prev | No XML attrs — fully programmatic. API: `getOtp()`, `clear()`, `setEnabled()` |
| `GlideHelper`         | `core.util`              | Utility: all Glide calls centralised (no layout file)    | N/A — static methods only                                              |

**Note:** `TagChipGroup`, `OtpInputView`, and `GlideHelper` are documented exceptions to the "view_name.xml layout required" rule:
- `TagChipGroup` extends `ChipGroup` directly — no extra ViewGroup layer needed.
- `OtpInputView` is fully programmatic — all 6 child `EditText` boxes are created in `init()`.
- `GlideHelper` is a static utility class, not a view.

*Cập nhật bảng này mỗi khi thêm Custom View mới.*

### 8.6 Checklist Trước Khi Tạo Custom View Mới

- [ ] Pattern xuất hiện ≥ 3 nơi hoặc chứa state nội bộ?
- [ ] Đã tạo `declare-styleable` trong `attrs.xml`?
- [ ] Layout dùng `<merge>` root?
- [ ] Class đọc attrs trong `finally { a.recycle(); }`?
- [ ] Public API không leak inner `View` references?
- [ ] Đã thêm vào bảng catalogue ở mục 8.5?

---

## 7. Checklist Reuse Khi Tạo Feature Mới

1. Tạo package theo chuẩn: `ui/<feature>/` và tách `ui/<feature>/<sub-feature>/` nếu có nhiều luồng UI.
2. Mỗi sub-feature phải có bộ tối thiểu: Fragment + ViewModel + UiState (Factory nếu cần dependency constructor).
3. API contract chỉ được định nghĩa tại `data/datasource/remote/dto/`.
4. Repository bắt buộc map DTO -> Domain qua `data/mapper/`, không trả DTO ra `ui/`.
5. Async nặng phải qua `ExecutorService`, UI chỉ observe `LiveData<UiState>`.
6. Nếu feature có tab/pager, adapter đặt ở feature root để điều phối sub-feature.
