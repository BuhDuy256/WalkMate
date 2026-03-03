import sqlite3
from typing import Optional
from datetime import datetime
from app.domain.models.session import Session, SessionStatus


class SessionRepository:
    def create(self, conn: sqlite3.Connection, session: Session) -> Session:
        cursor = conn.execute(
            """
            INSERT INTO sessions (
                proposal_id, user_a_id, user_b_id,
                status, scheduled_start_at
            ) VALUES (?, ?, ?, ?, ?)
            """,
            (
                session.proposal_id,
                session.user_a_id,
                session.user_b_id,
                session.status.value,
                session.scheduled_start_at.isoformat(),
            ),
        )
        session_id = cursor.lastrowid
        if session_id is None:
            raise ValueError("Failed to create session: no ID returned")
        created_session = self.get_by_id(conn, session_id)
        if not created_session:
            raise ValueError("Failed to retrieve created session")
        return created_session

    def get_by_id(
        self, conn: sqlite3.Connection, session_id: int
    ) -> Optional[Session]:
        cursor = conn.execute("SELECT * FROM sessions WHERE id = ?", (session_id,))
        row = cursor.fetchone()
        return self._row_to_session(row) if row else None

    def get_by_user_id(
        self, conn: sqlite3.Connection, user_id: int
    ) -> Optional[Session]:
        cursor = conn.execute(
            """
            SELECT * FROM sessions 
            WHERE (user_a_id = ? OR user_b_id = ?)
            ORDER BY created_at DESC LIMIT 1
            """,
            (user_id, user_id),
        )
        row = cursor.fetchone()
        return self._row_to_session(row) if row else None

    def get_active_by_user_id(
        self, conn: sqlite3.Connection, user_id: int
    ) -> Optional[Session]:
        cursor = conn.execute(
            """
            SELECT * FROM sessions 
            WHERE (user_a_id = ? OR user_b_id = ?)
            AND status IN ('CONFIRMED', 'IN_PROGRESS')
            ORDER BY created_at DESC LIMIT 1
            """,
            (user_id, user_id),
        )
        row = cursor.fetchone()
        return self._row_to_session(row) if row else None

    def count_active_by_user(self, conn: sqlite3.Connection, user_id: int) -> int:
        cursor = conn.execute(
            """
            SELECT COUNT(*) FROM sessions 
            WHERE (user_a_id = ? OR user_b_id = ?)
            AND status IN ('CONFIRMED', 'IN_PROGRESS')
            """,
            (user_id, user_id),
        )
        return cursor.fetchone()[0]

    def update_status(
        self,
        conn: sqlite3.Connection,
        session_id: int,
        status: SessionStatus,
        started_at: Optional[datetime] = None,
        ended_at: Optional[datetime] = None,
    ) -> None:
        if started_at and ended_at:
            conn.execute(
                """
                UPDATE sessions 
                SET status = ?, started_at = ?, ended_at = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE id = ?
                """,
                (status.value, started_at.isoformat(), ended_at.isoformat(), session_id),
            )
        elif started_at:
            conn.execute(
                """
                UPDATE sessions 
                SET status = ?, started_at = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE id = ?
                """,
                (status.value, started_at.isoformat(), session_id),
            )
        elif ended_at:
            conn.execute(
                """
                UPDATE sessions 
                SET status = ?, ended_at = ?, updated_at = CURRENT_TIMESTAMP 
                WHERE id = ?
                """,
                (status.value, ended_at.isoformat(), session_id),
            )
        else:
            conn.execute(
                "UPDATE sessions SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                (status.value, session_id),
            )

    def _row_to_session(self, row: sqlite3.Row) -> Session:
        return Session(
            id=row["id"],
            proposal_id=row["proposal_id"],
            user_a_id=row["user_a_id"],
            user_b_id=row["user_b_id"],
            status=SessionStatus(row["status"]),
            scheduled_start_at=datetime.fromisoformat(row["scheduled_start_at"]),
            started_at=(
                datetime.fromisoformat(row["started_at"]) if row["started_at"] else None
            ),
            ended_at=(
                datetime.fromisoformat(row["ended_at"]) if row["ended_at"] else None
            ),
            created_at=datetime.fromisoformat(row["created_at"]),
            updated_at=datetime.fromisoformat(row["updated_at"]),
        )
