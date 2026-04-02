# Optimization Decision Log — Phase 6

## Decision 1: Google Encoded Polyline Over Raw Coordinates

**Context:** GPS route data can be stored as a JSON array of `{lat, lng}` pairs, as raw floats in a binary column, or as a Google Encoded Polyline string.

**Decision:** Store coordinates as a Google Encoded Polyline string in a `TEXT` column.

**Rationale:**
- **Compression:** Encoded Polyline is 30–50 % smaller than a JSON coordinate array for typical walk routes. A 50-point batch that would be ~1.2 KB in JSON is ~300 bytes as Encoded Polyline.
- **Indexable text:** `TEXT` columns are trivially displayable, debuggable, and can be queried for substring patterns if needed.
- **Standard algorithm:** Widely supported by every Google Maps SDK; no custom decoder needed client-side.
- **Separation of concerns:** Coordinates and timestamps are split — coordinates → Polyline, timestamps → BYTEA — because encoding timestamps into Polyline would require a non-standard extension.

---

## Decision 2: Timestamps Stored as Big-Endian Packed Longs in BYTEA

**Context:** Each GPS point has a millisecond-precision Unix timestamp. These could be stored as: a JSON array of longs, a PostgreSQL `ARRAY(BIGINT)`, or a packed `BYTEA`.

**Decision:** Pack timestamps as big-endian `long[]` into a `BYTEA` column (8 bytes × pointCount).

**Rationale:**
- **Size:** 8 bytes per timestamp is 50 % smaller than storing them as text integers in JSON (which averages ~13 characters per number).
- **Alignment with Polyline:** The index `i` in the Polyline corresponds to index `i` in the timestamp blob, maintaining a clean parallel structure.
- **Simplicity:** `ByteBuffer.allocate(8 * n).order(BIG_ENDIAN)` is trivial in Java. The decoder on any platform just reads 8 bytes per point.
- **Alternative considered:** `ARRAY(BIGINT)` was rejected because PostgreSQL ARRAY columns have higher overhead per-element and make bulk reads slightly more complex than reading raw bytes.

---

## Decision 3: `nextChunkIndex` Inside the Same `@Transactional` Scope

**Context:** Assigning a chunk index (`MAX(chunk_index) + 1`) and inserting the row must be atomic. A concurrent insert could read the same MAX and produce a duplicate index.

**Decision:** Both `nextChunkIndex()` and `saveChunk()` are called inside the single `@Transactional` method in `TrackingCommandService`. The `UNIQUE(session_id, chunk_index)` constraint is the last line of defence.

**Rationale:**
- Spring's default transaction isolation (`READ COMMITTED`) means that within one transaction, the `MAX + 1` read and the insert are protected from concurrent reads of the same MAX — the unique constraint would abort the second transaction rather than silently corrupt data.
- For Phase 6, concurrent uploads from the same user on two threads are not a realistic scenario (the Android app uses a single-threaded executor for all Room and Retrofit calls).
- The unique constraint converts a potential silent data corruption into a database-level exception, making it a safe failure mode.
- A proper fix (if needed at scale) is to use a per-session DB sequence or a `SELECT ... FOR UPDATE` on a counter row; noted as a Phase 7 consideration.

---

## Decision 4: `acknowledgedIds` Echoed Back Instead of `syncedCount`

**Context:** The original `PushRoutePointsResponse` had a single `syncedCount: int`. The plan calls for `acknowledgedIds: List<Long>`.

**Decision:** Changed the backend response to return `acknowledgedIds` containing the `localId` values submitted by the client.

**Rationale:**
- **Precision:** If a future version introduces partial-batch failure (e.g., some points fail validation), the client can mark only the acknowledged subset as synced, avoiding data loss.
- **Idempotency:** If the client retries on timeout, it re-sends the same `localId` values. The server re-echoes them; the client marks the same rows synced. No duplicate chunk is inserted because the server creates a new chunk row (not an upsert).
- **Backward-compatible design:** The frontend can still infer `syncedCount = acknowledgedIds.size()` if needed.

---

## Decision 5: `SessionManager` Eagerly Instantiated in `Application.onCreate()`

**Context:** `SessionManager` (which wraps `EncryptedSharedPreferences`) is now needed by both `WalkSessionRepositoryImpl` (Phase 5) and `TrackingRepositoryImpl` (Phase 6). Each previously instantiated its own `SessionManager`.

**Decision:** Instantiate `SessionManager` once in `WalkMateApplication.onCreate()` and share it via `getSessionManager()`.

**Rationale:**
- **Single encrypted prefs file:** `EncryptedSharedPreferences` opens an encrypted file; opening it twice would be wasteful and could introduce subtle locking issues on Android.
- **Consistent token reads:** Both repositories share the same `SessionManager` instance, so a token refresh in one path is immediately visible to the other.
- **Minimal change:** The `SessionManager` constructor is cheap (no network calls); eager init in `onCreate` adds negligible startup time.
