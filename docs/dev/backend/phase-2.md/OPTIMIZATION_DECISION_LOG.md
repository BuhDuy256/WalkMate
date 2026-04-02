# Phase 2 — Optimization Decision Log

## ODL-2-001: Active walker count computation

- Decision: `activeWalkerCount` is computed at query time in `HotspotJdbcRepository`.
- Reason: Avoid denormalized counters that can drift from source-of-truth tables.
- Formula used:
  - Open intents: count of `walk_intent` rows where `status = 'OPEN'` per hotspot.
  - Live sessions: count of `walk_session` rows where `status IN ('PENDING', 'ACTIVE')`, multiplied by 2 walkers per session, grouped by hotspot using `source_intent_id_a` / `source_intent_id_b` joins.

## ODL-2-002: Runtime-safe fallback path

- Decision: Add SQL fallback from `walk_session` join query to `walk_intent`-only query on SQL grammar errors.
- Reason: Keep the app runnable in environments where `walk_session` or its columns are not migrated yet.
- Tradeoff: In fallback mode, active walkers from live sessions are not counted until full schema is present.

## ODL-2-003: API contract field naming

- Decision: Backend hotspot response now uses `activeWalkerCount` (camelCase).
- Reason: Align with API contract and Android domain model field naming.
- Compatibility: Android DTO accepts alternates (`active_walker_count`, `active_intent_count`) to avoid breakage during rollout.

## ODL-2-004: Seed migration strategy

- Decision: Use a non-destructive migration (`V5__seed_hotspots_phase2.sql`) with `UPDATE` + `INSERT ... ON CONFLICT`.
- Reason: Preserve existing environments and avoid drop/recreate side effects.
- Result: Canonical 5-location catalogue delivered without destructive operations.

## ODL-2-005: Public hotspot access

- Decision: Explicitly permit anonymous GET access to hotspot endpoints in `SecurityConfig`.
- Reason: Home map and catalogue browsing should work before authentication, consistent with product flow.
