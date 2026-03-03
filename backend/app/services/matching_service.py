import sqlite3
from datetime import datetime, timedelta
from typing import Optional
from app.domain.models.intent import Intent, IntentStatus
from app.domain.models.proposal import Proposal, ProposalStatus
from app.domain.invariants import Invariants
from app.repositories.intent_repo import IntentRepository
from app.repositories.proposal_repo import ProposalRepository
from app.strategies.matching_strategy import MatchingStrategy


class MatchingService:
    def __init__(self, strategy: MatchingStrategy):
        self.strategy = strategy
        self.intent_repo = IntentRepository()
        self.proposal_repo = ProposalRepository()
        self.invariants = Invariants()

    def find_match(self, conn: sqlite3.Connection, user_id: int) -> Optional[Proposal]:
        current_intent = self.intent_repo.get_active_by_user_id(conn, user_id)

        if not current_intent:
            raise ValueError("No active intent found for user")
        
        if current_intent.id is None:
            raise ValueError("Intent ID cannot be None")

        if current_intent.status != IntentStatus.OPEN:
            raise ValueError("Intent is not OPEN")

        active_proposal_count = self.proposal_repo.count_active_by_user(conn, user_id)
        if active_proposal_count > 0:
            raise ValueError("User already has an active proposal")

        candidates = self.intent_repo.find_open_candidates(conn, user_id)

        matched_intent = self.strategy.select_match(current_intent, candidates)

        if not matched_intent:
            return None
        
        if matched_intent.id is None:
            raise ValueError("Matched intent ID cannot be None")

        self.invariants.validate_different_users(user_id, matched_intent.user_id)

        intent_proposal_count = self.proposal_repo.count_active_by_intent(
            conn, matched_intent.id
        )
        self.invariants.validate_exclusive_pairing(matched_intent.id, intent_proposal_count)

        proposal = Proposal(
            id=None,
            requester_user_id=user_id,
            requester_intent_id=current_intent.id,
            target_user_id=matched_intent.user_id,
            target_intent_id=matched_intent.id,
            status=ProposalStatus.PROPOSED,
            expires_at=datetime.now() + timedelta(hours=24),
        )

        created_proposal = self.proposal_repo.create(conn, proposal)

        self.intent_repo.update_status(conn, current_intent.id, IntentStatus.LOCKED)
        self.intent_repo.update_status(conn, matched_intent.id, IntentStatus.LOCKED)

        return created_proposal
