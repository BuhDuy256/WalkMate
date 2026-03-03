import sqlite3
from typing import Optional
from datetime import datetime
from app.domain.models.proposal import Proposal, ProposalStatus


class ProposalRepository:
    def create(self, conn: sqlite3.Connection, proposal: Proposal) -> Proposal:
        cursor = conn.execute(
            """
            INSERT INTO proposals (
                requester_user_id, requester_intent_id,
                target_user_id, target_intent_id,
                status, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                proposal.requester_user_id,
                proposal.requester_intent_id,
                proposal.target_user_id,
                proposal.target_intent_id,
                proposal.status.value,
                proposal.expires_at.isoformat(),
            ),
        )
        proposal_id = cursor.lastrowid
        if proposal_id is None:
            raise ValueError("Failed to create proposal: no ID returned")
        created_proposal = self.get_by_id(conn, proposal_id)
        if not created_proposal:
            raise ValueError("Failed to retrieve created proposal")
        return created_proposal

    def get_by_id(
        self, conn: sqlite3.Connection, proposal_id: int
    ) -> Optional[Proposal]:
        cursor = conn.execute("SELECT * FROM proposals WHERE id = ?", (proposal_id,))
        row = cursor.fetchone()
        return self._row_to_proposal(row) if row else None

    def get_by_user_id(
        self, conn: sqlite3.Connection, user_id: int
    ) -> Optional[Proposal]:
        cursor = conn.execute(
            """
            SELECT * FROM proposals 
            WHERE (requester_user_id = ? OR target_user_id = ?)
            ORDER BY created_at DESC LIMIT 1
            """,
            (user_id, user_id),
        )
        row = cursor.fetchone()
        return self._row_to_proposal(row) if row else None

    def get_active_by_user_id(
        self, conn: sqlite3.Connection, user_id: int
    ) -> Optional[Proposal]:
        cursor = conn.execute(
            """
            SELECT * FROM proposals 
            WHERE (requester_user_id = ? OR target_user_id = ?)
            AND status IN ('PROPOSED', 'CONFIRMED')
            ORDER BY created_at DESC LIMIT 1
            """,
            (user_id, user_id),
        )
        row = cursor.fetchone()
        return self._row_to_proposal(row) if row else None

    def count_active_by_user(self, conn: sqlite3.Connection, user_id: int) -> int:
        cursor = conn.execute(
            """
            SELECT COUNT(*) FROM proposals 
            WHERE (requester_user_id = ? OR target_user_id = ?)
            AND status IN ('PROPOSED', 'CONFIRMED')
            """,
            (user_id, user_id),
        )
        return cursor.fetchone()[0]

    def count_active_by_intent(self, conn: sqlite3.Connection, intent_id: int) -> int:
        cursor = conn.execute(
            """
            SELECT COUNT(*) FROM proposals 
            WHERE (requester_intent_id = ? OR target_intent_id = ?)
            AND status IN ('PROPOSED', 'CONFIRMED')
            """,
            (intent_id, intent_id),
        )
        return cursor.fetchone()[0]

    def update_status(
        self, conn: sqlite3.Connection, proposal_id: int, status: ProposalStatus
    ) -> None:
        conn.execute(
            "UPDATE proposals SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (status.value, proposal_id),
        )

    def _row_to_proposal(self, row: sqlite3.Row) -> Proposal:
        return Proposal(
            id=row["id"],
            requester_user_id=row["requester_user_id"],
            requester_intent_id=row["requester_intent_id"],
            target_user_id=row["target_user_id"],
            target_intent_id=row["target_intent_id"],
            status=ProposalStatus(row["status"]),
            expires_at=datetime.fromisoformat(row["expires_at"]),
            created_at=datetime.fromisoformat(row["created_at"]),
            updated_at=datetime.fromisoformat(row["updated_at"]),
        )
