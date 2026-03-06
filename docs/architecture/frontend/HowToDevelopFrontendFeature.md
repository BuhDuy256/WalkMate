# How to Develop a Frontend Feature (Playbook)

**Platform:** Android Native (Java + MVVM-lite)  
**Target:** Developers adding new screens/features  
**Date:** March 6, 2026

---

## 1. Checklist (7 Bước)

### Bước 1: Xác định Screen + API

**Trả lời các câu hỏi:**

1. Screen nào? (Intent / Session / Rating / mới)
2. API endpoint nào? (GET/POST đâu)
3. Request/Response DTO như thế nào?
4. Domain model nào cần dùng? (WalkIntent / WalkSession / ...)

**Ví dụ:** "Activate Session"

- Screen: SessionActivity (existing)
- API: `POST /api/v1/sessions/{id}/activate`
- Response: `WalkSessionDto` (status = ACTIVE)
- Domain model: `WalkSession`

---

### Bước 2: Xác định UiState

**4 states cơ bản:**

1. **Idle** — Initial state, chưa load gì
2. **Loading** — Đang gọi API
3. **Content** — Có data (chứa domain model)
4. **Error** — Lỗi API/network (chứa message)

**Ví dụ:**

```java
public abstract class SessionUiState {
    public static class Idle extends SessionUiState {}
    public static class Loading extends SessionUiState {}
    public static class Content extends SessionUiState {
        public final WalkSession session;
        public Content(WalkSession session) { this.session = session; }
    }
    public static class Error extends SessionUiState {
        public final String message;
        public Error(String message) { this.message = message; }
    }
}
```

---

### Bước 3: Thêm API Method

**File:** `data/api/WalkMateApi.java`

```java
public interface WalkMateApi {

    @POST("api/v1/sessions/{id}/activate")
    Call<WalkSessionDto> activateSession(@Path("id") UUID sessionId);
}
```

---

### Bước 4: Thêm Repository Method

**File:** `data/repository/SessionRepository.java`

**Template:**

```java
public void [actionName]([params], Callback<Result<DomainModel>> callback) {
    api.[apiMethod]([params]).enqueue(new retrofit2.Callback<Dto>() {
        @Override
        public void onResponse(Call<Dto> call, Response<Dto> response) {
            if (response.isSuccessful() && response.body() != null) {
                DomainModel model = Mapper.toDomain(response.body());
                callback.onResult(new Result.Success<>(model));
            } else {
                ApiError error = parseError(response);
                callback.onResult(new Result.Error<>(error));
            }
        }

        @Override
        public void onFailure(Call<Dto> call, Throwable t) {
            callback.onResult(new Result.Error<>(new ApiError(0, "NETWORK_ERROR", t.getMessage())));
        }
    });
}
```

**Ví dụ thực tế:**

```java
public void activateSession(UUID sessionId, Callback<Result<WalkSession>> callback) {
    api.activateSession(sessionId).enqueue(new retrofit2.Callback<WalkSessionDto>() {
        @Override
        public void onResponse(Call<WalkSessionDto> call, Response<WalkSessionDto> response) {
            if (response.isSuccessful() && response.body() != null) {
                WalkSession session = SessionMapper.toDomain(response.body());
                callback.onResult(new Result.Success<>(session));
            } else {
                ApiError error = parseError(response);
                callback.onResult(new Result.Error<>(error));
            }
        }

        @Override
        public void onFailure(Call<WalkSessionDto> call, Throwable t) {
            callback.onResult(new Result.Error<>(new ApiError(0, "NETWORK_ERROR", t.getMessage())));
        }
    });
}
```

---

### Bước 5: Viết ViewModel Action

**File:** `ui/session/SessionViewModel.java`

**Template:**

```java
public void [actionName]([params]) {
    // 1. Guard: Check current state
    UiState current = uiState.getValue();
    if (!(current instanceof UiState.Content)) return;

    // 2. Validate (nếu cần)
    if ([validation fails]) {
        uiState.setValue(new UiState.Error("Validation message"));
        return;
    }

    // 3. Set Loading
    uiState.setValue(new UiState.Loading());

    // 4. Call Repository
    repository.[method]([params], result -> {
        if (result instanceof Result.Success) {
            uiState.setValue(new UiState.Content(result.data));
        } else {
            uiState.setValue(new UiState.Error(result.error.getMessage()));
        }
    });
}
```

**Ví dụ thực tế:**

```java
public class SessionViewModel extends ViewModel {

    private final SessionRepository repository;
    private MutableLiveData<SessionUiState> uiState = new MutableLiveData<>(new SessionUiState.Idle());

    public LiveData<SessionUiState> getUiState() {
        return uiState;
    }

    public void loadSession(UUID sessionId) {
        uiState.setValue(new SessionUiState.Loading());

        repository.getSession(sessionId, result -> {
            if (result instanceof Result.Success) {
                uiState.setValue(new SessionUiState.Content(result.data));
            } else {
                uiState.setValue(new SessionUiState.Error(result.error.getMessage()));
            }
        });
    }

    public void activateSession() {
        // Guard: check current state
        SessionUiState current = uiState.getValue();
        if (!(current instanceof SessionUiState.Content)) return;

        WalkSession session = ((SessionUiState.Content) current).session;

        // Guard: must be PENDING
        if (session.getStatus() != SessionStatus.PENDING) {
            uiState.setValue(new SessionUiState.Error("Chỉ có thể bắt đầu từ trạng thái Chờ"));
            return;
        }

        // Validate activation window
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = session.getScheduledStartTime().minusMinutes(15);
        LocalDateTime latest = session.getScheduledStartTime().plusMinutes(30);

        if (now.isBefore(earliest) || now.isAfter(latest)) {
            uiState.setValue(new SessionUiState.Error("Ngoài thời gian bắt đầu"));
            return;
        }

        // Set Loading
        uiState.setValue(new SessionUiState.Loading());

        // Call Repository
        repository.activateSession(session.getId(), result -> {
            if (result instanceof Result.Success) {
                uiState.setValue(new SessionUiState.Content(result.data));
            } else {
                uiState.setValue(new SessionUiState.Error(result.error.getMessage()));
            }
        });
    }
}
```

---

### Bước 6: Viết Activity (Observe + Render + Bind)

**File:** `ui/session/SessionActivity.java`

**Template:**

```java
public class [Screen]Activity extends AppCompatActivity {

    private Activity[Screen]Binding binding;
    private [Screen]ViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = Activity[Screen]Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get([Screen]ViewModel.class);

        // Observe UiState
        viewModel.getUiState().observe(this, this::render);

        // Bind user actions
        binding.btn[Action].setOnClickListener(v -> viewModel.[action]());

        // Load initial data
        viewModel.load[Data]([params]);
    }

    private void render([Screen]UiState state) {
        if (state instanceof [Screen]UiState.Idle) {
            // Do nothing
        } else if (state instanceof [Screen]UiState.Loading) {
            showLoading();
        } else if (state instanceof [Screen]UiState.Content) {
            showContent((([Screen]UiState.Content) state).data);
        } else if (state instanceof [Screen]UiState.Error) {
            showError((([Screen]UiState.Error) state).message);
        }
    }

    private void showLoading() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.contentContainer.setVisibility(View.GONE);
        binding.errorContainer.setVisibility(View.GONE);
    }

    private void showContent([DomainModel] data) {
        binding.progressBar.setVisibility(View.GONE);
        binding.contentContainer.setVisibility(View.VISIBLE);
        binding.errorContainer.setVisibility(View.GONE);

        // Render content
        binding.tv[Field].setText(data.get[Field]());
        // ...
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.contentContainer.setVisibility(View.GONE);
        binding.errorContainer.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }
}
```

**Ví dụ thực tế:**

```java
public class SessionActivity extends AppCompatActivity {

    private ActivitySessionBinding binding;
    private SessionViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(SessionViewModel.class);

        // Observe
        viewModel.getUiState().observe(this, this::render);

        // Bind actions
        binding.btnStartWalk.setOnClickListener(v -> viewModel.activateSession());
        binding.btnComplete.setOnClickListener(v -> viewModel.completeSession());
        binding.btnRetry.setOnClickListener(v -> viewModel.loadSession(sessionId));

        // Load initial
        UUID sessionId = UUID.fromString(getIntent().getStringExtra("SESSION_ID"));
        viewModel.loadSession(sessionId);
    }

    private void render(SessionUiState state) {
        if (state instanceof SessionUiState.Loading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.contentContainer.setVisibility(View.GONE);
        } else if (state instanceof SessionUiState.Content) {
            binding.progressBar.setVisibility(View.GONE);
            binding.contentContainer.setVisibility(View.VISIBLE);

            WalkSession session = ((SessionUiState.Content) state).session;
            renderSession(session);
        } else if (state instanceof SessionUiState.Error) {
            binding.progressBar.setVisibility(View.GONE);
            String message = ((SessionUiState.Error) state).message;
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> viewModel.loadSession(sessionId))
                .show();
        }
    }

    private void renderSession(WalkSession session) {
        binding.tvSessionId.setText(session.getId().toString());
        binding.tvScheduledTime.setText(formatTime(session.getScheduledStartTime()));

        // Render dựa trên backend status
        switch (session.getStatus()) {
            case PENDING:
                binding.btnStartWalk.setVisibility(View.VISIBLE);
                binding.btnComplete.setVisibility(View.GONE);
                break;
            case ACTIVE:
                binding.btnStartWalk.setVisibility(View.GONE);
                binding.btnComplete.setVisibility(View.VISIBLE);
                startLocationTracking();
                break;
            case COMPLETED:
                navigateToRating();
                break;
        }
    }
}
```

---

### Bước 7: Tests

**7.1 ViewModel Test (State Transitions)**

```java
@ExtendWith(MockitoExtension.class)
public class SessionViewModelTest {

    @Mock
    private SessionRepository repository;

    private SessionViewModel viewModel;

    @BeforeEach
    public void setUp() {
        viewModel = new SessionViewModel(repository);
    }

    @Test
    public void loadSession_success_shouldSetContentState() {
        // Given
        WalkSession session = createMockSession();
        doAnswer(invocation -> {
            Callback<Result<WalkSession>> callback = invocation.getArgument(1);
            callback.onResult(new Result.Success<>(session));
            return null;
        }).when(repository).getSession(any(), any());

        // When
        viewModel.loadSession(session.getId());

        // Then
        SessionUiState state = viewModel.getUiState().getValue();
        assertTrue(state instanceof SessionUiState.Content);
        assertEquals(session, ((SessionUiState.Content) state).session);
    }

    @Test
    public void activateSession_whenNotPending_shouldSetError() {
        // Given: session with ACTIVE status
        WalkSession session = createMockSession(SessionStatus.ACTIVE);
        viewModel.getUiState().setValue(new SessionUiState.Content(session));

        // When
        viewModel.activateSession();

        // Then
        SessionUiState state = viewModel.getUiState().getValue();
        assertTrue(state instanceof SessionUiState.Error);
    }
}
```

---

**7.2 Repository Test (Mapping)**

```java
@Test
public void activateSession_success_shouldMapToDomain() {
    // Given: mock API response
    WalkSessionDto dto = new WalkSessionDto();
    dto.setId("uuid");
    dto.setStatus("ACTIVE");

    Call<WalkSessionDto> call = mock(Call.class);
    when(api.activateSession(any())).thenReturn(call);

    // Simulate successful response
    doAnswer(invocation -> {
        retrofit2.Callback<WalkSessionDto> callback = invocation.getArgument(0);
        callback.onResponse(call, Response.success(dto));
        return null;
    }).when(call).enqueue(any());

    // When
    Result<WalkSession>[] result = new Result[1];
    repository.activateSession(UUID.randomUUID(), r -> result[0] = r);

    // Then
    assertTrue(result[0] instanceof Result.Success);
    WalkSession session = ((Result.Success<WalkSession>) result[0]).data;
    assertEquals(SessionStatus.ACTIVE, session.getStatus());
}
```

---

**7.3 UI Test (Render)**

```java
@RunWith(AndroidJUnit4.class)
public class SessionActivityTest {

    @Rule
    public ActivityScenarioRule<SessionActivity> scenarioRule =
        new ActivityScenarioRule<>(SessionActivity.class);

    @Test
    public void whenContentState_shouldShowSession() {
        // Given: ViewModel with Content state
        // (Inject mock ViewModel with Hilt/Dagger hoặc manual DI)

        // When: render triggered

        // Then: verify UI
        onView(withId(R.id.contentContainer)).check(matches(isDisplayed()));
        onView(withId(R.id.progressBar)).check(matches(not(isDisplayed())));
    }
}
```

---

## 2. Template Code

### 2.1 UiState (Sealed-ish Java Style)

```java
public abstract class [Feature]UiState {

    // Prevent direct instantiation
    private [Feature]UiState() {}

    public static class Idle extends [Feature]UiState {}

    public static class Loading extends [Feature]UiState {}

    public static class Content extends [Feature]UiState {
        public final [DomainModel] data;
        public Content([DomainModel] data) { this.data = data; }
    }

    public static class Error extends [Feature]UiState {
        public final String message;
        public Error(String message) { this.message = message; }
    }
}
```

---

### 2.2 ViewModel Method (Submit)

```java
public class [Feature]ViewModel extends ViewModel {

    private final [Feature]Repository repository;
    private MutableLiveData<[Feature]UiState> uiState = new MutableLiveData<>(new [Feature]UiState.Idle());

    public LiveData<[Feature]UiState> getUiState() {
        return uiState;
    }

    public void submit[Action]([params]) {
        // Validate
        if ([invalid]) {
            uiState.setValue(new [Feature]UiState.Error("Validation error"));
            return;
        }

        // Set Loading
        uiState.setValue(new [Feature]UiState.Loading());

        // Call Repository
        repository.[method]([params], result -> {
            if (result instanceof Result.Success) {
                uiState.setValue(new [Feature]UiState.Content(result.data));
            } else {
                uiState.setValue(new [Feature]UiState.Error(result.error.getMessage()));
            }
        });
    }
}
```

---

### 2.3 Repository Method (Wrapping API)

```java
public class [Feature]Repository {

    private final WalkMateApi api;

    public void [method]([params], Callback<Result<[DomainModel]>> callback) {
        api.[apiMethod]([params]).enqueue(new retrofit2.Callback<[Dto]>() {
            @Override
            public void onResponse(Call<[Dto]> call, Response<[Dto]> response) {
                if (response.isSuccessful() && response.body() != null) {
                    [DomainModel] model = [Mapper].toDomain(response.body());
                    callback.onResult(new Result.Success<>(model));
                } else {
                    ApiError error = parseError(response);
                    callback.onResult(new Result.Error<>(error));
                }
            }

            @Override
            public void onFailure(Call<[Dto]> call, Throwable t) {
                callback.onResult(new Result.Error<>(new ApiError(0, "NETWORK_ERROR", t.getMessage())));
            }
        });
    }

    private ApiError parseError(Response<?> response) {
        // Parse error body if needed
        return new ApiError(response.code(), "API_ERROR", "Request failed");
    }
}
```

---

### 2.4 Activity Render

```java
private void render([Feature]UiState state) {
    if (state instanceof [Feature]UiState.Idle) {
        // Initial state, do nothing
    } else if (state instanceof [Feature]UiState.Loading) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.contentContainer.setVisibility(View.GONE);
        binding.errorContainer.setVisibility(View.GONE);
    } else if (state instanceof [Feature]UiState.Content) {
        binding.progressBar.setVisibility(View.GONE);
        binding.contentContainer.setVisibility(View.VISIBLE);
        binding.errorContainer.setVisibility(View.GONE);

        [DomainModel] data = (([Feature]UiState.Content) state).data;
        render[Content](data);
    } else if (state instanceof [Feature]UiState.Error) {
        binding.progressBar.setVisibility(View.GONE);
        binding.contentContainer.setVisibility(View.GONE);
        binding.errorContainer.setVisibility(View.VISIBLE);

        String message = (([Feature]UiState.Error) state).message;
        binding.tvError.setText(message);
    }
}

private void render[Content]([DomainModel] data) {
    // Populate UI with data
    binding.tv[Field].setText(data.get[Field]());
    // ...
}
```

---

## 3. Anti-Patterns (5 Điều Tránh)

### ❌ Anti-Pattern 1: API Call trong Activity

```java
// BAD
public class SessionActivity extends AppCompatActivity {

    private void loadSession() {
        api.getSession(sessionId).enqueue(new Callback<WalkSessionDto>() {
            @Override
            public void onResponse(...) {
                // Handle response in Activity (WRONG!)
            }
        });
    }
}

// GOOD
public class SessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(...) {
        viewModel.getUiState().observe(this, this::render);
        viewModel.loadSession(sessionId); // ViewModel handles API
    }
}
```

**Tại sao sai:** Activity không survive rotation. API call bị cancel khi rotate.

---

### ❌ Anti-Pattern 2: Nhiều Boolean Flags

```java
// BAD
public class SessionViewModel extends ViewModel {
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> isError = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<WalkSession> session = new MutableLiveData<>();

    // Impossible states: isLoading=true && session!=null
}

// GOOD
public class SessionViewModel extends ViewModel {
    private MutableLiveData<SessionUiState> uiState = new MutableLiveData<>(new SessionUiState.Idle());

    // Single source of truth, no impossible states
}
```

---

### ❌ Anti-Pattern 3: Nhét Retry Lambda vào State

```java
// BAD
public static class Error extends UiState {
    public final String message;
    public final Runnable retryAction; // Lambda in state (WRONG!)

    public Error(String message, Runnable retry) {
        this.message = message;
        this.retryAction = retry;
    }
}

// Activity
binding.btnRetry.setOnClickListener(v -> state.retryAction.run());

// GOOD
public static class Error extends UiState {
    public final String message;
    public Error(String message) { this.message = message; }
}

// Activity
binding.btnRetry.setOnClickListener(v -> viewModel.loadSession(sessionId));
```

**Tại sao sai:** Lambda gây memory leak nếu capture Activity context. ViewModel nên tự nhớ action context.

---

### ❌ Anti-Pattern 4: Render Gây Navigation Loop

```java
// BAD
private void render(SessionUiState state) {
    if (state instanceof SessionUiState.Content) {
        WalkSession session = ((SessionUiState.Content) state).session;
        if (session.getStatus() == SessionStatus.COMPLETED) {
            navigateToRating(); // Gọi mỗi lần render → loop!
        }
    }
}

// GOOD (Option 1: Use flag)
private boolean hasNavigated = false;

private void render(SessionUiState state) {
    if (state instanceof SessionUiState.Content && !hasNavigated) {
        WalkSession session = ((SessionUiState.Content) state).session;
        if (session.getStatus() == SessionStatus.COMPLETED) {
            hasNavigated = true;
            navigateToRating();
        }
    }
}

// GOOD (Option 2: Use Event wrapper)
public class Event<T> {
    private T content;
    private boolean hasBeenHandled = false;

    public T getContentIfNotHandled() {
        if (hasBeenHandled) return null;
        hasBeenHandled = true;
        return content;
    }
}

// ViewModel
private MutableLiveData<Event<NavigationDestination>> navigationEvent = new MutableLiveData<>();

// Activity
viewModel.getNavigationEvent().observe(this, event -> {
    NavigationDestination dest = event.getContentIfNotHandled();
    if (dest != null) navigate(dest);
});
```

---

### ❌ Anti-Pattern 5: Không Có Loading State

```java
// BAD
public void activateSession() {
    repository.activateSession(id, result -> {
        // Không set Loading → user có thể double click
        if (result instanceof Result.Success) {
            uiState.setValue(new SessionUiState.Content(result.data));
        }
    });
}

// GOOD
public void activateSession() {
    // Guard
    if (uiState.getValue() instanceof SessionUiState.Loading) return; // Prevent double tap

    uiState.setValue(new SessionUiState.Loading()); // Disable button

    repository.activateSession(id, result -> {
        if (result instanceof Result.Success) {
            uiState.setValue(new SessionUiState.Content(result.data));
        } else {
            uiState.setValue(new SessionUiState.Error(result.error.getMessage()));
        }
    });
}
```

---

## 4. Test Strategy

| Test Type         | What to Test                         | Tool/Framework        | Location                         |
| ----------------- | ------------------------------------ | --------------------- | -------------------------------- |
| ViewModel Unit    | State transitions, validation        | JUnit + Mockito       | `test/` (unit test)              |
| Repository Unit   | DTO → Domain mapping, error handling | JUnit + Mockito       | `test/` (unit test)              |
| UI Instrumented   | Render Content/Error/Loading         | Espresso/Compose Test | `androidTest/` (device/emulator) |
| Integration (API) | Full flow (mock server)              | MockWebServer         | `androidTest/`                   |

### Test Checklist

**ViewModel Tests:**

- [ ] loadData() success → Content state
- [ ] loadData() failure → Error state
- [ ] submitAction() with invalid input → Error state
- [ ] submitAction() while Loading → ignored (guard)

**Repository Tests:**

- [ ] API success → Result.Success with mapped domain model
- [ ] API 4xx error → Result.Error with parsed message
- [ ] Network failure → Result.Error with "NETWORK_ERROR"

**UI Tests:**

- [ ] Content state → UI visible, data populated
- [ ] Loading state → Progress bar visible
- [ ] Error state → Error message visible, retry button works

---

## 5. Quick Reference

### Command Cheat Sheet

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Build debug APK
./gradlew assembleDebug

# Install debug APK
./gradlew installDebug
```

---

### File Creation Checklist

Khi thêm feature mới, tạo các file theo thứ tự:

1. `domain/model/[Model].java` — Domain model
2. `data/model/[Model]Dto.java` — DTO
3. `data/api/WalkMateApi.java` — Add method
4. `data/repository/[Feature]Repository.java` — Wrap API
5. `ui/[feature]/[Feature]UiState.java` — Define states
6. `ui/[feature]/[Feature]ViewModel.java` — Orchestrate
7. `ui/[feature]/[Feature]Activity.java` — Render
8. `res/layout/activity_[feature].xml` — Layout

---

## Summary

### 7 Bước Cơ Bản

1. **Identify** screen + API
2. **Define** UiState (Idle/Loading/Content/Error)
3. **Add** API method
4. **Add** Repository method (return Result)
5. **Write** ViewModel action (Loading → repo → Content/Error)
6. **Write** Activity (observe + render + bind)
7. **Test** (ViewModel → Repository → UI)

### Core Rules

- ✅ Activity chỉ observe + render
- ✅ ViewModel orchestrates workflow
- ✅ Repository wraps API
- ✅ UiState = single source of truth
- ✅ Backend status = domain truth (frontend chỉ hiển thị)

### Avoid These

- ❌ API trong Activity
- ❌ Nhiều boolean flags
- ❌ Lambda trong state
- ❌ Navigation loop trong render
- ❌ Không có Loading state

---

**END OF DOCUMENT**
