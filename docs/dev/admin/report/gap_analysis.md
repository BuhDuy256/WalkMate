# Gap Analysis: Admin Page — React UI vs. Backend API vs. Frontend Architecture

**Date:** 2026-05-05  
**Scope:** Identify all missing APIs, missing data fields, and architectural gaps that prevent the React Admin UI from being translated into a working Android (XML + Java) implementation connected to the existing backend.

---

## 1. UI Screens Inventory (from Figma React code)

| Screen | React File | Purpose |
|---|---|---|
| **Profile → Admin Card** | `ProfileScreen.tsx` (line 200–283) | Show admin badge on profile name, "Admin Dashboard" card with pending count, "Open Admin Panel" CTA |
| **Reports List** | `MobileAdminReportsList.tsx` | Header with pending badge, 2×2 stats grid (Total / Pending / Approved / Rejected), filter tabs (All / Pending / Resolved), search, report cards list |
| **Report Detail** | `MobileAdminReportDetail.tsx` | Header (report ID, status badge), Evidence card (report ID, reported user, reporter, reason, evidence link, trust score impact), Resolution card (form when pending, read-only when resolved), confirmation bottom sheet |

---

## 2. Data Requirements per Screen

### 2.1 Profile → Admin Card

| UI Element | Data Needed | Available Source |
|---|---|---|
| Admin badge next to name | User's `role` (is it `"ADMIN"`?) | **GAP G-1**: JWT carries `role` claim, but `SessionManager` does not expose a `getRole()` method. Frontend has no way to know the user's role. |
| "X pending" badge | Count of reports with `status = "OPEN"` | **GAP G-2**: No dedicated count endpoint. Must be derived from `GET /api/v1/admin/reports?status=OPEN` list length. Acceptable but requires an admin API call from the Profile screen. |

### 2.2 Reports List Screen

| UI Element | Data Needed | Backend API Field | Gap? |
|---|---|---|---|
| Report card: reported user name | `reportedUserName` | `AdminReportResponse.reportedUserId` (UUID only) | **GAP G-3**: Backend returns `reportedUserId` (UUID), not a display name. The UI needs the reported user's full name. |
| Report card: reporter name | `reporterName` | `AdminReportResponse.reporterId` (UUID only) | **GAP G-3** (same): Backend returns `reporterId` (UUID), not a display name. |
| Stats: Total | List length | `GET /api/v1/admin/reports` → list | ✅ Available |
| Stats: Pending | Filter count | Filter by `status == "OPEN"` | ✅ Available (note: backend uses `"OPEN"`, React mock uses `"pending"`) |
| Stats: Approved | Filter count | Filter by `status == "APPROVED"` | ✅ Available |
| Stats: Rejected | Filter count | Filter by `status == "REJECTED"` | ✅ Available |
| Report card: status | `status` | `AdminReportResponse.status` | ✅ Available (mapping: `OPEN` → `PENDING` display) |
| Report card: reason | `reason` | `AdminReportResponse.reason` | ✅ Available |
| Report card: filed date | `createdAt` | `AdminReportResponse.createdAt` | ✅ Available |
| Report card: ID display | `reportId` | `AdminReportResponse.reportId` | ✅ Available |
| Search by name | Match against names | Requires names to be in the response | **GAP G-3** blocks this |

### 2.3 Report Detail Screen

| UI Element | Data Needed | Backend API Field | Gap? |
|---|---|---|---|
| Reported User name | `reportedUserName` | UUID only | **GAP G-3** |
| Reporter name | `reporterName` | UUID only | **GAP G-3** |
| Evidence link | `evidenceUrl` | `AdminReportResponse.evidenceUrl` | ✅ Available |
| Trust Score Impact | `appliedTrustDelta` | `AdminReportResponse.appliedTrustDelta` | ✅ Available |
| Resolution note input | Admin types note | `PATCH .../resolve` body `resolutionNote` | ✅ Available |
| Approve/Reject action | Submit resolution | `PATCH /api/v1/admin/reports/{id}/resolve` | ✅ Available |
| Resolved date | `resolvedAt` | `AdminReportResponse.resolvedAt` | ✅ Available |
| Resolution note (read-only) | `resolutionNote` | `AdminReportResponse.resolutionNote` | ✅ Available |

---

## 3. Gap Registry

### G-1 — Frontend Cannot Determine User Role

| Attribute | Detail |
|---|---|
| **Severity** | 🔴 Critical (blocks showing/hiding Admin Card and securing admin navigation) |
| **Root Cause** | `SessionManager.java` only extracts `sub` (userId) from JWT. The JWT carries a `"role"` claim (added by backend Phase D.1), but the frontend never reads it. |
| **Impact** | Profile screen cannot conditionally show the Admin Dashboard Card. Navigation to admin screens cannot be role-gated on the client side. |
| **Fix Location** | Frontend only: `SessionManager.java` |
| **Fix Detail** | Add `getUserRole()` method that decodes JWT payload and returns `json.optString("role", "USER")`. No backend change required. |

### G-2 — No Dedicated Pending Count Endpoint

| Attribute | Detail |
|---|---|
| **Severity** | 🟡 Low (workaround exists) |
| **Root Cause** | The Profile screen wants to show "X pending" on the Admin Card. There's no `GET /api/v1/admin/reports/count` endpoint. |
| **Impact** | Must call `GET /api/v1/admin/reports?status=OPEN` and use `list.size()` for the count. This fetches full report data just for a count. |
| **Decision** | **Accept the workaround.** Creating a new count endpoint is over-engineering for this use case. The reports list is small (admin-scoped). The list response will be reused when the user navigates to the Reports List screen. |
| **Fix** | None needed. Use `list.size()` on the filtered response. |

### G-3 — Backend Returns User UUIDs, UI Needs Display Names

| Attribute | Detail |
|---|---|
| **Severity** | 🔴 Critical (blocks the entire Reports List and Detail screens from rendering meaningful data) |
| **Root Cause** | `AdminReportResponse` returns `reporterId` and `reportedUserId` as raw UUIDs. The React mock data uses `reportedUserName` and `reporterName` (human-readable names). |
| **Impact** | Reports list and detail screens would show UUIDs instead of names — completely unusable for an admin reviewing reports. Search by name is impossible. |
| **Fix Location** | Backend: `AdminReportResponse.java`, `AdminReportController.java`, and `AdminReportQueryService.java` (or controller's `toAdminResponse` method) |
| **Fix Detail** | Add `reporterName` and `reportedUserName` fields to `AdminReportResponse`. In the controller's `toAdminResponse()` method, batch-load user profiles for the reporter and reported user UUIDs and map their `fullName` into the response. This follows the same pattern used in the leaderboard feature (see conversation `5c49e835` — batch-loading profile snapshots to avoid N+1 queries). |

### G-4 — Frontend Admin Data Layer Does Not Exist

| Attribute | Detail |
|---|---|
| **Severity** | 🔴 Critical (no code exists to connect Android UI to backend) |
| **Root Cause** | There is no `AdminReportApiService`, no `AdminReportRepository`, no `AdminReport` domain model, no response DTOs, no mapper in the frontend codebase. |
| **Impact** | Everything must be created from scratch following the Frontend Architecture. |
| **Fix Location** | Frontend: `data/datasource/remote/api/`, `data/datasource/remote/dto/response/report/`, `data/datasource/remote/dto/request/report/`, `domain/report/` (admin model), `data/mapper/`, `data/repository/` |

### G-5 — Frontend Admin UI Layer Does Not Exist

| Attribute | Detail |
|---|---|
| **Severity** | 🔴 Critical |
| **Root Cause** | No `ui/admin/` package exists. No XML layouts, no Fragments, no ViewModels, no UiState classes. |
| **Impact** | All admin UI screens must be created: `AdminReportsListFragment`, `AdminReportDetailFragment`, plus their ViewModels, UiStates, and XML layouts. Navigation actions and nav_graph entries must be added. |

### G-6 — Status Enum Mismatch Between Backend and React Mock

| Attribute | Detail |
|---|---|
| **Severity** | 🟢 Trivial (mapping only) |
| **Root Cause** | Backend uses `"OPEN"` for unresolved reports. React mock uses `"pending"`. The display label should be `"PENDING"` regardless. |
| **Impact** | Frontend must map `"OPEN"` → display as "PENDING". `"APPROVED"` and `"REJECTED"` match directly. |
| **Fix** | Handle in the frontend mapper/UI layer. No backend change. |

---

## 4. Backend API Coverage Summary

| API Endpoint | Exists? | Sufficient for UI? |
|---|---|---|
| `GET /api/v1/admin/reports` | ✅ Yes | ⚠️ Missing user names (G-3) |
| `GET /api/v1/admin/reports?status=OPEN` | ✅ Yes | ⚠️ Missing user names (G-3) |
| `GET /api/v1/admin/reports/{reportId}` | ✅ Yes | ⚠️ Missing user names (G-3) |
| `PATCH /api/v1/admin/reports/{reportId}/resolve` | ✅ Yes | ✅ Fully sufficient |
| Role-based security (`/api/v1/admin/**`) | ✅ Yes | ✅ Blocks non-ADMIN users |
| JWT `role` claim in token | ✅ Yes | ⚠️ Frontend doesn't read it (G-1) |

---

## 5. Summary: What Must Be Done

| Priority | Gap | Action Required |
|---|---|---|
| 🔴 P0 | **G-3**: Add user names to `AdminReportResponse` | **Backend change** — add `reporterName`, `reportedUserName` fields; batch-load from `user_profile` table |
| 🔴 P0 | **G-1**: Expose user role from JWT on frontend | **Frontend change** — add `getUserRole()` to `SessionManager.java` |
| 🔴 P0 | **G-4**: Create frontend data layer for admin | **Frontend change** — API service, DTOs, domain model, mapper, repository |
| 🔴 P0 | **G-5**: Create frontend UI layer for admin | **Frontend change** — XML layouts, Fragments, ViewModels, UiStates, nav_graph actions |
| 🟢 P2 | **G-6**: Status enum mapping | **Frontend change** — map `OPEN` → `PENDING` in display |
| 🟡 Accepted | **G-2**: No count endpoint | Accepted — use list.size() |
