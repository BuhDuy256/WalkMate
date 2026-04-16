# Phân tích các vấn đề (Bugs) trong ExploreViewModel & ExploreFragment

Tài liệu này ghi nhận lại các bug tiềm ẩn và lỗi logic đã được phát hiện trong quá trình review code của module Explore (`ExploreViewModel.java` và `ExploreFragment.java`). Những lỗi này tập trung ở việc quản lý State, Vòng đời (Lifecycle) và Luồng (Thread).

## 1. Lỗi Override (Ghi đè) State Khi Load Lại Dữ Liệu (Critical)
**Vị Trí:** `ExploreViewModel.java` — Hàm `loadHotspots()` (Dòng 88 - 105. Cụ thể tại **dòng 96**)
**Mô tả:** Khi gọi `onSuccess`, ViewModel tạo mới trạng thái bằng `new ExploreUiState(false, hotspots, null, AppState.WELCOME, null);` thay vì cập nhật trạng thái đã có.
**Tại sao bị bug:** Hành động tạo mới này mặc định ném toàn bộ trạng thái UI về `AppState.WELCOME` và đặt `selectedHotspot` thành null.
**Hệ quả:** Nếu quá trình tải/cập nhật dữ liệu từ Repository bị trigger lại ở background khi người dùng đang thao tác trong form tìm người (`SETUP`) hoặc đang chờ ghép đôi (`SCANNING`), toàn bộ tiến trình của họ sẽ bị đột ngột đóng lại và văng ra ngoài màn hình chính.
**Tư duy đúng đắn (Mindset):** Mọi thao tác cập nhật State qua ViewModel phải tuân theo nguyên tắc "Bất biến bằng cách sao chép" (Immutable Copy). Cần sử dụng pattern Builder hoặc hàm Copy (ví dụ: `current().withHotspots(hotspots)`) để phần thay đổi được dung hòa vào State hiện tại mà không làm hỏng các UI properties khác (như `AppState`).

**Self-insight:** Khi app tự động tải lại data (ví dụ: refresh thủ công hoặc update ngầm định kì) ở giữa một luồng thao tác của User, nếu ta tạo mới (`new`) một State hoàn toàn thay vì kế thừa, ta sẽ xóa trắng tiến độ mà User đang làm dở (mất check point).
**Cách fix:** Trong `onSuccess`, thay vì `new ExploreUiState(...)`, hãy dùng hàm copy kế thừa từ State hiện tại:
```java
// TRƯỚC (Bug)
public void onSuccess(List<Hotspot> hotspots) {
    post(new ExploreUiState(false, hotspots, null, AppState.WELCOME, null));
}

// SAU (Fix)
public void onSuccess(List<Hotspot> hotspots) {
    ExploreUiState s = current();
    post(new ExploreUiState(false, hotspots, s.getSelectedHotspot(), s.getAppState(), null));
}
```
> Thêm hàm `withHotspots()` vào `ExploreUiState` để gọn hơn:
> `post(current().withHotspots(hotspots).withLoading(false));`

## 2. Mất Dữ Liệu Khi Lọc (Filter) Hoặc Chọn (Select) Hotspot
**Vị Trí:** `ExploreViewModel.java` — Hàm `selectHotspot(String hotspotId)` (Dòng 112 - 124. Cụ thể tại **dòng 123**)
**Mô tả:** Trong hàm `selectHotspot()` hay các hàm khởi tạo tương tự bên trong `ExploreViewModel`, code thường truyền vào danh sách gốc (`s.getHotspots()`) và bỏ lơ danh sách đang được lọc (`filteredHotspots`).
**Tại sao bị bug:** Việc sử dụng Constructor `new ExploreUiState(...)` không truyền đủ tham số đã làm rơi rớt dữ liệu của tiến trình search.
**Hệ quả:** Nếu người dùng đang gõ tìm kiếm, danh sách kết quả đang được lọc, và họ chọn một điểm. Trạng thái sau đó bị thay thế, danh sách lọc biến mất, gây mất nhịp thao tác trong giao diện.
**Tư duy đúng đắn (Mindset):** Trạng thái hiển thị (`UiState`) là điểm duy nhất nói lên sự thật (Single source of truth). Bất kì thao tác nào trả về State mới đều phải bê nguyên vẹn mọi filter, cache và selection của luồng trước đó sang.
**Self-insight:** Trạng thái UI (`UiState`) là một cỗ xe chứa nhiều hành lý. Mọi hàm cập nhật muốn đổi xe mới thì đều phải dùng code thủ công vác nguyên xi các rương hành lý đang xài dở (search filter, selection cache) từ xe cũ sang. Nếu lười truyền sót tham số ở Constructor, người dùng sẽ bị tước đoạt công sức thao tác trước mắt.
**Cách fix:** Tại dòng 123, truyền thêm `s.getFilteredHotspots()` để giữ nguyên danh sách lọc:
```java
// TRƯỚC (Bug)
post(new ExploreUiState(false, s.getHotspots(), found, AppState.SETUP, null));

// SAU (Fix) — Thêm withFilteredHotspots để giữ nguyên filter đang dùng
post(new ExploreUiState(false, s.getHotspots(), found, AppState.SETUP, null)
        .withFilteredHotspots(s.getFilteredHotspots()));
```
> Áp dụng tương tự cho `closeSetup()`, `resetToWelcome()` và `consumeMatchFound()` — bất kỳ chỗ nào dùng `new ExploreUiState(...)` trực tiếp.

## 3. Rò Rỉ Logic Khi Process Bị Kill (Process Death)
**Vị Trí:** `ExploreViewModel.java` — Biến `openIntentId` (**Dòng 51**)
**Mô tả:** Biến theo dõi phiên mở WalkIntent `private String openIntentId = null;` được lưu lại dưới dạng một biến thể hiện (Instance Variable) nội bộ của Class `ExploreViewModel`.
**Tại sao bị bug:** OS Android có cơ chế thu hồi RAM của ứng dụng bằng cách đóng ngầm Process (System-initiated Process Death). Khi người dùng quay lại app, ViewModel bị tạo mới (recreated) và mọi Instance Variable đều bị reset (về `null`).
**Hệ quả:** Nếu người dùng đang `SCANNING`, hệ điều hành kill app, quá trình chờ vẫn đang chạy dưới backend. Khi FCM báo Push Message `MATCH_FOUND` về app, do ViewModel đã được recreate lại và `openIntentId == null`, biểu thức `event.intentId.equals(openIntentId)` sẽ trả về `false`. Event bị bỏ lỡ, và người dùng kẹt ở `SCANNING` mãi mãi.
**Tư duy đúng đắn (Mindset):** Với những ID quan trọng mang tính chất nắm giữ phiên hoạt động (session / transaction ID), cần phải được đưa vào `SavedStateHandle` để có thể sống sót sau Process Death.

**Self-insight:** Nghĩa là nếu app bị tắt ngầm để giải phóng RAM -> ViewModel bị recreate -> `openIntentId` bị reset bằng null. Khi FCM gửi Notification ghép đôi thành công về chậm, code kiểm tra khớp ID gặp giá trị `null` nên app ngó lơ luôn thông báo đó và kẹt mãi mãi.
**Cách fix:** Dùng `SavedStateHandle` để lưu `openIntentId` thay vì dùng plain instance variable:
```java
// 1. Inject SavedStateHandle vào constructor
private final SavedStateHandle savedState;
public ExploreViewModel(HotspotRepository hotspot,
                        WalkIntentRepository intent,
                        SavedStateHandle savedState) {
    this.savedState = savedState;
    ...
}

// 2. Thay thế get/set openIntentId
private String getOpenIntentId() {
    return savedState.get("openIntentId");
}
private void setOpenIntentId(String id) {
    savedState.set("openIntentId", id);
}

// 3. Cập nhật ViewModelFactory để inject SavedStateHandle
// (dùng AbstractSavedStateViewModelFactory hoặc Hilt)
```

## 4. Trash-State (Chưa Dọn Rác) Cho \`openIntentId\`
**Vị Trí:** `ExploreViewModel.java` — Hàm `appEventObserver` (**Dòng 62 - 72**)
**Mô tả:** Trong `appEventObserver`, dù đã nhận được sự kiện `MATCH_FOUND` và kết quả trả về khớp, biến `openIntentId` không hề được làm rỗng (`= null`).
**Tại sao bị bug:** Sau khi 1 Intent hoàn tất, ID cũ vẫn còn lưu trong ViewModel. Điều này duy trì dữ liệu rác dài hơn vòng đời của View.
**Hệ quả:** Do EventBus có thể vô tình báo lại, hoặc người dùng thao tác một luồng tìm kiếm mới bị lỗi/trễ nhịp, biến `openIntentId` chứa giá trị cũ sẽ làm sai lệch bộ máy nhận diện, gây nhầm lẫn trạng thái match cho các session tương lai.
**Tư duy đúng đắn (Mindset):** Giải phóng (Clean-up/Teardown): Khi một giao dịch xử lý kết thúc ở thành công, lỗi hay hủy, tất cả cờ (flags) và State phụ trợ phải được dọn dẹp sạch sẽ.
**Self-insight:** Bất kỳ "phiên/luồng" (Session) thao tác nào cũng xài 1 tấm vé Session ID để chạy. Luồng đóng lại, vé ID này tuyệt đối phải bị xé đi (`= null`). Nếu nằm đó lưu cữu, các sự kiện rác rưởi hoặc thao tác quét đợt 2 dễ dùng nhầm vé cũ, đánh lừa bộ máy kiểm duyệt và phá nát luồng State của app.
**Cách fix:** Thêm `openIntentId = null;` ngay sau khi `MATCH_FOUND` được xử lý trong `appEventObserver`:
```java
// TRƯỚC (Bug)
if (event.type == AppEvent.Type.MATCH_FOUND && event.intentId.equals(openIntentId)) {
    cancelScanningTimeout();
    post(current().withMatchFound(event.proposalId));
    AppEventBus.get().consumeEvent();
    // THIẾU: không xóa openIntentId!
}

// SAU (Fix)
if (event.type == AppEvent.Type.MATCH_FOUND && event.intentId.equals(openIntentId)) {
    cancelScanningTimeout();
    openIntentId = null;  // ← Dọn rác ngay khi match xong
    post(current().withMatchFound(event.proposalId));
    AppEventBus.get().consumeEvent();
}
```

## 5. Bỏ Rơi Executor Service (Nguy Cơ Gây Chặn UI)
**Vị Trí:** `ExploreViewModel.java` — Thuộc tính `executor` (**Dòng 46**)
**Mô tả:** `private final ExecutorService executor = Executors.newSingleThreadExecutor();` được tạo ra nhưng không hề có tác vụ nào `submit` hay `execute` vào đó.
**Tại sao bị bug:** Quá trình gọi `hotspotRepository.getHotspots()` đang phó mặc hoàn toàn cho cơ chế luồng bên trong Repository. 
**Hệ quả:** Nếu Repository đang sử dụng các lời gọi trực tiếp (Synchronous Calls), việc chặn luồng xử lý chính của Fragment (UI/Main Thread) sẽ xảy ra, gây giật lag hoặc dính lỗi ANR đỏ. Bản thân `executor` tạo ra mà không dùng cũng gây rác bộ nhớ (Memory Leak).
**Tư duy đúng đắn (Mindset):** UI Component hay ViewModel không quyết định Worker Thread cho Data Layer, mà hãy đưa Coroutine Dispatchers/Executors vào hạ tầng (Repository/Use Cases). Nếu ViewModel cần Off-main thread, nó phải phân công trực tiếp vào `executor`.
**Self-insight:** Nếu ứng dụng bày vẽ sinh ra Thread rảnh rỗi không sử dụng, trong khi API/DB bên dưới lại chạy đồng bộ thẳng đứng dồn cục (Synchronous), cục tạ data nặng nề trôi tuột sẽ đè sập Main Thread (luồng vẽ UI chỉ có 1). Kết quả là nhịp đập khung hình đứng hình, app bị khựng cứng và bị HĐH vả thông báo Crash (ANR).
**Cách fix:** Có 2 hướng tùy thuộc Repository đang implement thế nào:
- **Nếu Repository đang Async (callback-based):** Xóa bỏ `executor` đi vì nó thừa và gây rác.
- **Nếu Repository đang Synchronous:** Bọc lệnh gọi trong `executor.submit()`:
```java
// Xóa executor nếu Repository đã tự xử lý thread nội bộ (khuyên dùng)
// private final ExecutorService executor = ...; ← XÓA

// HOẶC: Dùng đúng nếu Repository chạy sync
public void loadHotspots() {
    post(current().withLoading(true));
    executor.submit(() -> {
        hotspotRepository.getHotspots(new DomainCallback<List<Hotspot>>() {
            @Override public void onSuccess(List<Hotspot> hotspots) {
                ExploreUiState s = current();
                post(new ExploreUiState(false, hotspots, s.getSelectedHotspot(), s.getAppState(), null));
            }
            @Override public void onError(Exception e) { /* ... */ }
        });
    });
}
```
**Self-insight:** Trong lập trình bất đồng bộ (Async), tuyệt đối không đoán thời gian của 2 luồng chạy song song. Timer (10s) và kết quả Backend có thể đến đích vào cùng một miligiây và cùng tàn phá UI. Nguyên tắc: Sự kiện nào cán đích trước thì code phải chủ động khóa/hủy (Cancel) sự kiện kia ngay lập tức.
**Cách fix:** `cancelScanningTimeout()` phải được gọi **trước** khi setState trong `appEventObserver`, và bên trong `timeoutRunnable` cần re-check xem state có còn là `SCANNING` không:
```java
// appEventObserver — hủy timeout TRƯỚC KHI update state
if (event.type == AppEvent.Type.MATCH_FOUND && event.intentId.equals(openIntentId)) {
    cancelScanningTimeout();          // ← Giết timer ngay
    openIntentId = null;
    post(current().withMatchFound(event.proposalId));
    AppEventBus.get().consumeEvent();
}

// timeoutRunnable — double-check AppState trước khi hiện dialog
private final Runnable timeoutRunnable = () -> {
    // Chỉ báo timeout nếu vẫn đang SCANNING (chưa match)
    if (current().getAppState() == AppState.SCANNING && openIntentId != null) {
        post(current().withScanTimedOut(true));
    }
};
```

## 7. Bug Lần Đầu Trống, Reset/Back Lại Nhận Dữ Tại Liệu (Constructor Data Loading)
**Vị Trí:** `ExploreViewModel.java` — Hàm `ExploreViewModel()` (**Dòng 79**) và `ExploreFragment.java`
**Mô tả:** Bạn gọi hàm `loadHotspots()` trực tiếp tại hàm khởi tạo (Constructor) của `ExploreViewModel`. Trong khi đó UI ở Fragment vẽ `Google Map` lại rất mất thời gian để `onMapReady()`.
**Tại sao bị bug:** Nếu Data được trả về nhanh hơn cả tốc độ Map sẵn sàng (hoặc Android Fragment kết nối đến LiveData), sự kiện đẩy (`postValue` / emit value) đầu tiên sẽ trượt do Fragment chưa observe thành công. Hơn nữa, Navigation làm Android View bị destroy nhưng ViewModel vẫn giữ Data (do scope Activity), khiến lần 2 bạn vào View bám vào LiveData ngậm dữ liệu của lần 1, Map Ready liền nhận được dữ liệu tải từ trước.
**Hệ quả:** Màn hình lần đầu khởi động sẽ trống không hoặc biểu thị Loading mãi, nhưng hễ chuyển tab ra vô lại là xài bình thường.
**Tư duy đúng đắn (Mindset):** Không nên nhúng logic Async Data fetching vào Constructor của ViewModel. Việc "Khi nào lấy Data?" là quyền của View Layer. Giải pháp đúng là đưa nó vào `Fragment.onViewCreated()`. Kèm theo check `if(viewModel.uiState.value.hotspots.isEmpty())` để tránh tải lại vô ích.
**Self-insight:** ViewModel lấy mồi qua Local DB/Cache chớp mắt là có dữ liệu (Siêu thanh). Google Map trên vỏ bọc UI lại tốn hàng giây để rục rịch gọi hàm Ready (Rùa bò). Bóp cò nã đạn Data từ ngay khi ViewModel vừa đẻ (Constructor) là Data vụt đi trong cơn mê sảng UI nhắm mắt chưa kịp đón. Màn hình tịt ngòi trống trơn. View sinh sinh mệnh tới đâu thì phải gọi kích nổ load dữ liệu tới đó (`onViewCreated`).
**Cách fix:** Xóa `loadHotspots()` khỏi Constructor ViewModel, chuyển sang Fragment `onViewCreated()`:
```java
// ExploreViewModel.java — TRƯỚC (Bug)
public ExploreViewModel(...) {
    ...
    loadHotspots(); // ← XÓA khỏi đây
}

// ExploreFragment.java — SAU (Fix)
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    setupViewModel();
    setupMap();
    ...
    // Chỉ load nếu chưa có data (tránh reload khi xoay màn hình)
    if (viewModel.getUiState().getValue().getHotspots().isEmpty()) {
        viewModel.loadHotspots();
    }
}
```
> Ngoài ra, trong `post()` của ViewModel, ưu tiên `setValue()` thay vì `postValue()` khi biết chắc đang ở Main Thread để tránh bị nuốt tick:
```java
private void post(ExploreUiState state) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        uiState.setValue(state);
    } else {
        uiState.postValue(state);
    }
}
```
