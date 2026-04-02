# WalkMate Handoff — End of Phase 2 (Hotspot Catalogue)

## What Was Delivered

### Backend

- Hotspot domain and read flow are fully wired through DDD-lite layers:
  - `domain/hotspot/Hotspot.java`
  - `domain/hotspot/HotspotRepository.java`
  - `application/hotspot/HotspotQueryService.java`
  - `infrastructure/repository/hotspot/HotspotJdbcRepository.java`
  - `presentation/controller/hotspot/HotspotController.java`
- Hotspot API responses now expose `activeWalkerCount` (camelCase) in JSON data payload.
- `SecurityConfig` explicitly allows public read access for:
  - `GET /api/v1/hotspots`
  - `GET /api/v1/hotspots/{id}`

### Database (Flyway)

- Added non-destructive migration:
  - `V5__seed_hotspots_phase2.sql`
- Migration normalizes the catalogue to 5 requested HCMC hotspots:
  1. Nguyen Hue
  2. Turtle Lake
  3. Han Thuan Park
  4. Le Van Tam Park
  5. Crescent Mall

### Frontend (Android)

- Replaced mock hotspot repository with real Retrofit implementation in:
  - `data/repository/HotspotRepositoryImpl.java`
- Repository now calls backend endpoints via authenticated Retrofit:
  - `ApiClient.buildAuthenticatedRetrofit(sessionManager)`
- Hotspot DTO compatibility updated:
  - `HotspotResponse` accepts `activeWalkerCount` (and legacy alternates for compatibility).
- Existing `HotspotMapper` is used to map remote DTO -> domain model.

## API Endpoints (Phase 2)

### 1) List hotspots

- `GET /api/v1/hotspots`
- Public endpoint (no JWT required)
- Response shape:

```json
{
  "success": true,
  "data": [
    {
      "id": "...",
      "name": "Nguyen Hue",
      "lat": 10.77256,
      "lng": 106.70262,
      "activeWalkerCount": 0
    }
  ],
  "error": null
}
```

### 2) Get hotspot by id

- `GET /api/v1/hotspots/{id}`
- Public endpoint (no JWT required)
- Response shape:

```json
{
  "success": true,
  "data": {
    "id": "...",
    "name": "Turtle Lake",
    "lat": 10.78756,
    "lng": 106.69506,
    "activeWalkerCount": 0
  },
  "error": null
}
```

## Seed Data Snapshot

- `11111111-1111-1111-1111-111111111111` -> Nguyen Hue
- `22222222-2222-2222-2222-222222222222` -> Turtle Lake
- `33333333-3333-3333-3333-333333333333` -> Han Thuan Park
- `44444444-4444-4444-4444-444444444444` -> Le Van Tam Park
- `55555555-5555-5555-5555-555555555555` -> Crescent Mall

## Validation

- Compile validation command:
  - `./gradlew :backend:compileJava :frontend:compileDebugJavaWithJavac`
- Result: **BUILD SUCCESSFUL**

## Notes

- Existing migration numbering already used `V2` for `walk_intent`; therefore seed extension was added as `V5__seed_hotspots_phase2.sql` to preserve Flyway integrity and keep the system runnable.
