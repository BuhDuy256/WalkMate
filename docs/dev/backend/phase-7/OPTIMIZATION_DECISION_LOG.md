# Optimization & Decision Log — Phase 7

## 1. Decoupling from All Other Domains

**Decision:** `UserProfile` is entirely independent of `WalkIntent`, `Proposal`, `Session`, `Review`, `Tracking`, and `Gamification` domains.

**How achieved:**
- The new tables (`user_profile`, `profile_tag`) reference only `user_account` (Phase 1). No FK to any Phase 2–6 table.
- `UserProfileRepository`, `UserQueryService`, `UserProfileCommandService` import nothing outside `domain/user/` and `application/user/`.
- `UserProfileController` composes only `UserQueryService`, `UserProfileCommandService`, and `AvatarStorageService`. Zero cross-domain service calls.
- Stats (`trustScore`, `totalDistanceKm`, `completedSessions`) are read from `user_account` (already present from Phases 6/12/14 migrations), not recomputed — so the profile module consumes existing data without triggering inter-domain logic.

---

## 2. Separate `user_profile` Table (Not Embedded in `user_account`)

**Decision:** Profile data lives in its own `user_profile` table, joined 1:1 via FK.

**Why not add columns to `user_account`?**
- `user_account` is the auth table. Mixing auth data (`password_hash`, `provider`, `status`) with UI-facing profile data (`bio`, `avatar_url`, `tags`) violates single-responsibility at the schema level.
- Adding nullable columns to `user_account` would grow every query in `UserJdbcRepository` even for code paths that only need email + password hash (e.g., every JWT validation lookup).
- Separate table makes Phase 7 fully reversible — drop `user_profile` and `profile_tag` without touching auth.

---

## 3. Lazy Profile Creation

**Decision:** No profile row is created at registration time. The first `GET /api/v1/profile/me` call auto-creates a blank row.

**Why not create at registration?**
- Strict decoupling constraint: modifying `UserCommandService.registerUser()` to also insert a profile row would tightly couple the auth domain to the profile domain.
- The lazy approach costs one extra upsert on the first profile fetch, which is negligible.
- DB-level: the `user_profile` PK is nullable-absent, not a forced NOT NULL column on `user_account`.

---

## 4. Gender as VARCHAR, not PG Enum

**Decision:** `gender` stored as `VARCHAR(30)` rather than a PostgreSQL `ENUM` type.

**Why:**
- PG enums require a Flyway migration to add new values (`ALTER TYPE … ADD VALUE`).
- Business requirement to add "non-binary", "intersex", etc. in the future can be done without a schema migration — only the Java `Gender` enum needs updating.
- Validation is enforced at the domain layer (Java `Gender.valueOf()` throws `INVALID_USER_DATA` on unknown strings), so the DB column is intentionally permissive.

---

## 5. Tags as a Separate Table (not JSON/Array)

**Decision:** Tags stored as rows in `profile_tag (user_id, tag_name) PK`.

**Why not `TEXT[]` or `JSONB` in `user_profile`?**
- Relational design keeps the door open for future features (tag-based search, tag popularity analytics) without a schema change.
- `replaceTags()` is one `DELETE` + N `INSERT` — simple and atomic within a transaction.
- The `ORDER BY ctid` trick preserves insertion order without adding an explicit sequence column.

---

## 6. Avatar Stored Locally, Not S3/MinIO by Default

**Decision:** `AvatarStorageService` writes to `${app.file.upload-dir}/avatars/` on the local filesystem.

**Why not MinIO immediately?**
- Local storage requires zero external infrastructure for local development — keeps the "always runnable" rule.
- The storage concern is fully encapsulated behind `AvatarStorageService`. Swapping to S3/MinIO means replacing only `store()` and `resolve()` — the controller, command service, and repository are untouched.
- Filename strategy: `{userId}_{UUID}.{ext}` — each upload gets a unique name so old URLs remain valid; no cache invalidation needed.
- Path-traversal protection: `resolve()` normalises the path and asserts it starts with `avatarDir` before returning it.

---

## 7. File Serving via Controller, Not Spring Static Resources

**Decision:** `GET /api/v1/files/avatars/{filename}` is handled by `UserProfileController`, not Spring's `ResourceHandlerRegistry`.

**Why:**
- Spring's static resource handler maps to classpath or fixed filesystem paths configured at startup. The upload directory may be a runtime-configurable absolute path, making `ResourceHandlerRegistry` awkward.
- The controller approach allows future access control (e.g., restrict avatar access to authenticated users) without Spring Security path-matching gymnastics.
- `Files.probeContentType()` serves the correct `Content-Type` header for all image formats.

---

## 8. HTTP Status: `DomainException` → 400 (not 422)

**Decision:** Age-validation failure (`INVALID_USER_DATA` thrown by `UserProfile.update()`) returns HTTP 400, consistent with all other `DomainException` mappings in `GlobalExceptionHandler`.

**Why not 422?**
- The plan spec mentions 422, but that status is reserved by the existing `MethodArgumentNotValidException` handler (Bean Validation failures). Using 422 for domain exceptions would blur the distinction between structural/format errors and semantic domain errors.
- Consistency: every `DomainException` across all phases maps to 400. Changing this for one case would surprise API consumers.
- `searchRadius = 0` still returns 422 via Bean Validation (`@Min(1)`), so the two layers are complementary, not conflicting.

---

## 9. `currentStreak` and Badges Left as Zero / Empty in Phase 7

**Decision:** `ProfileUiState.currentStreak = 0` and `badges = emptyList()` in the wired `ProfileViewModel`.

**Why:**
- Streak computation requires querying session history (cross-domain dependency on `WalkSession`). Phase 7 must stay decoupled from session domain.
- Badge data is already served by the existing `GamificationRepositoryImpl` (Phase 6). The profile screen can call that repository in a future cross-cutting profile-enhancement phase — no code is lost, only deferred.
- Placeholders are explicit (commented in `ProfileViewModel.toUiState()`) so the next engineer knows exactly what to wire.

---

## 10. `fullName` Not Stored at Registration

**Observation:** The existing `User.register(fullName, …)` validates but does not persist `fullName` — the `user_account` table has no `full_name` column.

**Decision:** Phase 7 does not backfill this. The user sets their full name on first profile edit via `PUT /api/v1/profile/me`. The blank profile created lazily defaults `full_name = ''`.

**Why not add `full_name` to `user_account` in V15?**
- Would require modifying `UserJdbcRepository.save()` and the rehydration constructor — touching the auth domain from a profile migration, breaking the decoupling constraint.
- The profile table is the correct home for display-name data by design.
