# 02 — "Report" Use Case: Trigger & Current Codebase Trace

## Overview

The Report (Incident Report) use case allows a walk participant to file a complaint against their partner after a completed session. It is **purely an evidence-collection flow** — it writes one row to `session_report` and currently has **no automatic side-effects** on trust scores, matching, or any other system.

---

## Entry Point (Frontend Trigger)

**What triggers the Report screen?**

The report button is exposed inside the **Session History** list, not during an active session.

**Files:**
- `frontend/.../ui/history/SessionHistoryAdapter.java` (lines 131–142)
- `frontend/.../ui/history/SessionHistoryFragment.java` (lines 95–101)

### Visibility Conditions (all three must be true)

| Condition | Value required |
|---|---|
| Global session status | `COMPLETED` |
| Current user's individual status | `COMPLETED` |
| Partner's individual status | `NO_SHOW` |

When conditions are met, the adapter fires `OnReportClickListener` with three arguments:
- `sessionId`
- `partnerId` (the user being reported)
- `sessionTerminalAtMs` (end timestamp — used for the 72-hour window check on the frontend)

The fragment then navigates to `ReportIncidentFragment` via `NavController`.

---

## Frontend Data Flow

```
SessionHistoryAdapter (report button click)
  └─ SessionHistoryFragment.OnReportClickListener
       └─ NavController.navigate → ReportIncidentFragment
            ├─ args: sessionId, reportedUserId, sessionTerminalAtMs
            └─ ReportIncidentFragment.onViewCreated()
                 ├─ Validates 72-hour reporting window (client-side guard)
                 ├─ RadioGroup → selectedReason() → AbortReason enum
                 │    SAFETY_CONCERN | EMERGENCY | PARTNER_MISCONDUCT | OTHER
                 └─ btnSubmitReport.onClick()
                      └─ ReportIncidentViewModel.submitReport(sessionId, reportedUserId, reason, evidenceUrl)
```

### ReportIncidentViewModel

**File:** `frontend/.../ui/report/ReportIncidentViewModel.java` (lines 40–69)

- Validates `reason` and `reportedUserId` are non-blank.
- Delegates to `sessionRepo.reportSession(sessionId, reportedUserId, reason, evidenceUrl, callback)`.
- Posts `ReportIncidentUiState` (IDLE / LOADING / SUBMITTED / ERROR).

### Frontend Repository → HTTP

**File:** `frontend/.../data/repository/WalkSessionRepositoryImpl.java` (lines 205–230)

Builds `ReportSessionRequest { reportedUserId, reason, evidenceUrl }` and calls:

```
POST /api/v1/sessions/{sessionId}/report
```

via `SessionApiService` (Retrofit).

---

## Backend Data Flow

```
POST /api/v1/sessions/{sessionId}/report
  └─ ReportController.submitReport()                         [Presentation]
       └─ ReportCommandService.submitReport()                [@Transactional, Application]
            ├─ Load WalkSession (throws SESSION_NOT_FOUND)
            ├─ Assert reporter is participant (throws SESSION_NOT_PARTICIPANT)
            ├─ Assert reporter ≠ reportedUser (throws REPORT_SELF_NOT_ALLOWED)
            ├─ Assert session status = COMPLETED (throws REPORT_SESSION_NOT_COMPLETED)
            ├─ Assert within 72-hour window (throws REPORT_WINDOW_EXPIRED)
            ├─ Assert no duplicate (throws REPORT_ALREADY_SUBMITTED)
            └─ SessionReport.create() → reportRepository.save()
                 └─ INSERT INTO session_report (...)
```

Returns `HTTP 201 CREATED` with `{ reportId, createdAt }`.

### Business Rules (ReportCommandService)

**File:** `backend/.../application/report/ReportCommandService.java` (lines 34–81)

| Rule | Exception thrown |
|---|---|
| Session must exist | `SESSION_NOT_FOUND` |
| Reporter must be a participant | `SESSION_NOT_PARTICIPANT` |
| Cannot report yourself | `REPORT_SELF_NOT_ALLOWED` |
| Session must be `COMPLETED` | `REPORT_SESSION_NOT_COMPLETED` |
| Must report within 72 hours (configurable: `${app.report.completed-window-hours:72}`) | `REPORT_WINDOW_EXPIRED` |
| One report per reporter per session | `REPORT_ALREADY_SUBMITTED` |

### Domain Model

**File:** `backend/.../domain/report/SessionReport.java` (lines 45–48)

```java
public static SessionReport create(
    String sessionId, String reporterId, String reportedUserId,
    String reason, String evidenceUrl) {
    // auto-generates reportId (UUID), createdAt = Instant.now()
}
```

### Database Write

**File:** `backend/.../infrastructure/repository/report/SessionReportJdbcRepository.java` (lines 17–32)

```sql
INSERT INTO session_report
  (report_id, session_id, reporter_id, reported_user_id, reason, evidence_url, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
```

Unique constraint: `UNIQUE (session_id, reporter_id)` — one report per reporter per session.

---

## What Report Does NOT Do (Current State)

| Action | Status |
|---|---|
| Update `user_account.trust_score` | ❌ Not done |
| Trigger gamification events | ❌ Not done |
| Modify `matching_preference_model` | ❌ Not done |
| Affect active or future matching | ❌ Not done |
| Update `walk_session` status | ❌ Not done |
| Create a `dispute_case` | ❌ Not done (schema exists, unused) |

Trust-score deductions today come only from `GamificationCommandService`, driven by session outcomes (`NO_SHOW` → −100, late cancellation → −20). A report is **evidence only** — no automatic penalty is applied.

---

## Complete File Reference

### Frontend

| Role | File |
|---|---|
| History list (trigger point) | `frontend/.../ui/history/SessionHistoryFragment.java` |
| Adapter (button visibility) | `frontend/.../ui/history/SessionHistoryAdapter.java` |
| Report screen UI | `frontend/.../ui/report/ReportIncidentFragment.java` |
| ViewModel | `frontend/.../ui/report/ReportIncidentViewModel.java` |
| UI state | `frontend/.../ui/report/ReportIncidentUiState.java` |
| ViewModel factory | `frontend/.../ui/report/ReportIncidentViewModelFactory.java` |
| Repository impl | `frontend/.../data/repository/WalkSessionRepositoryImpl.java` |
| Retrofit interface | `frontend/.../data/datasource/remote/api/SessionApiService.java` |
| Request DTO | `frontend/.../data/datasource/remote/dto/request/walksession/ReportSessionRequest.java` |

### Backend

| Layer | File |
|---|---|
| Controller | `backend/.../presentation/controller/report/ReportController.java` |
| Request DTO | `backend/.../presentation/dto/request/report/SubmitReportRequest.java` |
| Command service | `backend/.../application/report/ReportCommandService.java` |
| Domain model | `backend/.../domain/report/SessionReport.java` |
| Repo interface | `backend/.../domain/report/SessionReportRepository.java` |
| Error codes | `backend/.../domain/report/ReportErrorCode.java` |
| Repo impl | `backend/.../infrastructure/repository/report/SessionReportJdbcRepository.java` |
