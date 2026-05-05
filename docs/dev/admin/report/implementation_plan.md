# Implementation Plan: Admin Page — XML + Java

**Date:** 2026-05-05  
**Depends on:** [gap_analysis.md](./gap_analysis.md)  
**Architecture refs:** [Frontend_VI.md](../../single-source-of-truth/architecture/Frontend_VI.md), [Backend_VI.md](../../single-source-of-truth/architecture/Backend_VI.md)

---

## Phase 1 — Backend: Add User Names to AdminReportResponse (G-3)

> Resolves Gap G-3. Without this, Reports List/Detail show UUIDs instead of names.

### 1.1 Modify `AdminReportResponse.java`

**File:** `backend/.../presentation/dto/response/report/AdminReportResponse.java`

Add two fields after `reportedUserId`:

```java
public record AdminReportResponse(
    String reportId,
    String sessionId,
    String reporterId,
    String reporterName,        // ← NEW
    String reportedUserId,
    String reportedUserName,    // ← NEW
    String reason,
    String evidenceUrl,
    String status,
    int appliedTrustDelta,
    String createdAt,
    String resolvedBy,
    String resolvedAt,
    String resolutionNote
) {}
```

### 1.2 Modify `AdminReportController.toAdminResponse()`

**File:** `backend/.../presentation/controller/report/AdminReportController.java`

**Strategy:** Inject `UserProfileQueryService` (or `UserProfileRepository` — whichever already provides `findById` returning `UserProfile`). Batch-load profiles for reporter + reported user. Same pattern as the leaderboard feature.

**Changes:**

1. Add injected dependency: `UserProfileQueryService` (or the profile repository that returns `UserProfile` with `getFullName()`).
2. For list endpoints: collect all unique reporter/reported UUIDs, batch-load profiles into a `Map<UUID, String>` (userId → fullName).
3. Update `toAdminResponse()` signature to accept the name map.
4. Pass names into the new `AdminReportResponse` constructor fields.

**Key concern:** The `toAdminResponse` is a private helper on the controller. It currently takes only `SessionReport`. Update it to also accept `String reporterName, String reportedUserName` or the name map.

### 1.3 Verify endpoint contract

After this change, the JSON shape for `GET /api/v1/admin/reports` becomes:

```json
{
  "success": true,
  "data": [{
    "reportId": "uuid",
    "sessionId": "uuid",
    "reporterId": "uuid",
    "reporterName": "Tran Thi Bich",
    "reportedUserId": "uuid",
    "reportedUserName": "Nguyen Van An",
    "reason": "SAFETY_CONCERN",
    "evidenceUrl": "https://...",
    "status": "OPEN",
    "appliedTrustDelta": -30,
    "createdAt": "2025-05-02T09:23:00",
    "resolvedBy": null,
    "resolvedAt": null,
    "resolutionNote": null
  }]
}
```

---

## Phase 2 — Frontend: SessionManager Role Extraction (G-1)

**File:** `frontend/.../data/datasource/remote/api/SessionManager.java`

Add one method:

```java
/**
 * Extracts the user role from the JWT "role" claim.
 * Returns "USER" if no token or claim is missing.
 */
public String getUserRole() {
    String token = getAccessToken();
    if (token == null) return "USER";
    try {
        JSONObject json = decodePayload(token);
        return json.optString("role", "USER");
    } catch (Exception e) {
        Log.w("SessionManager", "Failed to decode JWT role claim", e);
        return "USER";
    }
}

public boolean isAdmin() {
    return "ADMIN".equals(getUserRole());
}
```

`decodePayload()` already exists as a private method. No new dependencies.

---

## Phase 3 — Frontend Data Layer (G-4)

### 3.1 Admin Report Response DTO

**File:** `frontend/.../data/datasource/remote/dto/response/report/AdminReportResponse.java`  
**Action:** Create new

```java
package com.walkmate.data.datasource.remote.dto.response.report;

import com.google.gson.annotations.SerializedName;

public class AdminReportResponse {
    @SerializedName("reportId")         public String reportId;
    @SerializedName("sessionId")        public String sessionId;
    @SerializedName("reporterId")       public String reporterId;
    @SerializedName("reporterName")     public String reporterName;
    @SerializedName("reportedUserId")   public String reportedUserId;
    @SerializedName("reportedUserName") public String reportedUserName;
    @SerializedName("reason")           public String reason;
    @SerializedName("evidenceUrl")      public String evidenceUrl;
    @SerializedName("status")           public String status;
    @SerializedName("appliedTrustDelta")public int appliedTrustDelta;
    @SerializedName("createdAt")        public String createdAt;
    @SerializedName("resolvedBy")       public String resolvedBy;
    @SerializedName("resolvedAt")       public String resolvedAt;
    @SerializedName("resolutionNote")   public String resolutionNote;
}
```

### 3.2 Resolve Report Request DTO

**File:** `frontend/.../data/datasource/remote/dto/request/report/ResolveReportRequest.java`  
**Action:** Create new

```java
package com.walkmate.data.datasource.remote.dto.request.report;

public class ResolveReportRequest {
    private final String resolution;
    private final String resolutionNote;

    public ResolveReportRequest(String resolution, String resolutionNote) {
        this.resolution = resolution;
        this.resolutionNote = resolutionNote;
    }
}
```

### 3.3 Admin Report API Service (Retrofit)

**File:** `frontend/.../data/datasource/remote/api/AdminReportApiService.java`  
**Action:** Create new

```java
package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.report.ResolveReportRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.report.AdminReportResponse;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface AdminReportApiService {

    @GET("api/v1/admin/reports")
    Call<ApiResponse<List<AdminReportResponse>>> getReports(
            @Query("status") String status);

    @GET("api/v1/admin/reports/{reportId}")
    Call<ApiResponse<AdminReportResponse>> getReport(
            @Path("reportId") String reportId);

    @PATCH("api/v1/admin/reports/{reportId}/resolve")
    Call<ApiResponse<AdminReportResponse>> resolveReport(
            @Path("reportId") String reportId,
            @Body ResolveReportRequest body);
}
```

### 3.4 Domain Model

**File:** `frontend/.../domain/report/AdminReport.java`  
**Action:** Create new

Lightweight immutable model used by UI layer. Maps `OPEN` → `PENDING` at this boundary.

```java
package com.walkmate.domain.report;

public class AdminReport {
    public enum Status { PENDING, APPROVED, REJECTED }
    public enum Reason { SAFETY_CONCERN, MISCONDUCT, EMERGENCY, OTHER }

    private final String reportId;
    private final String sessionId;
    private final String reporterId;
    private final String reporterName;
    private final String reportedUserId;
    private final String reportedUserName;
    private final Reason reason;
    private final String evidenceUrl;
    private final Status status;
    private final int appliedTrustDelta;
    private final String createdAt;
    private final String resolvedBy;
    private final String resolvedAt;
    private final String resolutionNote;

    // Constructor with all fields
    // Getters only — no setters (immutable)
}
```

### 3.5 Domain Repository Interface

**File:** `frontend/.../domain/report/AdminReportRepository.java`  
**Action:** Create new

```java
package com.walkmate.domain.report;

import com.walkmate.domain.shared.DomainCallback;
import java.util.List;

public interface AdminReportRepository {
    void getAllReports(DomainCallback<List<AdminReport>> callback);
    void getReportsByStatus(String status, DomainCallback<List<AdminReport>> callback);
    void getReportById(String reportId, DomainCallback<AdminReport> callback);
    void resolveReport(String reportId, String resolution, String note,
                       DomainCallback<AdminReport> callback);
}
```

### 3.6 Mapper

**File:** `frontend/.../data/mapper/AdminReportMapper.java`  
**Action:** Create new

Maps `AdminReportResponse` (DTO) → `AdminReport` (domain). Handles:
- `status`: `"OPEN"` → `Status.PENDING`, `"APPROVED"` → `Status.APPROVED`, `"REJECTED"` → `Status.REJECTED`
- `reason`: `"SAFETY_CONCERN"` → `Reason.SAFETY_CONCERN`, etc.

### 3.7 Repository Implementation

**File:** `frontend/.../data/repository/AdminReportRepositoryImpl.java`  
**Action:** Create new

Follows standard pattern (see `ReviewRepositoryImpl`):
- Injected: `AdminReportApiService`, `AdminReportMapper`, `ExecutorService`
- Each method enqueues Retrofit call, maps response via mapper, delivers result through `DomainCallback`

### 3.8 Register in `WalkMateApplication`

Add singleton `AdminReportApiService` (via `ApiClient.getRetrofit().create(...)`) and `AdminReportRepositoryImpl` to the Application class. Expose via `getAdminReportRepository()`.

---

## Phase 4 — Frontend UI: Profile Admin Card (G-5, part 1)

Modifies the existing Profile screen to show the Admin Dashboard card for admin users.

### 4.1 Modify `fragment_profile.xml`

**Insert** the Admin Dashboard card between `cardMilestones` and `cardMenu`. Initially `visibility="gone"`. Structure:

```xml
<!-- ── ADMIN DASHBOARD CARD ── -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardAdminDashboard"
    android:visibility="gone"
    ... (dark gradient bg #0F172A, 20dp corners, orange border) >

    <LinearLayout orientation="vertical" padding="18dp">

        <!-- Row 1: Shield icon + "Admin Dashboard" + pending badge -->
        <LinearLayout horizontal>
            <FrameLayout 40x40 orange gradient bg>
                <ImageView ic_shield_check white />
            </FrameLayout>
            <LinearLayout vertical flex=1>
                <TextView "Admin Dashboard" white bold />
                <TextView "Super Admin · Elevated Access" white 42% alpha />
            </LinearLayout>
            <TextView id="@+id/txtAdminPendingBadge" orange bg pill />
        </LinearLayout>

        <!-- Divider -->
        <View 1dp white 8% alpha />

        <!-- Quick stats row -->
        <LinearLayout horizontal>
            <ImageView ic_flag amber />
            <TextView id="@+id/txtAdminPendingInfo" white 55% alpha />
        </LinearLayout>

        <!-- CTA button: "Open Admin Panel" -->
        <LinearLayout id="@+id/btnOpenAdminPanel" orange gradient bg>
            <ImageView ic_dashboard white />
            <TextView "Open Admin Panel" white bold />
            <ImageView ic_chevron_right white />
        </LinearLayout>

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### 4.2 Modify `ProfileFragment.java`

- Bind new views: `cardAdminDashboard`, `txtAdminPendingBadge`, `txtAdminPendingInfo`, `btnOpenAdminPanel`
- In `setupClickListeners()`: wire `btnOpenAdminPanel` to navigate to `adminReportsListFragment`
- In `renderState()`: check `state.isAdmin()` to toggle `cardAdminDashboard` visibility and set pending count text

### 4.3 Modify `ProfileViewModel.java`

- Inject `SessionManager` (to call `isAdmin()`)
- Inject `AdminReportRepository` (to fetch pending count)
- If admin: call `adminReportRepository.getReportsByStatus("OPEN", callback)` on load, store pending count
- Expose navigation event for admin panel

### 4.4 Modify `ProfileUiState.java`

Add fields:
```java
private final boolean isAdmin;
private final int adminPendingCount;
```
Update constructor, static factories, and getters.

### 4.5 Modify `ProfileViewModelFactory.java`

Add `SessionManager` and `AdminReportRepository` as constructor parameters.

---

## Phase 5 — Frontend UI: Admin Reports List Screen (G-5, part 2)

### 5.1 Navigation

**File:** `frontend/.../res/navigation/nav_graph.xml`

Add entries:

```xml
<fragment
    android:id="@+id/adminReportsListFragment"
    android:name="com.walkmate.ui.admin.reports.AdminReportsListFragment"
    android:label="Reports">
    <action
        android:id="@+id/action_adminReportsList_to_adminReportDetail"
        app:destination="@id/adminReportDetailFragment"
        app:enterAnim="@anim/nav_default_enter_anim"
        app:exitAnim="@anim/nav_default_exit_anim"
        app:popEnterAnim="@anim/nav_default_pop_enter_anim"
        app:popExitAnim="@anim/nav_default_pop_exit_anim" />
</fragment>

<fragment
    android:id="@+id/adminReportDetailFragment"
    android:name="com.walkmate.ui.admin.reports.AdminReportDetailFragment"
    android:label="Report Detail">
    <argument
        android:name="REPORT_ID"
        app:argType="string"
        android:defaultValue="" />
</fragment>
```

Add action from `profileFragment`:
```xml
<action
    android:id="@+id/action_profile_to_adminReportsListFragment"
    app:destination="@id/adminReportsListFragment"
    ... standard anims ... />
```

### 5.2 Layout: `fragment_admin_reports_list.xml`

**File:** `frontend/.../res/layout/fragment_admin_reports_list.xml`

Structure (matches `MobileAdminReportsList.tsx`):

```
FrameLayout (root, bg_cream)
├── ProgressBar (center, gone by default)
└── LinearLayout (vertical, content root)
    ├── Header (standard header bar: back + "Reports" + pending badge + search toggle)
    ├── Search bar (EditText, gone by default, toggle visibility)
    ├── Stats grid (2x2 GridLayout or 2-column LinearLayout)
    │   ├── Stat card: Total (slate bg)
    │   ├── Stat card: Pending (amber bg)
    │   ├── Stat card: Approved (green bg)
    │   └── Stat card: Rejected (red bg)
    ├── Filter tabs (HorizontalScrollView → LinearLayout with 3 pill buttons: All, Pending, Resolved)
    ├── RecyclerView (report cards list)
    └── Empty state (gone by default: icon + "No reports found" + subtitle)
```

### 5.3 Item Layout: `item_admin_report.xml`

Report card item for RecyclerView:

```
MaterialCardView (white, 18dp corners, conditional orange border for pending)
└── LinearLayout vertical padding=16dp
    ├── Row 1: reportId (mono) + reportedUserName (bold) ←→ status badge pill
    ├── Row 2: reason chip (colored bg) + "by reporterName" (muted)
    └── Row 3: filedAt date (muted) ←→ "Review >" button (orange, for pending) or chevron (for resolved)
```

### 5.4 RecyclerView Adapter: `AdminReportAdapter.java`

**File:** `frontend/.../ui/admin/reports/AdminReportAdapter.java`

Standard `RecyclerView.Adapter<VH>`:
- `onBindViewHolder`: bind `AdminReport` data, set status badge colors, reason chip colors, click listener
- Click callback interface: `OnReportClickListener` with `onReportClick(String reportId)`

### 5.5 UiState: `AdminReportsListUiState.java`

**File:** `frontend/.../ui/admin/reports/AdminReportsListUiState.java`

```java
public class AdminReportsListUiState {
    private final boolean isLoading;
    private final List<AdminReport> reports;  // all reports (filtering done in Fragment)
    private final String error;

    // Constructor, static factories (loading(), error()), getters
    // Derived: getTotalCount(), getPendingCount(), getApprovedCount(), getRejectedCount()
}
```

### 5.6 ViewModel: `AdminReportsListViewModel.java`

**File:** `frontend/.../ui/admin/reports/AdminReportsListViewModel.java`

- Injected: `AdminReportRepository`
- `loadReports()`: calls `repository.getAllReports()`, posts `UiState`
- Exposes `LiveData<AdminReportsListUiState>`
- Navigation event for report detail

### 5.7 Factory: `AdminReportsListViewModelFactory.java`

Standard `ViewModelProvider.Factory` pattern.

### 5.8 Fragment: `AdminReportsListFragment.java`

**File:** `frontend/.../ui/admin/reports/AdminReportsListFragment.java`

Responsibilities:
1. Inflate `fragment_admin_reports_list.xml`
2. Set up RecyclerView + adapter
3. Wire filter tabs (All / Pending / Resolved) — filter is client-side on the full list
4. Wire search — client-side text filter on `reportedUserName`, `reporterName`, `reason`, `reportId`
5. Observe `LiveData<UiState>` → render stats grid counts, update adapter, toggle empty state
6. Handle report click → navigate to detail with `REPORT_ID` argument
7. Handle back button → `NavController.popBackStack()`

---

## Phase 6 — Frontend UI: Admin Report Detail Screen (G-5, part 3)

### 6.1 Layout: `fragment_admin_report_detail.xml`

**File:** `frontend/.../res/layout/fragment_admin_report_detail.xml`

Structure (matches `MobileAdminReportDetail.tsx`):

```
FrameLayout (root, bg_cream)
├── ProgressBar (center, gone)
└── NestedScrollView
    └── LinearLayout vertical
        ├── Header (back button + reportId + "Review" breadcrumb + status badge)
        │
        ├── Card A: "Evidence" (READ ONLY badge)
        │   ├── FieldRow: Report ID (monospace)
        │   ├── FieldRow: Reported User (icon + name bold)
        │   ├── FieldRow: Reporter (name)
        │   ├── FieldRow: Reason (colored chip)
        │   ├── FieldRow: Evidence Link (clickable, or "No evidence provided")
        │   └── FieldRow: Trust Score Impact (-Xpts red chip, or "No penalty")
        │
        ├── Card B: "Resolution"
        │   ├── [PENDING state]: instruction text + EditText (resolution note, 500 char max, char counter) + Approve/Reject buttons
        │   ├── [APPROVED state]: Decision (green check), Decided On, Note, info banner "penalty remains"
        │   └── [REJECTED state]: Decision (red X), Decided On, Note, warning/info banner
        │
        └── Footer: "Filed {date}" centered
```

### 6.2 UiState: `AdminReportDetailUiState.java`

```java
public class AdminReportDetailUiState {
    private final boolean isLoading;
    private final boolean isProcessing;  // true while resolve API is in-flight
    private final AdminReport report;    // null when loading/error
    private final String error;
    private final boolean isResolved;    // set to true after successful resolve → triggers nav back or refresh

    // Constructor, static factories, getters
}
```

### 6.3 ViewModel: `AdminReportDetailViewModel.java`

- Injected: `AdminReportRepository`
- `loadReport(String reportId)`: calls `repository.getReportById()`
- `resolveReport(String reportId, String resolution, String note)`: calls `repository.resolveReport()`, sets `isProcessing = true`, then updates state
- `consumeError()`: clears one-time error

### 6.4 Factory: `AdminReportDetailViewModelFactory.java`

Standard factory. Passes `AdminReportRepository`.

### 6.5 Fragment: `AdminReportDetailFragment.java`

Responsibilities:
1. Read `REPORT_ID` from arguments
2. Inflate `fragment_admin_report_detail.xml`
3. Bind all views
4. Call `viewModel.loadReport(reportId)` on creation
5. Observe `LiveData<UiState>`:
   - Loading → show progress, hide content
   - Data → render Evidence card fields, Resolution card based on status
   - Processing → disable buttons, show loading indicator
   - Resolved → show Toast, navigate back to list
   - Error → Toast
6. Approve button click → show confirmation `AlertDialog` → on confirm → `viewModel.resolveReport(id, "APPROVED", note)`
7. Reject button click → show confirmation `AlertDialog` → on confirm → `viewModel.resolveReport(id, "REJECTED", note)`
8. Evidence link click → open URL in browser via `Intent(ACTION_VIEW)`

**Confirmation dialog** uses standard `AlertDialog.Builder` (same pattern as logout confirmation in `ProfileFragment`). The React UI uses a bottom sheet, but `AlertDialog` is the established pattern in the codebase and is simpler to implement.

---

## Phase 7 — Register Dependencies in WalkMateApplication

**File:** `frontend/.../WalkMateApplication.java`

Add:
```java
// In onCreate or lazy init:
private AdminReportApiService adminReportApiService;
private AdminReportRepositoryImpl adminReportRepository;

public AdminReportRepository getAdminReportRepository() {
    if (adminReportRepository == null) {
        adminReportApiService = ApiClient.getRetrofit(sessionManager)
                .create(AdminReportApiService.class);
        adminReportRepository = new AdminReportRepositoryImpl(
                adminReportApiService,
                new AdminReportMapper(),
                executorService);
    }
    return adminReportRepository;
}
```

---

## Complete File Inventory

### Backend — Modified Files

| File | Phase | Change |
|---|---|---|
| `AdminReportResponse.java` | 1.1 | Add `reporterName`, `reportedUserName` fields |
| `AdminReportController.java` | 1.2 | Inject profile service, batch-load names, update `toAdminResponse()` |

### Frontend — New Files

| File | Phase | Description |
|---|---|---|
| `data/.../dto/response/report/AdminReportResponse.java` | 3.1 | Response DTO |
| `data/.../dto/request/report/ResolveReportRequest.java` | 3.2 | Request DTO |
| `data/.../api/AdminReportApiService.java` | 3.3 | Retrofit interface |
| `domain/report/AdminReport.java` | 3.4 | Domain model |
| `domain/report/AdminReportRepository.java` | 3.5 | Repository interface |
| `data/mapper/AdminReportMapper.java` | 3.6 | DTO → Domain mapper |
| `data/repository/AdminReportRepositoryImpl.java` | 3.7 | Repository impl |
| `res/layout/fragment_admin_reports_list.xml` | 5.2 | Reports list layout |
| `res/layout/item_admin_report.xml` | 5.3 | Report card item |
| `ui/admin/reports/AdminReportAdapter.java` | 5.4 | RecyclerView adapter |
| `ui/admin/reports/AdminReportsListUiState.java` | 5.5 | List UiState |
| `ui/admin/reports/AdminReportsListViewModel.java` | 5.6 | List ViewModel |
| `ui/admin/reports/AdminReportsListViewModelFactory.java` | 5.7 | List VM factory |
| `ui/admin/reports/AdminReportsListFragment.java` | 5.8 | List Fragment |
| `res/layout/fragment_admin_report_detail.xml` | 6.1 | Detail layout |
| `ui/admin/reports/AdminReportDetailUiState.java` | 6.2 | Detail UiState |
| `ui/admin/reports/AdminReportDetailViewModel.java` | 6.3 | Detail ViewModel |
| `ui/admin/reports/AdminReportDetailViewModelFactory.java` | 6.4 | Detail VM factory |
| `ui/admin/reports/AdminReportDetailFragment.java` | 6.5 | Detail Fragment |

### Frontend — Modified Files

| File | Phase | Change |
|---|---|---|
| `SessionManager.java` | 2 | Add `getUserRole()`, `isAdmin()` |
| `fragment_profile.xml` | 4.1 | Add Admin Dashboard card (gone by default) |
| `ProfileFragment.java` | 4.2 | Bind admin views, wire click, render admin state |
| `ProfileViewModel.java` | 4.3 | Inject SessionManager + AdminReportRepo, load pending count |
| `ProfileUiState.java` | 4.4 | Add `isAdmin`, `adminPendingCount` fields |
| `ProfileViewModelFactory.java` | 4.5 | Add new dependencies |
| `nav_graph.xml` | 5.1 | Add 2 fragment destinations + actions |
| `WalkMateApplication.java` | 7 | Register `AdminReportRepository` singleton |

---

## Implementation Order

```
Phase 1 (Backend G-3)  ──→  Phase 2 (Frontend G-1)  ──→  Phase 3 (Data Layer G-4)
                                                               │
                                                               ▼
Phase 4 (Profile Admin Card)  ──→  Phase 5 (Reports List)  ──→  Phase 6 (Report Detail)
                                                               │
                                                               ▼
                                                          Phase 7 (DI Registration)
```

Phase 1 must be done first (backend). Phases 2–3 can be parallelized. Phases 4–6 are sequential (each builds on previous). Phase 7 should be done alongside Phase 3–4 as dependencies are created.

---

## Design Decisions

| Decision | Rationale |
|---|---|
| **Use `AlertDialog` for confirmations** (not BottomSheet) | Consistent with existing codebase pattern (`ProfileFragment.showLogoutAllConfirmation()`). Simpler implementation. |
| **Client-side filtering** (not server-side per-tab) | Backend already returns all reports in one call. Reports count is small (admin-only). Avoids 3 separate API calls for tab switching. |
| **No Room cache for admin data** | Admin data is transient and always needs latest state. No offline-first requirement for admin features. |
| **Package: `ui/admin/reports/`** | Follows `ui/<feature>/<sub-feature>/` convention. Future admin sub-features (Users, Settings) go under `ui/admin/<name>/`. |
| **Status mapping in Mapper** | `OPEN` → `PENDING` conversion happens in `AdminReportMapper` at the data boundary, keeping UI code clean. |
