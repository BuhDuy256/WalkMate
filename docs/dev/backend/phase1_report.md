# WalkMate Backend — Phase 1 Report
**Date:** 2026-04-13
**Branch:** `merge/oauth`

## Summary

Two of three Phase 1 gaps are closed. Gap 1.1 was reverted after determining the SSOT was incorrect — the implementation behaviour (`deviceId` in request, auto-login on register) is intentional. The SSOT has been corrected to match. No DB migrations required.

---

## Files Modified

| File | Change |
|---|---|
| `application/hotspot/HotspotQueryService.java` | Removed empty-list guard block; `getAllHotspots()` now returns the list directly |
| `presentation/controller/user/UserController.java` | Both `logout()` and `logoutAll()` return type changed from `ResponseEntity<Void>` (204) to `ResponseEntity<ApiResponse<Void>>` (200) |
| `docs/single-source-of-truth/use-cases/backend_use_cases.md` | UC-01 corrected: `deviceId` added to request payload; response updated to full token payload |
| `docs/dev/backend/gap_analysis.md` | Gap 1.1 marked as SSOT error (not a code gap) |

---

## Gap Closure Confirmation

### Gap 2.1 — HotspotQueryService empty list (🟠 HIGH)
**Closed.** `getAllHotspots()` now returns an empty list instead of throwing `DomainException`. The `HotspotErrorCode.NO_HOTSPOT_AVAILABLE` entry is retained.

### Gap 1.1 — UC-01 Register response — REVERTED / SSOT CORRECTED
**Not a gap.** The SSOT was wrong. The implementation is the intended behaviour:
- Request includes `deviceId` (required to issue a refresh token tied to the device)
- Response is a full token payload — client auto-logs-in on register
- `backend_use_cases.md` UC-01 has been corrected to reflect this
- `RegisterUserResponse.java` (created during Fix 2) has been deleted
- All code changes from Fix 2 have been reverted

### Gap 1.2 — UC-10/11 Logout returns 200 (🟠 HIGH)
**Closed.** Both `POST /api/v1/auth/logout` and `POST /api/v1/auth/logout-all` now return `200 OK` with `{ "data": null }` (`ApiResponse<Void>`), replacing the previous `204 No Content`.
