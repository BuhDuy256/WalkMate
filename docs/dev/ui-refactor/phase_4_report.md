# Phase 4 Report — Domain Models
**Date:** 2026-04-09
**Branch:** `implement/realtime`
**Gaps closed:** 3.2, 3.3 (WalkIntent fields), 4.1 (WalkProposal fields), 5.4 (WalkSession timestamps + constants), 7.1 (SessionSummary), 7.2 (SessionRoute)

---

## Updated Constructor Signatures

### `WalkIntent`
```java
public WalkIntent(
    String id, String hotspotId, String userId,
    float timeStart, float timeEnd,
    int ageMin, int ageMax,
    String status, String createdAt,
    List<String> tags,
    String expiresAt,    // NEW — ISO-8601, nullable
    String description   // NEW — nullable
)
```

### `WalkProposal`
```java
public WalkProposal(
    String proposalId, String intentId,
    String matchedUserId, String matchedUserName,
    int matchedUserAge, int trustScore,
    List<String> overlappingTags,
    float overlappingTimeStart, float overlappingTimeEnd,
    Status status,
    String expiresAt,          // NEW — ISO-8601
    double meetingLat,         // NEW
    double meetingLng,         // NEW
    String myAcceptanceStatus, // NEW — "ACCEPTED" or null
    String sessionId           // NEW — null until CONFIRMED
)
```

### `WalkSession`
```java
public WalkSession(
    String sessionId, String proposalId,
    String partnerName, String partnerAvatar,
    double meetingPointLat, double meetingPointLng,
    String scheduledTime, Status status,
    String scheduledEnd,       // NEW
    String startedAt,          // NEW
    String endedAt,            // NEW
    String userAActivatedAt,   // NEW
    String userBActivatedAt,   // NEW
    boolean isReviewed,        // NEW
    boolean isCallerUserA      // NEW
)
```

Public static constants added:
```java
public static final int ACTIVATION_WINDOW_BEFORE_MINUTES = 10;
public static final int ACTIVATION_WINDOW_AFTER_MINUTES  = 15;
public static final int MINIMUM_WALK_DURATION_MINUTES    = 5;
```

---

## New Files Created

| File | Package |
|---|---|
| `domain/walksession/AbortReason.java` | `com.walkmate.domain.walksession` |
| `domain/walksession/SessionRoute.java` | `com.walkmate.domain.walksession` |
| `domain/walksession/SessionSummary.java` | `com.walkmate.domain.walksession` |

### `AbortReason.java`
Enum with `SAFETY_CONCERN`, `EMERGENCY`, `PARTNER_MISCONDUCT`, `OTHER`. Has `toApiValue()` returning `name()`.

### `SessionRoute.java`
Fields: `List<String> userAPolylines`, `List<String> userBPolylines`, `double totalDistanceKm`, `int durationMinutes`. Full constructor + getters.

### `SessionSummary.java`
Fields: `String sessionId`, `WalkSession.Status status`, `String partnerId`, `String scheduledStart`, `double totalDistanceKm`, `int durationMinutes`, `boolean isReviewed`. Full constructor + getters.

---

## `WalkState.FINISHING` Added
**File:** `domain/tracking/WalkState.java`

`FINISHING` was inserted between `PAUSED` and `FINISHED`:
```
READY → ACTIVE ↔ PAUSED → FINISHING → FINISHED
```
Represents "Complete tapped; API call in progress, awaiting server confirmation."

---

## Call Sites Broken by Constructor Changes

### `WalkIntentMapper.java`
Updated `toDomain()` to pass `response.getExpiresAt()` and `null` (description) as the two new trailing args. TODO comment removed.

### `WalkProposalMapper.java`
- `toDomain()` — added 5 new trailing args: `expiresAt`, `proposedLat`, `proposedLng`, `myAcceptanceStatus`, `sessionId`. TODO comment removed.
- `toSession()` — WalkSession constructor now requires 15 args. Added 7 new trailing args with safe defaults (`null` for all String timestamps, `false` for booleans). `isCallerUserA` is `false` here because caller identity is unknown at the proposal stage; it is corrected when the session is subsequently fetched via `WalkSessionMapper.toDomain(response, callerId)`.

### `WalkSessionMapper.java`
Updated `toDomain()` to wire in all 7 new fields from `WalkSessionResponse`. `isCallerUserA` is computed as `callerId.equals(response.getUserIdA())`. TODO block removed.

### `SessionSummaryMapper.java`
`response.getStatus()` (String) was previously passed directly to `SessionSummary`; now converted via a `toStatus()` helper to `WalkSession.Status`. Also added missing `WalkSession` import.
