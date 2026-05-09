# Share Walk Post — Current Flow Status

**Date:** 2026-05-09 (updated after route preview implementation)
**Branch:** dev/share
**Scope:** Full-stack audit + implementation of the Walk Post / Share Walk Session feature.

---

## A. Executive Summary

### What currently works

| Area | Status |
|---|---|
| Backend create post API (`POST /api/v1/sessions/{sessionId}/posts`) | ✅ Fully implemented |
| Backend get my posts (`GET /api/v1/profiles/me/posts`) | ✅ Fully implemented |
| Backend get user posts (`GET /api/v1/profiles/{userId}/posts`) | ✅ Fully implemented |
| Backend update visibility (`PATCH /api/v1/walk-posts/{postId}/visibility`) | ✅ Fully implemented |
| Backend delete post (`DELETE /api/v1/walk-posts/{postId}`) + Supabase cleanup | ✅ Implemented |
| Backend visibility filtering (owner / friend / stranger / blocked) | ✅ Enforced server-side |
| Backend duplicate prevention (unique constraint + service check) | ✅ Enforced |
| Backend cancelled/no-show/not-completed session guard | ✅ Enforced in `WalkPostCommandService` |
| DB schema — `walk_post` with `route_preview_url`, `route_preview_path`, `route_preview_status` | ✅ V125 + V126 |
| History metadata (`canPost`, `hasPosted`, `postId`) | ✅ Returned in `SessionSummaryResponse` |
| History trigger — "Share Post" button reads `canPost` from server | ✅ Correct |
| DTO field name alignment | ✅ Correct |
| `CreateWalkPostFragment` — caption, visibility, toggles, live preview, submit | ✅ Implemented |
| `WalkActivityFragment` — loads posts, change-visibility, delete dialogs | ✅ Implemented |
| `WalkResultPostCard` — renders author, time, caption, hotspot, stats, companion | ✅ Implemented |
| `WalkResultPostCard` — route slot: READY shows image, PENDING/NO_ROUTE/FAILED shows text | ✅ Implemented |
| `WalkPostMapper` (frontend) — maps `routePreviewStatus` | ✅ Correct |
| DI via `WalkMateApplication.getWalkPostRepository()` | ✅ Registered |
| Navigation graph (history → create post, history → walk activity, profile → walk activity) | ✅ Defined |
| Public/Friend Profile walk post section (`PublicProfileFragment` + `PublicProfileViewModel`) | ✅ Fully wired |
| **Server-side route preview generation pipeline** | ✅ Implemented |
| **`StaticMapImageClient` — polyline decode/encode, Google Static Maps download** | ✅ Implemented |
| **`SupabaseRoutePreviewStorage` — upload/delete via Supabase Storage REST** | ✅ Implemented |
| **`WalkPostRoutePreviewService` — orchestrates chunks → image → upload → DB update** | ✅ Implemented |

### What is still missing / requires env config to activate

| Area | Status |
|---|---|
| `MAP_PROVIDER_API_KEY` env var not yet set | ⚠️ Must configure |
| `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_ROUTE_PREVIEW_BUCKET` env vars | ⚠️ Must configure |
| Supabase Storage bucket must be created manually | ⚠️ Must create |
| "Generating route preview…" state in card will show until re-loaded after post creation | ℹ️ By design |

### What no longer needs doing

- Client-side Canvas `RoutePreviewView` — replaced by server-side image generation.
- `RouteApiService` / `/route/me` endpoint for frontend route fetching — not needed for post cards.
- `buildStaticMapUrl()` static-map marker in `CreateWalkPostFragment` — removed.

---

## B. Current Code Flow

### Create Post → Route Preview

```
SessionHistoryFragment
  └── btnPost click → CreateWalkPostFragment (args: sessionId, distanceKm, durationSeconds, hotspotName, ...)
        └── btnPostToProfile → CreateWalkPostViewModel.submit()
              └── WalkPostRepositoryImpl.createPost() → POST /api/v1/sessions/{sessionId}/posts
                    └── WalkPostController.createPost()
                          1. WalkPostCommandService.createPost()  ← TX1: INSERT walk_post (status=PENDING)
                          2. WalkPostRoutePreviewService.generateAndPersist(postId, sessionId, authorId)
                             ├── chunkRepository.findPolylinesBySessionAndUser(sessionId, authorId)
                             ├── if empty → updateRoutePreview(postId, null, null, "NO_ROUTE")
                             ├── StaticMapImageClient.downloadRoutePreview(polylines)
                             │   ├── decode all polylines → List<double[]> points
                             │   ├── downsample to ≤150 points
                             │   ├── re-encode as single polyline
                             │   └── GET maps.googleapis.com/maps/api/staticmap?path=enc:...
                             ├── SupabaseRoutePreviewStorage.upload(path, imageBytes)
                             │   └── POST {SUPABASE_URL}/storage/v1/object/{bucket}/{path}
                             └── updateRoutePreview(postId, publicUrl, storagePath, "READY")
                          3. walkPostQueryService.getPostById(postId) → re-fetch with updated fields
                          4. WalkPostMapper.toResponse(post) → includes routePreviewUrl + routePreviewStatus
```

### Walk Activity → Post Card Rendering

```
WalkActivityFragment.onViewCreated()
  └── WalkActivityViewModel.loadMyPosts() → GET /api/v1/profiles/me/posts
        └── List<WalkPostResponse> (routePreviewUrl, routePreviewStatus included)
              → WalkPostMapper.toDomainList()
              → WalkActivityUiState.ready(posts) → adapter
                    WalkResultPostCard.bind(post, OWNER)
                      bindRouteMap(post):
                        if showRouteMap=false → GONE
                        if routePreviewUrl present → Glide loads image
                        if status=PENDING → "Generating route preview…"
                        if status=NO_ROUTE → "No route recorded"
                        if status=FAILED → "Route preview unavailable"
```

### Delete Post → Storage Cleanup

```
WalkPostController.deletePost():
  1. walkPostQueryService.getRoutePreviewPath(postId)  ← reads path before row is gone
  2. walkPostCommandService.deletePost(postId, requesterId)  ← deletes DB row
  3. walkPostRoutePreviewService.deleteStorageImageQuietly(path)  ← deletes Supabase file
```

---

## C. Route Preview Status

### Implementation: Server-side static map image generation + Supabase Storage

| Dimension | Current (after this implementation) |
|---|---|
| Route data source | GPS polylines from `session_point_chunks` via `TrackingChunkRepository.findPolylinesBySessionAndUser()` |
| Image generation | `StaticMapImageClient` — decodes + downsamples + re-encodes polylines, calls Google Static Maps API |
| Image storage | `SupabaseRoutePreviewStorage` — uploads PNG to Supabase Storage bucket, returns public URL |
| `route_preview_url` in DB | Populated with Supabase public URL when READY, null otherwise |
| `route_preview_path` in DB | Supabase object path, used for cleanup on delete |
| `route_preview_status` in DB | PENDING → READY / NO_ROUTE / FAILED |
| Post card rendering | Glide loads `routePreviewUrl` when READY; text fallback for other states |
| Raw GPS exposure | GPS coordinates never exposed in any public API response |
| Non-participant viewers | Load `routePreviewUrl` from post response — no GPS endpoint called |

---

## D. Gap List (remaining)

| # | Gap | Current | Expected | Severity | Action |
|---|---|---|---|---|---|
| G-ENV-1 | `MAP_PROVIDER_API_KEY` not configured | Build uses empty key | Google Static Maps API key with "Maps Static API" enabled | Critical | Set env var on server |
| G-ENV-2 | Supabase Storage env vars not set | Build fails at startup | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_ROUTE_PREVIEW_BUCKET` | Critical | Set env vars + create bucket |
| G-ENV-3 | Supabase bucket not created | Upload call will fail | Public bucket "walk-route-previews" (or configured name) | Critical | Create bucket in Supabase dashboard |
| G-TEST-1 | No automated tests for route preview pipeline | Manual only | Unit tests for polyline codec, integration test for full preview flow | Medium | Add tests |

---

## E. Environment Variables Required

```
# Existing (already required)
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
...

# New — required for route preview generation
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<service-role-key-from-supabase-dashboard>
SUPABASE_ROUTE_PREVIEW_BUCKET=walk-route-previews
MAP_PROVIDER_API_KEY=<google-maps-api-key-with-static-maps-enabled>
```

---

## F. Manual Verification Checklist

```
[ ] Set all 4 new env vars on server
[ ] Create Supabase Storage bucket with configured name (public access: on)
[ ] Enable Google Static Maps API in Google Cloud Console
[ ] POST /api/v1/sessions/{sessionId}/posts with completed session → response has route_preview_status=READY and non-null route_preview_url
[ ] Walk Activity screen shows actual route image for READY post
[ ] Walk Activity screen shows "No route recorded" for zero-distance/no-GPS session
[ ] Walk Activity screen shows "Generating route preview…" immediately after posting (before reload)
[ ] DELETE /api/v1/walk-posts/{postId} → Supabase Storage object removed
[ ] Public Profile "Recent Walks" shows friend/public posts with route images
[ ] Blocked user sees empty Recent Walks
[ ] Stranger sees only PUBLIC posts
```
