# Optimization & Decision Log

- **Decision 1:** Unified `CONSUMED` as the canonical backend state for "Matched" intents to align with the DB schema.
- **Decision 2:** Frontend will handle display labels (e.g., "Đang chờ") while the API contract strictly uses enum keys.
- **Decision 3:** Added `date` field to `CreateWalkIntentRequest` to allow deterministic ISO-8601 timestamp conversion on the backend.
- **Decision 4:** Introduced `ABORTED` state for emergency mid-walk cancellations to improve safety tracking.