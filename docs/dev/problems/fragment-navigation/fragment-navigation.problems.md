# Phân tích các vấn đề (Bugs) trong Navigation & HomeFragment

Tài liệu này ghi nhận lại các vấn đề về hiệu năng (Performance) và bug logic đã được phát hiện trong quá trình review code của các file `MainActivity.java`, `HomeFragment.java`, `HomeViewModel.java` và `MatchesFragment.java`. Triệu chứng chính: **HomeFragment load chậm, navigate giữa các tab bị lag**.

---

## 1. HomeFragment Load Lại Toàn Bộ Data Mỗi Lần Vào Tab (Critical)
**Vị Trí:** `HomeFragment.java` — `onViewCreated()` (**Dòng 89**) và `HomeViewModel.java` — `loadDashboard()` (**Dòng 67-86**)
**Mô tả:** Mỗi lần `onViewCreated()` được gọi (tức là mỗi lần navigate vào HomeFragment), code bắt buộc gọi `viewModel.loadDashboard()` không có điều kiện kiểm tra nào. `loadDashboard()` luôn bắt đầu bằng cách post trạng thái Loading rồi gọi tuần tự 3 API: `getMyProfile()` → `getActiveSessions()` → `getNotifications()`.
**Tại sao bị bug:** ViewModel được scope theo Fragment (`new ViewModelProvider(this, ...)`). Điều này có nghĩa là mỗi lần Fragment bị destroy và recreate (chuyển tab, xoay màn hình, navigate đi rồi về), ViewModel bị **tạo mới hoàn toàn**. Fragment-scoped ViewModel không cache được gì; mọi lần vào Tab là mọi thứ bắt đầu lại từ đầu.
**Hệ quả:** Mỗi lần user tap vào tab Home, app phải chờ 3 API calls nối tiếp nhau. Trong khoảng thời gian đó, `renderState()` có check `if (state.isLoading()) return;` — nghĩa là màn hình **đứng yên trống** cho đến khi cả 3 APIs xong. Đây là nguyên nhân chính gây ra cảm giác "load chậm".
**Tư duy đúng đắn (Mindset):** ViewModel phải được scope theo Activity hoặc Navigation Graph (`ViewModelProvider(requireActivity(), ...)`), không phải scope theo Fragment. Một ViewModel sống lâu hơn Fragment sẽ cache được data giữa các lần navigate và tránh reload không cần thiết.
**Self-insight:** Fragment-scoped ViewModel = vừa vào cửa xong là quên hết. Mỗi lần mở cửa lại xếp hàng 3 cửa quầy API từ đầu. Activity-scoped ViewModel = nhớ mặt khách, lần sau vào khỏi xếp hàng lại.
**Cách fix:**
```java
// HomeFragment.java — TRƯỚC (Bug): Scope theo Fragment → VM bị recreate mỗi lần
viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);

// SAU (Fix): Scope theo Activity → VM sống xuyên suốt session
viewModel = new ViewModelProvider(requireActivity(), factory).get(HomeViewModel.class);

// Và trong onViewCreated, chỉ load khi chưa có data:
if (viewModel.getUiState().getValue() == null || viewModel.getUiState().getValue().isLoading()) {
    viewModel.loadDashboard();
}
```

---

## 2. Chuỗi API Tuần Tự (Waterfall) Thay Vì Song Song
**Vị Trí:** `HomeViewModel.java` — Luồng `loadDashboard()` → `loadSessions()` → `loadNotificationsAndPublish()` (**Dòng 67 → 106 → 125**)
**Mô tả:** Ba API calls được thiết kế thành chuỗi tuyến tính (Waterfall): chờ Profile xong → mới gọi Sessions → chờ Sessions xong → mới gọi Notifications. Tổng thời gian chờ = thời gian của API 1 + API 2 + API 3. Trong khi đó, `getMyProfile()` và `getActiveSessions()` **hoàn toàn độc lập nhau** về mặt data.
**Tại sao bị bug:** Không có lý do kỹ thuật để chờ Profile trước mới gọi Sessions. Code làm vậy chỉ để truyền `cachedGreetingName` sau khi gọi xong — nhưng cách này có thể giải quyết bằng biến trung gian hoặc counter mà không cần tuần tự hóa.
**Hệ quả:** Giả sử mỗi API tốn ~300ms. Chuỗi waterfall = ~900ms chờ. Nếu gọi song song = ~300ms. Người dùng cảm nhận gấp 3 lần bị chậm.
**Tư duy đúng đắn (Mindset):** Các API call độc lập phải chạy song song (Parallel). Chỉ chain tuần tự khi API sau **thực sự cần kết quả** của API trước.
**Self-insight:** Bắt người chờ lần lượt từng cửa quầy trong khi cả 3 cửa quầy đang rảnh là làm hỏng UX cho không.
**Cách fix:** Dùng counter để tracking khi nào cả 2 API đồng thời về xong:
```java
public void loadDashboard() {
    uiState.postValue(HomeDashboardUiState.loading());
    final UserProfile[] profileHolder = {null};
    final List<WalkSession>[] sessionsHolder = new List[]{null};
    final int[] doneCount = {0};

    Runnable checkAllDone = () -> {
        doneCount[0]++;
        if (doneCount[0] == 2) { // Cả 2 đã về
            loadNotificationsAndPublish(buildSessionSnapshot(sessionsHolder[0]));
        }
    };

    // Gọi song song — không chờ nhau
    profileRepo.getMyProfile(new DomainCallback<UserProfile>() {
        @Override public void onSuccess(UserProfile p) {
            cachedGreetingName = p.getFullName();
            checkAllDone.run();
        }
        @Override public void onError(Exception e) {
            cachedGreetingName = null;
            checkAllDone.run();
        }
    });

    sessionRepo.getActiveSessions(new DomainCallback<List<WalkSession>>() {
        @Override public void onSuccess(List<WalkSession> s) {
            sessionsHolder[0] = s;
            checkAllDone.run();
        }
        @Override public void onError(Exception e) { checkAllDone.run(); }
    });
}
```

---

## 3. `renderState()` Bị Block Hoàn Toàn Khi Loading — Không Hiển Thị Gì
**Vị Trí:** `HomeFragment.java` — `renderState()` (**Dòng 148-151**)
**Mô tả:** Khi `state.isLoading() == true`, hàm `renderState()` return ngay lập tức mà không render bất cứ gì — không skeleton, không spinner, không placeholder.
**Tại sao bị bug:** Người dùng vào tab Home thấy màn hình trắng/rỗng cho đến khi tất cả APIs về xong. Không có phản hồi visual nào để cho thấy app đang làm việc.
**Hệ quả:** Tạo ra cảm giác "app bị đơ" hoặc "load chậm" dù thực chất API đang chạy bình thường.
**Tư duy đúng đắn (Mindset):** Loading state không bao giờ là "không làm gì". Ít nhất phải hiển thị Skeleton Screen hoặc ProgressBar để user biết app đang hoạt động.
**Self-insight:** Màn hình trắng = user không biết app đang sống hay chết. Skeleton/spinner = user kiên nhẫn chờ vì thấy app đang "thở".
**Cách fix:**
```java
private void renderState(HomeDashboardUiState state) {
    // Hiển thị loading indicator thay vì trả về trống
    progressBar.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
    contentContainer.setVisibility(state.isLoading() ? View.GONE : View.VISIBLE);

    if (state.isLoading()) return; // Vẫn có thể return sớm sau khi đã set loading UI
    // ... render bình thường
}
```

---

## 4. ViewModel Khai Báo `ExecutorService` Nhưng Không Dùng
**Vị Trí:** `HomeViewModel.java` — **Dòng 36**
**Mô tả:** `private final ExecutorService executor = Executors.newSingleThreadExecutor();` được khai báo nhưng không có bất kỳ tác vụ nào được submit vào đó. Mọi API call (`getMyProfile`, `getActiveSessions`, `getNotifications`) đều chạy theo cơ chế thread của Repository tự quản.
**Tại sao bị bug:** Giống hệt bug tương tự đã phát hiện trong `ExploreViewModel`. Executor được tạo ra nhưng bỏ rơi — đây là dead code ẩn nguy cơ memory leak và che giấu vấn đề threading thực sự.
**Hệ quả:** Nếu bất kỳ Repository nào đang chạy Synchronous (không có thread riêng), Main Thread sẽ bị block. Executor bị bỏ hoang cũng chiếm tài nguyên thread pool không có lý do.
**Tư duy đúng đắn (Mindset):** Hoặc xóa executor nếu Repository đã tự xử lý thread, hoặc dùng nó đúng cách bằng cách submit công việc vào đó.
**Self-insight:** Khởi tạo nhân lực rồi cho ngồi chơi xơi nước, trong khi nhiệm vụ lại giao cho người khác làm — vừa tốn chi phí vừa vô trách nhiệm.
**Cách fix:**
```java
// Nếu Repository đã async (callback-based) → XÓA executor
// private final ExecutorService executor = ...; ← XÓA

// Nếu cần dùng → submit tác vụ vào đó
executor.submit(() -> profileRepo.getMyProfile(...));
```

---

## 5. `MatchesFragment` Load Data Mỗi Lần Navigate Vào Tab
**Vị Trí:** `MatchesFragment.java` — `onViewCreated()` (**Dòng 67**)
**Mô tả:** `matchesViewModel.loadAll()` được gọi mỗi lần `onViewCreated()` chạy — tức là mỗi lần user chuyển sang tab Matches. `MatchesViewModel` cũng được scope theo Fragment (`new ViewModelProvider(this, ...)`).
**Tại sao bị bug:** Tương tự Bug #1 của HomeFragment. Fragment-scoped ViewModel bị recreate mỗi lần, dẫn đến `loadAll()` gọi lại mọi API từ đầu mỗi lần switch tab.
**Hệ quả:** Chuyển qua lại giữa Home và Matches tab liên tục sẽ triggger reload data liên tục, gây hiện tượng nhấp nháy UI và lag navigation.
**Tư duy đúng đắn (Mindset):** ViewModel của các tab-level fragment nên được scope theo Activity hoặc Navigation Graph để tồn tại giữa các lần navigate.
**Self-insight:** Mỗi lần mở lại cửa hàng là phải lấy lại hàng hóa từ kho từ đầu. Đây là lãng phí rõ ràng khi hàng hóa thực ra vẫn còn nguyên ở đó.
**Cách fix:**
```java
// MatchesFragment.java — TRƯỚC (Bug)
matchesViewModel = new ViewModelProvider(
        this, new MatchesViewModelFactory(requireActivity().getApplication()))
        .get(MatchesViewModel.class);

// SAU (Fix): Scope theo Activity
matchesViewModel = new ViewModelProvider(
        requireActivity(), new MatchesViewModelFactory(requireActivity().getApplication()))
        .get(MatchesViewModel.class);

// Chỉ load khi chưa có data:
if (matchesViewModel.getUiState().getValue() == null) {
    matchesViewModel.loadAll();
}
```

---

## 6. `MainActivity` Double-Navigate Khi Tap Home Tab (Logic Bug)
**Vị Trí:** `MainActivity.java` — `bottomNav.setOnItemSelectedListener()` (**Dòng 62-69**)
**Mô tả:** Khi user tap tab Home, code thực hiện 2 hành động liên tiếp: (1) `NavigationUI.onNavDestinationSelected()` điều hướng đến `homeFragment`, rồi ngay sau đó (2) `navController.popBackStack(R.id.homeFragment, false)` pop back stack. Hai lệnh này chạy đồng bộ ngay nhau trên Main Thread.
**Tại sao bị bug:** `NavigationUI.onNavDestinationSelected()` kích hoạt animation navigate, trong khi `popBackStack()` được gọi ngay sau có thể gây xung đột: NavController đang xử lý navigate thì bị yêu cầu pop ngay lập tức — đây là race condition nhỏ trong NavController's transaction queue.
**Hệ quả:** Có thể gây hiệu ứng "nhấp nháy" fragment khi tap Home tab, hoặc animation bị cắt đứt giữa chừng.
**Tư duy đúng đắn (Mindset):** Không nên tương tác với NavController 2 lần liên tiếp đồng bộ. Nếu cần pop, hãy cấu hình `NavOptions` ngay trong lệnh navigate thay vì gọi pop riêng.
**Self-insight:** Ra lệnh tiến về phía trước và rút lui cùng lúc — người lính không biết phải làm gì.
**Cách fix:**
```java
// TRƯỚC (Bug): 2 lệnh NavController liên tiếp
bottomNav.setOnItemSelectedListener(item -> {
    boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
    if (item.getItemId() == R.id.homeFragment) {
        navController.popBackStack(R.id.homeFragment, false); // Xung đột
    }
    return handled;
});

// SAU (Fix): Dùng NavOptions để pop ngay trong navigate
bottomNav.setOnItemSelectedListener(item -> {
    if (item.getItemId() == R.id.homeFragment) {
        navController.navigate(R.id.homeFragment,
            null,
            new NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, true) // Inclusive pop
                .setLaunchSingleTop(true)
                .build());
        return true;
    }
    return NavigationUI.onNavDestinationSelected(item, navController);
});
```

---

## 7. Race Condition Trong `acceptProposal()` — Scroll Tới Tab Trước Khi Data Về (MatchesViewModel)
**Vị Trí:** `MatchesViewModel.java` — `acceptProposal()` (**Dòng 251-252**)
**Mô tả:** Sau khi backend xác nhận accept proposal thành công, code gọi `loadAll()` rồi **ngay lập tức** gọi tiếp `scrollToTabEvent.postValue(TAB_SESSION)`. Hai lệnh này chạy đồng bộ kế tiếp nhau.
**Tại sao bị bug:** `loadAll()` là bất đồng bộ — nó fire 3 API calls rồi return ngay mà không đợi. `scrollToTabEvent.postValue()` được emit ngay sau đó, khiến Fragment nhận tín hiệu "mở tab Session" **trước khi** 3 API của `loadAll()` kịp trả về data.
**Hệ quả:** User tap "Accept" → bị đưa sang tab Session nhưng thấy nó trống hoặc chưa cập nhật. Phải chờ thêm một chút mới thấy session mới xuất hiện — trải nghiệm bị gián đoạn.
**Tư duy đúng đắn (Mindset):** Tín hiệu điều hướng UI chỉ nên được emit **sau khi** data đã sẵn sàng, không phải ngay khi request bắt đầu.
**Self-insight:** Ra lệnh "mời khách vào phòng" trong khi phòng chưa được dọn xong — khách vào gặp ngay cảnh bừa bộn.
**Cách fix:** Refactor `loadAll()` để nhận một optional callback chạy sau khi cả 3 API đã về:
```java
public void acceptProposal(String proposalId) {
    proposalRepository.acceptProposal(proposalId, new DomainCallback<WalkSession>() {
        @Override
        public void onSuccess(WalkSession result) {
            loadAll(() -> {
                // Callback này chỉ chạy sau khi cả 3 API đã hoàn thành
                scrollToTabEvent.postValue(MatchesPagerAdapter.TAB_SESSION);
            });
        }
        // ...
    });
}

// loadAll() refactor để nhận Runnable onComplete
public void loadAll(Runnable onComplete) {
    Runnable onOneDone = () -> {
        if (pending.decrementAndGet() == 0) {
            uiState.postValue(...);
            if (onComplete != null) onComplete.run(); // ← Gọi sau khi post state
        }
    };
}
```

---

## 8. `cancelProposal()` và `cancelSession()` Reload Thừa API (MatchesViewModel)
**Vị Trí:** `MatchesViewModel.java` — `cancelProposal()` (**Dòng 225**) và `cancelSession()` (**Dòng 282**)
**Mô tả:** Cả hai action đều gọi `loadAll()` sau thành công — tức là reload cả 3 API dù mỗi action chỉ ảnh hưởng đến 2 trong 3 danh sách. `cancelProposal()` không ảnh hưởng Sessions; `cancelSession()` không ảnh hưởng Proposals.
**Tại sao bị bug:** Không có lý do kỹ thuật để reload API không liên quan. Đây là reload "cho chắc" nhưng tốn network không cần thiết.
**Hệ quả:** Mỗi lần user cancel, app tốn 3 network request thay vì 2. Dài hơn mức cần thiết, nhất là khi mạng yếu.
**Tư duy đúng đắn (Mindset):** Reload đúng phạm vi bị ảnh hưởng — không reload thứ mình biết chắc không thay đổi.
**Self-insight:** Gọi vệ sinh toàn bộ tòa nhà chỉ vì một phòng bị bẩn — đúng về kết quả nhưng sai về chi phí.
**Cách fix:**
```java
// cancelProposal → chỉ reload Intents + Proposals
private void reloadIntentsAndProposals() { /* 2 API song song */ }

// cancelSession → chỉ reload Sessions + Intents
private void reloadSessionsAndIntents() { /* 2 API song song */ }
```

---

## 9. `firstError` Chỉ Lưu Lỗi Đầu Tiên — Các Lỗi Sau Bị Im Lặng (MatchesViewModel)
**Vị Trí:** `MatchesViewModel.java` — `loadAll()` (**Dòng 87, 106, 117, 128**)
**Mô tả:** `AtomicReference<String> firstError` dùng `compareAndSet(null, error.getMessage())` chỉ ghi nhận lỗi API đầu tiên. Nếu cả 2 hoặc 3 API đều thất bại, các lỗi sau bị nuốt hoàn toàn.
**Tại sao bị bug:** Khi mất kết nối mạng, cả 3 API đều lỗi nhưng user và developer chỉ thấy lỗi của API đầu tiên. Thông tin debug bị che khuất.
**Hệ quả:** Gây khó khăn khi debug production. User nhận thông báo lỗi không đầy đủ. Đặc biệt nguy hiểm khi lỗi thứ 2 có message quan trọng hơn lỗi thứ nhất.
**Tư duy đúng đắn (Mindset):** Aggregate (gom) nhiều lỗi lại hoặc ít nhất log tất cả, không nên im lặng bỏ qua lỗi thứ cấp.
**Self-insight:** Chứng kiến 3 tai nạn xảy ra cùng lúc mà chỉ báo cáo tai nạn đầu tiên — 2 tai nạn còn lại không ai biết để xử lý.
**Cách fix:**
```java
// Dùng List để gom tất cả lỗi
AtomicReference<List<String>> errors = new AtomicReference<>(new ArrayList<>());

// Trong mỗi onError:
synchronized (errors.get()) { errors.get().add(error.getMessage()); }
onOneDone.run();

// Khi publish:
List<String> errorList = errors.get();
String errorMessage = errorList.isEmpty() ? null : String.join("; ", errorList);
uiState.postValue(new MatchesUiState(false, ..., errorMessage));
```
