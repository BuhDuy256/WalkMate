from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from app.infrastructure.database import get_db_transaction
from app.services.matching_service import MatchingService
from app.strategies.first_compatible import FirstCompatibleStrategy

router = APIRouter()


class MatchRequest(BaseModel):
    user_id: int


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


@router.post("/api/match", response_model=ProposalResponse)
def find_match(request: MatchRequest):
    try:
        strategy = FirstCompatibleStrategy()
        matching_service = MatchingService(strategy)

        with get_db_transaction() as conn:
            proposal = matching_service.find_match(conn, request.user_id)

            if not proposal:
                raise HTTPException(status_code=404, detail="No match found")

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
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
