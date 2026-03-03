from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from app.infrastructure.database import get_db_connection, get_db_transaction
from app.repositories.proposal_repo import ProposalRepository
from app.repositories.session_repo import SessionRepository
from app.services.session_service import SessionService

router = APIRouter()


class ProposalResponse(BaseModel):
    id: int
    requester_user_id: int
    requester_intent_id: int
    target_user_id: int
    target_intent_id: int
    status: str
    expires_at: str
    created_at: str
    updated_at: str


class SessionResponse(BaseModel):
    id: int
    proposal_id: int
    user_a_id: int
    user_b_id: int
    status: str
    scheduled_start_at: str
    started_at: str | None
    ended_at: str | None
    created_at: str
    updated_at: str


class ConfirmProposalRequest(BaseModel):
    user_id: int


class StartSessionRequest(BaseModel):
    user_id: int


@router.get("/api/proposals/{proposal_id}", response_model=ProposalResponse)
def get_proposal(proposal_id: int):
    try:
        with get_db_connection() as conn:
            proposal_repo = ProposalRepository()
            proposal = proposal_repo.get_by_id(conn, proposal_id)

            if not proposal:
                raise HTTPException(status_code=404, detail="Proposal not found")

            return ProposalResponse(
                id=proposal.id,
                requester_user_id=proposal.requester_user_id,
                requester_intent_id=proposal.requester_intent_id,
                target_user_id=proposal.target_user_id,
                target_intent_id=proposal.target_intent_id,
                status=proposal.status.value,
                expires_at=proposal.expires_at.isoformat(),
                created_at=proposal.created_at.isoformat(),
                updated_at=proposal.updated_at.isoformat(),
            )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/api/proposals/user/{user_id}", response_model=ProposalResponse)
def get_user_proposal(user_id: int):
    try:
        with get_db_connection() as conn:
            proposal_repo = ProposalRepository()
            proposal = proposal_repo.get_by_user_id(conn, user_id)

            if not proposal:
                raise HTTPException(status_code=404, detail="Proposal not found")

            return ProposalResponse(
                id=proposal.id,
                requester_user_id=proposal.requester_user_id,
                requester_intent_id=proposal.requester_intent_id,
                target_user_id=proposal.target_user_id,
                target_intent_id=proposal.target_intent_id,
                status=proposal.status.value,
                expires_at=proposal.expires_at.isoformat(),
                created_at=proposal.created_at.isoformat(),
                updated_at=proposal.updated_at.isoformat(),
            )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/api/proposals/{proposal_id}/confirm", response_model=ProposalResponse)
def confirm_proposal(proposal_id: int, request: ConfirmProposalRequest):
    try:
        session_service = SessionService()

        with get_db_transaction() as conn:
            proposal = session_service.confirm_proposal(
                conn, proposal_id, request.user_id
            )

            return ProposalResponse(
                id=proposal.id,
                requester_user_id=proposal.requester_user_id,
                requester_intent_id=proposal.requester_intent_id,
                target_user_id=proposal.target_user_id,
                target_intent_id=proposal.target_intent_id,
                status=proposal.status.value,
                expires_at=proposal.expires_at.isoformat(),
                created_at=proposal.created_at.isoformat(),
                updated_at=proposal.updated_at.isoformat(),
            )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/api/proposals/{proposal_id}/cancel", response_model=ProposalResponse)
def cancel_proposal(proposal_id: int, request: ConfirmProposalRequest):
    try:
        session_service = SessionService()

        with get_db_transaction() as conn:
            proposal = session_service.cancel_proposal(
                conn, proposal_id, request.user_id
            )

            return ProposalResponse(
                id=proposal.id,
                requester_user_id=proposal.requester_user_id,
                requester_intent_id=proposal.requester_intent_id,
                target_user_id=proposal.target_user_id,
                target_intent_id=proposal.target_intent_id,
                status=proposal.status.value,
                expires_at=proposal.expires_at.isoformat(),
                created_at=proposal.created_at.isoformat(),
                updated_at=proposal.updated_at.isoformat(),
            )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/api/sessions/{session_id}", response_model=SessionResponse)
def get_session(session_id: int):
    try:
        with get_db_connection() as conn:
            session_repo = SessionRepository()
            session = session_repo.get_by_id(conn, session_id)

            if not session:
                raise HTTPException(status_code=404, detail="Session not found")

            return SessionResponse(
                id=session.id,
                proposal_id=session.proposal_id,
                user_a_id=session.user_a_id,
                user_b_id=session.user_b_id,
                status=session.status.value,
                scheduled_start_at=session.scheduled_start_at.isoformat(),
                started_at=(
                    session.started_at.isoformat() if session.started_at else None
                ),
                ended_at=session.ended_at.isoformat() if session.ended_at else None,
                created_at=session.created_at.isoformat(),
                updated_at=session.updated_at.isoformat(),
            )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/api/sessions/user/{user_id}", response_model=SessionResponse)
def get_user_session(user_id: int):
    try:
        with get_db_connection() as conn:
            session_repo = SessionRepository()
            session = session_repo.get_by_user_id(conn, user_id)

            if not session:
                raise HTTPException(status_code=404, detail="Session not found")

            return SessionResponse(
                id=session.id,
                proposal_id=session.proposal_id,
                user_a_id=session.user_a_id,
                user_b_id=session.user_b_id,
                status=session.status.value,
                scheduled_start_at=session.scheduled_start_at.isoformat(),
                started_at=(
                    session.started_at.isoformat() if session.started_at else None
                ),
                ended_at=session.ended_at.isoformat() if session.ended_at else None,
                created_at=session.created_at.isoformat(),
                updated_at=session.updated_at.isoformat(),
            )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/api/sessions/{session_id}/start", response_model=SessionResponse)
def start_session(session_id: int, request: StartSessionRequest):
    try:
        session_service = SessionService()

        with get_db_transaction() as conn:
            session = session_service.start_session(conn, session_id, request.user_id)

            return SessionResponse(
                id=session.id,
                proposal_id=session.proposal_id,
                user_a_id=session.user_a_id,
                user_b_id=session.user_b_id,
                status=session.status.value,
                scheduled_start_at=session.scheduled_start_at.isoformat(),
                started_at=(
                    session.started_at.isoformat() if session.started_at else None
                ),
                ended_at=session.ended_at.isoformat() if session.ended_at else None,
                created_at=session.created_at.isoformat(),
                updated_at=session.updated_at.isoformat(),
            )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.put("/api/sessions/{session_id}/complete", response_model=SessionResponse)
def complete_session(session_id: int, request: StartSessionRequest):
    try:
        session_service = SessionService()

        with get_db_transaction() as conn:
            session = session_service.complete_session(
                conn, session_id, request.user_id
            )

            return SessionResponse(
                id=session.id,
                proposal_id=session.proposal_id,
                user_a_id=session.user_a_id,
                user_b_id=session.user_b_id,
                status=session.status.value,
                scheduled_start_at=session.scheduled_start_at.isoformat(),
                started_at=(
                    session.started_at.isoformat() if session.started_at else None
                ),
                ended_at=session.ended_at.isoformat() if session.ended_at else None,
                created_at=session.created_at.isoformat(),
                updated_at=session.updated_at.isoformat(),
            )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
