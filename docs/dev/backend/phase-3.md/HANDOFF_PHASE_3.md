# WalkMate Handoff — End of Phase 3 (WalkIntent Core)

## What Was Delivered

### Backend

- **`CONSUMED` transition applied** — Phase 0 canonical enum name now live in DB and code:
  - `V6__migrate_intent_status_matched_to_consumed.sql` adds `CONSUMED` to `intent_status` PG enum and migrates any legacy `MATCHED` rows.
  - `IntentStatus.MATCHED` retained in Java enum as `@Deprecated` (PostgreSQL cannot remove enum values; application code will never write it again).
- **Overlap guard** — `createIntent()` now calls `hasOverlappingActiveIntent()` before saving. A user cannot hold two `OPEN` or `CONSUMED` intents that share any portion of a time window.
- **Ownership check on cancel** — `cancelIntent(intentId, callerId)` verifies the JWT user owns the intent before allowing cancellation.
- **`GET /api/v1/intents` endpoint** — returns all `OPEN` intents for the authenticated user, newest-first.
- **`date + float` API contract** — `POST /api/v1/intents` now accepts `date` (yyyy-MM-dd) and fractional-hour floats (`time_start`, `time_end`) instead of raw ISO-8601 timestamps. The controller converts to `Instant` in `Asia/Ho_Chi_Minh` timezone before passing to the domain.

### Frontend (Android)

- `WalkIntentRepository` domain interface updated: `createIntent` now accepts `date` and `tags` parameters.
- `WalkIntentRepositoryImpl` de-mocked: `createIntent`, `listActiveIntents`, and `cancelIntent` hit the real backend via authenticated Retrofit. `findMatch` remains stubbed (Phase 4).

---

## API Endpoints (Phase 3)

### 1) Create intent

```
POST /api/v1/intents
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "hotspot_id": "22222222-2222-2222-2222-222222222222",
  "date": "2026-04-01",
  "time_start": 17.0,
  "time_end": 18.5,
  "age_min": 20,
  "age_max": 40
}
```

Response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "<uuid>",
    "hotspot_id": "22222222-2222-2222-2222-222222222222",
    "user_id": "<uuid>",
    "time_window_start": "2026-04-01T10:00:00Z",
    "time_window_end": "2026-04-01T11:30:00Z",
    "age_min": 20,
    "age_max": 40,
    "status": "OPEN",
    "created_at": "...",
    "expires_at": "2026-04-01T11:30:00Z"
  },
  "error": null
}
```

### 2) List active intents

```
GET /api/v1/intents
Authorization: Bearer <jwt>
```

Response `200 OK`:
```json
{
  "success": true,
  "data": [ /* array of WalkIntentResponse */ ],
  "error": null
}
```

### 3) Cancel intent

```
DELETE /api/v1/intents/{intentId}
Authorization: Bearer <jwt>
```

Response `200 OK` (owner cancelling their own intent):
```json
{ "success": true, "data": null, "error": null }
```

### 4) Poll for match (unchanged — mocked on frontend until Phase 4)

```
GET /api/v1/intents/{intentId}/match
Authorization: Bearer <jwt>
```

Response `204 No Content` if no match found yet.

---

## Time Conversion Contract

| Frontend sends | Backend stores | Example |
|---|---|---|
| `date: "2026-04-01"` + `time_start: 17.0` | `time_window_start: 2026-04-01T10:00:00Z` | 17:00 ICT = 10:00 UTC |
| `date: "2026-04-01"` + `time_end: 18.5` | `time_window_end: 2026-04-01T11:30:00Z` | 18:30 ICT = 11:30 UTC |

Timezone: `Asia/Ho_Chi_Minh` (UTC+7). Conversion formula: `Math.round(hourFloat * 60)` → integer minutes, avoiding float rounding drift.

The frontend `WalkIntentMapper.toDomain()` reverses this: ISO-8601 string → local time float using the device timezone. Works correctly when device is set to VN timezone.

---

## Error Codes (WalkIntent Domain)

| Code | HTTP | Trigger |
|---|---|---|
| `INTENT_NOT_FOUND` | 400 | `intentId` does not exist |
| `INTENT_ALREADY_CANCELLED` | 400 | Cancel on already-cancelled intent |
| `INTENT_ALREADY_CONSUMED` | 400 | Cancel on a matched/consumed intent |
| `INTENT_NOT_OWNER` | 400 | Cancel attempted by non-owner |
| `INTENT_OVERLAPPING` | 400 | User already has OPEN/CONSUMED intent in this window |
| `INVALID_TIME_RANGE` | 400 | `timeEnd ≤ timeStart` or window < 15 min |
| `INVALID_AGE_RANGE` | 400 | `ageMin > ageMax` |
| `INVALID_INTENT_DATA` | 400 | Blank required field in domain |
| `HOTSPOT_NOT_FOUND` | 400 | `hotspotId` does not match any hotspot |
| `VALIDATION_ERROR` | 422 | Bean validation failure (missing field, bad date format, etc.) |

---

## Flyway Migration State (End of Phase 3)

| Version | Description |
|---|---|
| V1 | Create hotspot table |
| V1.1 | Create user_account table + auth enums |
| V1.2 | Create refresh_token table |
| V2 | Create walk_intent table + intent_status enum |
| V3 | Create match_proposal table + proposal_status enum |
| V4 | Seed initial hotspot data |
| V5 | Seed Phase 2 canonical 5-hotspot catalogue |
| V6 | Add CONSUMED to intent_status; migrate MATCHED rows |

---

## Ready for Phase 4

- Matching infrastructure (`RuleBasedMatchingStrategy`, `findOpenCandidates` in repo) is already implemented and wired — only the frontend `findMatch()` stub and proposal persistence need to be connected.
- `WalkIntent.consume()` is the domain method to call when a match is confirmed. It guards against double-consumption.
- The `match_proposal` table (V3) is created but not yet written to by application code.
