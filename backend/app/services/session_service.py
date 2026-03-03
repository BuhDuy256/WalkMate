import sqlite3
from datetime import datetime
from typing import Optional
from app.domain.models.proposal import Proposal, ProposalStatus
from app.domain.models.session import Session, SessionStatus
from app.domain.state_machine import StateMachine
from app.repositories.proposal_repo import ProposalRepository
from app.repositories.session_repo import SessionRepository


class SessionService:
    def __init__(self):
        self.proposal_repo = ProposalRepository()
        self.session_repo = SessionRepository()
        self.state_machine = StateMachine()

    def confirm_proposal(
        self, conn: sqlite3.Connection, proposal_id: int, user_id: int
    ) -> Proposal:
        proposal = self.proposal_repo.get_by_id(conn, proposal_id)

        if not proposal:
            raise ValueError("Proposal not found")

        if proposal.status != ProposalStatus.PROPOSED:
            raise ValueError(f"Proposal is not in PROPOSED status: {proposal.status}")

        if user_id not in {proposal.requester_user_id, proposal.target_user_id}:
            raise ValueError("User is not part of this proposal")

        if not self.state_machine.can_transition_proposal(
            proposal.status, ProposalStatus.CONFIRMED
        ):
            raise ValueError("Invalid state transition")

        self.proposal_repo.update_status(conn, proposal_id, ProposalStatus.CONFIRMED)

        session = Session(
            id=None,
            proposal_id=proposal_id,
            user_a_id=proposal.requester_user_id,
            user_b_id=proposal.target_user_id,
            status=SessionStatus.CONFIRMED,
            scheduled_start_at=datetime.now(),
        )

        self.session_repo.create(conn, session)

        updated_proposal = self.proposal_repo.get_by_id(conn, proposal_id)
        if not updated_proposal:
            raise ValueError("Failed to retrieve updated proposal")
        return updated_proposal

    def cancel_proposal(
        self, conn: sqlite3.Connection, proposal_id: int, user_id: int
    ) -> Proposal:
        proposal = self.proposal_repo.get_by_id(conn, proposal_id)

        if not proposal:
            raise ValueError("Proposal not found")

        if user_id not in {proposal.requester_user_id, proposal.target_user_id}:
            raise ValueError("User is not part of this proposal")

        if not self.state_machine.can_transition_proposal(
            proposal.status, ProposalStatus.CANCELLED
        ):
            raise ValueError("Invalid state transition")

        self.proposal_repo.update_status(conn, proposal_id, ProposalStatus.CANCELLED)

        updated_proposal = self.proposal_repo.get_by_id(conn, proposal_id)
        if not updated_proposal:
            raise ValueError("Failed to retrieve updated proposal")
        return updated_proposal

    def start_session(
        self, conn: sqlite3.Connection, session_id: int, user_id: int
    ) -> Session:
        session = self.session_repo.get_by_id(conn, session_id)

        if not session:
            raise ValueError("Session not found")

        if user_id not in {session.user_a_id, session.user_b_id}:
            raise ValueError("User is not part of this session")

        if not self.state_machine.can_transition_session(
            session.status, SessionStatus.IN_PROGRESS
        ):
            raise ValueError("Invalid state transition")

        self.session_repo.update_status(
            conn, session_id, SessionStatus.IN_PROGRESS, started_at=datetime.now()
        )

        updated_session = self.session_repo.get_by_id(conn, session_id)
        if not updated_session:
            raise ValueError("Failed to retrieve updated session")
        return updated_session

    def complete_session(
        self, conn: sqlite3.Connection, session_id: int, user_id: int
    ) -> Session:
        session = self.session_repo.get_by_id(conn, session_id)

        if not session:
            raise ValueError("Session not found")

        if user_id not in {session.user_a_id, session.user_b_id}:
            raise ValueError("User is not part of this session")

        if not self.state_machine.can_transition_session(
            session.status, SessionStatus.COMPLETED
        ):
            raise ValueError("Invalid state transition")

        self.session_repo.update_status(
            conn, session_id, SessionStatus.COMPLETED, ended_at=datetime.now()
        )

        updated_session = self.session_repo.get_by_id(conn, session_id)
        if not updated_session:
            raise ValueError("Failed to retrieve updated session")
        return updated_session
