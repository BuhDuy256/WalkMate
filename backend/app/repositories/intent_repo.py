import sqlite3
from typing import Optional, List
from datetime import datetime
from app.domain.models.intent import Intent, IntentStatus


class IntentRepository:
    def create(self, conn: sqlite3.Connection, intent: Intent) -> Intent:
        cursor = conn.execute(
            """
            INSERT INTO intents (
                user_id, walk_type, start_at, flex_minutes,
                window_start, window_end, lat, lng, radius_m, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                intent.user_id,
                intent.walk_type,
                intent.start_at.isoformat(),
                intent.flex_minutes,
                intent.window_start.isoformat(),
                intent.window_end.isoformat(),
                intent.lat,
                intent.lng,
                intent.radius_m,
                intent.status.value,
            ),
        )
        intent_id = cursor.lastrowid
        if intent_id is None:
            raise ValueError("Failed to create intent: no ID returned")
        created_intent = self.get_by_id(conn, intent_id)
        if not created_intent:
            raise ValueError("Failed to retrieve created intent")
        return created_intent

    def get_by_id(self, conn: sqlite3.Connection, intent_id: int) -> Optional[Intent]:
        cursor = conn.execute("SELECT * FROM intents WHERE id = ?", (intent_id,))
        row = cursor.fetchone()
        return self._row_to_intent(row) if row else None

    def get_by_user_id(
        self, conn: sqlite3.Connection, user_id: int
    ) -> Optional[Intent]:
        cursor = conn.execute(
            "SELECT * FROM intents WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
            (user_id,),
        )
        row = cursor.fetchone()
        return self._row_to_intent(row) if row else None

    def get_active_by_user_id(
        self, conn: sqlite3.Connection, user_id: int
    ) -> Optional[Intent]:
        cursor = conn.execute(
            "SELECT * FROM intents WHERE user_id = ? AND status IN ('OPEN', 'LOCKED') ORDER BY created_at DESC LIMIT 1",
            (user_id,),
        )
        row = cursor.fetchone()
        return self._row_to_intent(row) if row else None

    def count_active_by_user(self, conn: sqlite3.Connection, user_id: int) -> int:
        cursor = conn.execute(
            "SELECT COUNT(*) FROM intents WHERE user_id = ? AND status IN ('OPEN', 'LOCKED')",
            (user_id,),
        )
        return cursor.fetchone()[0]

    def find_open_candidates(
        self, conn: sqlite3.Connection, exclude_user_id: int
    ) -> List[Intent]:
        cursor = conn.execute(
            "SELECT * FROM intents WHERE status = 'OPEN' AND user_id != ? ORDER BY created_at",
            (exclude_user_id,),
        )
        return [self._row_to_intent(row) for row in cursor.fetchall()]

    def update_status(
        self, conn: sqlite3.Connection, intent_id: int, status: IntentStatus
    ) -> None:
        conn.execute(
            "UPDATE intents SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (status.value, intent_id),
        )

    def _row_to_intent(self, row: sqlite3.Row) -> Intent:
        return Intent(
            id=row["id"],
            user_id=row["user_id"],
            walk_type=row["walk_type"],
            start_at=datetime.fromisoformat(row["start_at"]),
            flex_minutes=row["flex_minutes"],
            window_start=datetime.fromisoformat(row["window_start"]),
            window_end=datetime.fromisoformat(row["window_end"]),
            lat=row["lat"],
            lng=row["lng"],
            radius_m=row["radius_m"],
            status=IntentStatus(row["status"]),
            created_at=datetime.fromisoformat(row["created_at"]),
            updated_at=datetime.fromisoformat(row["updated_at"]),
        )
