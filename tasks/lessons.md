# Lessons
<!-- Claude appends a new entry here after every user correction. -->
<!-- Format: ## YYYY-MM-DD – [short pattern name] -->
<!-- What went wrong, what the correct pattern is, rule to prevent recurrence. -->

---

## 2026-03-31 – Domain layer must never import android.util.Log

**What went wrong:**
`SessionTrackingService.java` imports `android.util.Log` (line 3) despite its own Javadoc explicitly stating *"This class has NO Android framework imports — it is a pure Java domain service."* The class violated its own contract.

**Correct pattern:**
Domain classes are pure Java. If logging is needed, inject a `DomainLogger` interface via the constructor and provide the Android implementation at the infrastructure layer:
```java
// domain/shared/DomainLogger.java
public interface DomainLogger {
    void debug(String tag, String message);
    void error(String tag, String message);
}

// data/ or service/ layer provides:
public class AndroidDomainLogger implements DomainLogger {
    @Override public void debug(String tag, String msg) { Log.d(tag, msg); }
    @Override public void error(String tag, String msg) { Log.e(tag, msg); }
}
```
Alternatively, remove the Log calls entirely — the repository and service layer already log what matters.

**Rule to prevent recurrence:**
Before writing any class inside `domain/`, run the import check: zero `android.*`, zero `androidx.*` (except the documented `LiveData` exception on Repository interfaces). If `android.util.Log` is needed for debugging, use `System.out.println` temporarily or inject `DomainLogger`.

---

## 2026-03-31 – LiveData in a domain Repository interface is an intentional exception

**What went wrong:**
`TrackingRepository.java` imports `androidx.lifecycle.LiveData`. This looks like a domain-layer purity violation.

**Correct pattern:**
This is a **documented, intentional exception**. In a pure-Java, non-Coroutine, non-RxJava project, `LiveData<T>` is the only viable reactive return type for Room-backed observable reads. It is accepted on Repository interfaces only for reactive read methods (e.g., `LiveData<List<RoutePoint>> getPointsForSession(String sessionId)`). All other methods must use `DomainCallback<T>`.

**Rule to prevent recurrence:**
Never expand this exception. Only `LiveData<T>` return types on Repository interfaces are permitted. Do not add any other `androidx.*` import to any domain class. Document any new exception in `tasks/lesson.md` Section 10.

---

## 2026-03-31 – UiState inner snapshot classes: public final vs private final + getters

**What went wrong:**
`HomeDashboardUiState` outer class uses `private final` fields + getters (standard Java bean pattern), but its inner classes `UpcomingSessionSnapshot` and `QuickInviteUser` use `public final` fields directly. This creates an internal style inconsistency.

**Correct pattern:**
Both are immutable (final fields cannot be reassigned), so neither is a correctness error. The decision is a style convention:
- **Inner snapshot classes** (simple, read-only data containers used only inside UiState): `public final` fields are acceptable for brevity.
- **Outer UiState class**: always `private final` + getters.

**Rule to prevent recurrence:**
When generating new UiState classes, apply this rule explicitly:
- Outer UiState: `private final` + getters only.
- Static inner snapshot/data classes: `public final` fields are permitted. Document this is intentional, not an oversight.

---

## 2026-03-31 – No RxJava, no Coroutines — ever

**What was identified:**
The architecture mandates `LiveData` + `ExecutorService` + `DomainCallback<T>` as the only async mechanism. Any temptation to introduce `Observable`, `Single`, `Flow`, `suspend fun`, `CoroutineScope`, or `Channel` is a critical violation.

**Correct pattern:**
```java
// ✅ Correct — background work
executor.execute(() -> {
    repository.doWork(param, new DomainCallback<Result>() {
        @Override public void onSuccess(Result r) { uiState.postValue(build(r)); }
        @Override public void onError(Exception e) { uiState.postValue(buildError(e)); }
    });
});

// ✅ Correct — recurring background work
timerFuture = timerExecutor.scheduleAtFixedRate(
    () -> elapsedSecondsLiveData.postValue(computeElapsed()),
    0L, 1L, TimeUnit.SECONDS);
```

**Rule to prevent recurrence:**
When generating any ViewModel or RepositoryImpl, import check: zero `io.reactivex.*`, zero `kotlinx.coroutines.*`, zero `kotlin.coroutines.*`, zero `Flow`, zero `StateFlow`, zero `SharedFlow`.

---

## 2026-03-31 – ViewModel instantiation: always use a Factory

**What was identified:**
Every ViewModel that takes non-default constructor arguments must have a paired `*ViewModelFactory`. The default no-arg `ViewModelProvider` factory cannot satisfy constructor parameters.

**Correct pattern:**
```java
// In Fragment.onViewCreated() or Activity.onCreate():
WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
HomeViewModelFactory factory = new HomeViewModelFactory(
        app.getWalkSessionRepository(),
        app.getUserRepository());
viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
```

**Rule to prevent recurrence:**
Never call `new ViewModelProvider(this).get(XxxViewModel.class)` when the ViewModel has constructor parameters. Always generate a paired `XxxViewModelFactory` for every new ViewModel.

---

## 2026-03-31 – renderState() is the ONLY place Views are mutated

**What was identified:**
The passive-view pattern requires that all View mutations happen in a single `renderState(XxxUiState state)` method called by the LiveData observer. No other method in the Fragment or Activity should call `setText()`, `setVisibility()`, etc.

**Correct pattern:**
```java
// Wire-up:
viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

// Single renderer — all view writes here:
private void renderState(HomeDashboardUiState state) {
    if (state.isLoading()) return;
    txtGreeting.setText(getString(R.string.home_greeting_format, state.getGreetingName()));
    cardUpcomingSession.setVisibility(state.getUpcomingSession() != null ? View.VISIBLE : View.GONE);
    // ...
}
```

**Rule to prevent recurrence:**
When generating a new Fragment or Activity, insert a single `renderState()` stub and annotate it with `// ONLY place that writes to Views`. Never add view mutation logic to click listeners, `onResume()`, or any other lifecycle method.

---

## 2026-03-31 – Fragment observation must use getViewLifecycleOwner()

**What was identified:**
In a Fragment, LiveData must be observed with `getViewLifecycleOwner()`, not `this`. Using `this` causes the observer to survive View destruction during back-stack operations, leading to crashes when the observer fires on a null View.

**Correct pattern:**
```java
// ✅ Correct — in Fragment
viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

// ❌ Wrong — leaks observer across view recreation
viewModel.getUiState().observe(this, this::renderState);
```

**Rule to prevent recurrence:**
In any generated Fragment, always use `getViewLifecycleOwner()`. In Activity, `this` is correct (Activity and View lifecycle are the same). Flag any deviation immediately.

---

## 2026-03-31 – Navigation via OnXxxActionListener — Fragment never casts getActivity()

**What was identified:**
The `HomeFragment` correctly uses an `OnHomeActionListener` interface for navigation instead of casting `getActivity()` to a concrete Activity type. The listener is nulled in `onDetach()` to prevent the Activity from leaking.

**Correct pattern:**
```java
// Declare inside Fragment:
public interface OnHomeActionListener {
    void switchToExplore();
}

@Override
public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (!(context instanceof OnHomeActionListener)) {
        throw new IllegalStateException("Host must implement OnHomeActionListener");
    }
    listener = (OnHomeActionListener) context;
}

@Override
public void onDetach() {
    super.onDetach();
    listener = null; // prevent Activity memory leak
}
```

**Rule to prevent recurrence:**
When generating any Fragment that needs to trigger navigation, always create an `OnXxxActionListener` interface inside the Fragment. Never generate `((MainActivity) getActivity()).someMethod()` — that is a direct coupling violation.

---

## 2026-03-31 – postValue() vs setValue() threading rule

**What was identified:**
`postValue()` is thread-safe and must be used from background threads (executor callbacks, timer ticks). `setValue()` must only be called from the main thread (inside `MediatorLiveData.addSource()` lambdas, which run on main).

**Correct pattern:**
```java
// ✅ Background thread (inside executor callback):
uiState.postValue(new XxxUiState(...));

// ✅ Main thread (inside MediatorLiveData source lambda):
uiStateLiveData.addSource(sourceA, value -> {
    uiStateLiveData.setValue(new XxxUiState(...)); // addSource runs on main
});

// ❌ Wrong — setValue() from background thread:
executor.execute(() -> uiState.setValue(...)); // will throw IllegalStateException
```

**Rule to prevent recurrence:**
Default to `postValue()` inside any executor lambda. Only use `setValue()` when 100% certain the call is on the main thread (i.e., inside a `MediatorLiveData.addSource()` lambda or a direct user-click handler).

---

## 2026-03-31 – ExecutorService must be shut down in onCleared()

**What was identified:**
Every `ExecutorService` and `ScheduledExecutorService` created in a ViewModel must be shut down in `onCleared()`. Failing to do so leaks background threads after the ViewModel is destroyed (e.g., on back-stack pop or process lifecycle changes).

**Correct pattern:**
```java
@Override
protected void onCleared() {
    super.onCleared();
    stopTimer();           // cancel scheduled future first
    timerExecutor.shutdown(); // then release the thread pool
    executor.shutdown();
}
```

**Rule to prevent recurrence:**
When generating a ViewModel, always include `onCleared()` that shuts down every executor created as a member field. The `SessionTrackingService` domain service also holds an executor — its owner (`WalkTrackerService`) must call `sessionTrackingService.stopTracking()` in `onDestroy()`.

---

## 2026-03-31 – Data layer mapping rule: DTO/Entity never leaves data/

**What was identified:**
DTOs (`data/datasource/remote/dto/`) and Room Entities (`data/datasource/local/entity/`) must never be imported in `ui/` or `domain/`. Mappers in `data/mapper/` convert them to domain models before they cross the boundary.

**Correct pattern:**
```
DTO/Entity  →  data/mapper/XxxMapper.java  →  domain/xxx/Xxx.java  →  ViewModel  →  UiState
```

```java
// ✅ Correct — inside RepositoryImpl:
List<RoutePointEntity> entities = dao.getAll();
callback.onSuccess(RoutePointMapper.toDomainList(entities)); // crosses boundary as domain model

// ❌ Wrong — in ViewModel:
import com.walkmate.data.datasource.local.entity.RoutePointEntity; // NEVER
```

**Rule to prevent recurrence:**
When generating a new RepositoryImpl, always pass domain objects to callbacks, never entities or DTOs. When generating a ViewModel, its import list must contain zero `data.datasource.*` imports.
