# Optimization & Decision Log — Phase 1

- **Decision 1:** Split user account migrations into `V1_1` and `V1_2` (fractional versioning) instead of `V5`/`V6`.
  `V2__create_walk_intent.sql` already contains `REFERENCES user_account(user_id)`. Putting user account at V5 would make a fresh-database bootstrap impossible. Fractional versions (`1.1`, `1.2`) let Flyway run them between V1 and V2 on a clean DB while still applying them on existing DBs when combined with `out-of-order=true`.

- **Decision 2:** Added `spring.flyway.out-of-order=true` to `application.properties`.
  Without this flag, Flyway refuses to apply V1.1/V1.2 on databases that already ran V2–V4 (which were created before the user table migrations existed). Out-of-order mode makes the history append-only and safe for this recovery scenario.

- **Decision 3:** Used idempotent DDL patterns (`IF NOT EXISTS`, `DO $$ BEGIN IF NOT EXISTS ... END $$`) for all new migrations.
  The Supabase-hosted development database had `user_account` created manually before Flyway was introduced. Idempotent DDL ensures migrations are safe to run whether the object exists or not, without data loss.

- **Decision 4:** Kept `password_hash VARCHAR(72)` instead of `VARCHAR(255)`.
  BCrypt output is always exactly 60 characters. 72 bytes is the maximum input length BCrypt processes. Using 72 as the column length documents this constraint directly in the schema and avoids storing oversized values.

- **Decision 5:** Used Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server` + `NimbusJwtEncoder/Decoder`) instead of adding the JJWT library.
  The Spring Security OAuth2 stack provides production-grade JWT signing and verification with first-class Spring integration (e.g., `JwtAuthenticationConverter`). JJWT would be a redundant dependency for the same capability.

- **Decision 6:** `LoginUserResponse` returns `tokenType` and `expiresIn` alongside `accessToken`.
  The frontend can display session duration and correctly format the `Authorization: Bearer` header without hardcoding either value. The Android `AuthInterceptor` already prepends "Bearer " automatically, but the contract is explicit.

- **Decision 7:** Frontend `UserRepositoryImpl` propagates raw backend error codes (e.g., `USER_INVALID_CREDENTIALS`) as the `Exception` message rather than mapping them to localised strings at the data layer.
  Error-to-string mapping belongs in the UI/ViewModel layer where locale context is available. The data layer stays decoupled from display concerns.

- **Decision 8:** `@Tag(name = "Auth")` added to `UserController` but no `@Operation` annotations on individual endpoints.
  Springdoc generates accurate summaries from method names and DTOs automatically. Verbose `@Operation` annotations would duplicate information and create a maintenance burden. Add them only if the auto-generated description is misleading.
