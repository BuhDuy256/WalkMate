from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from datetime import datetime, timedelta
from app.infrastructure.database import get_db_connection
from app.domain.models.intent import Intent, IntentStatus
from app.domain.invariants import Invariants
from app.repositories.intent_repo import IntentRepository

router = APIRouter()


class CreateIntentRequest(BaseModel):
    user_id: int
    walk_type: str
    start_at: str
    flex_minutes: int
    lat: float
    lng: float
    radius_m: int


class IntentResponse(BaseModel):
    id: int
    user_id: int
    walk_type: str
    start_at: str
    flex_minutes: int
    window_start: str
    window_end: str
    lat: float
    lng: float
    radius_m: int
    status: str
    created_at: str
    updated_at: str


@router.post("/api/intents", response_model=IntentResponse)
def create_intent(request: CreateIntentRequest):
    try:
        Invariants.validate_flex_minutes(request.flex_minutes)

        start_at = datetime.fromisoformat(request.start_at)
        window_start = start_at - timedelta(minutes=request.flex_minutes)
        window_end = start_at + timedelta(minutes=request.flex_minutes)

        with get_db_connection() as conn:
            intent_repo = IntentRepository()

            active_count = intent_repo.count_active_by_user(conn, request.user_id)
            Invariants.validate_one_active_intent_per_user(request.user_id, active_count)

            intent = Intent(
                id=None,
                user_id=request.user_id,
                walk_type=request.walk_type,
                start_at=start_at,
                flex_minutes=request.flex_minutes,
                window_start=window_start,
                window_end=window_end,
                lat=request.lat,
                lng=request.lng,
                radius_m=request.radius_m,
                status=IntentStatus.OPEN,
            )

            created_intent = intent_repo.create(conn, intent)
            conn.commit()

            return IntentResponse(
                id=created_intent.id,
                user_id=created_intent.user_id,
                walk_type=created_intent.walk_type,
                start_at=created_intent.start_at.isoformat(),
                flex_minutes=created_intent.flex_minutes,
                window_start=created_intent.window_start.isoformat(),
                window_end=created_intent.window_end.isoformat(),
                lat=created_intent.lat,
                lng=created_intent.lng,
                radius_m=created_intent.radius_m,
                status=created_intent.status.value,
                created_at=created_intent.created_at.isoformat(),
                updated_at=created_intent.updated_at.isoformat(),
            )

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/api/intents/user/{user_id}", response_model=IntentResponse)
def get_user_intent(user_id: int):
    try:
        with get_db_connection() as conn:
            intent_repo = IntentRepository()
            intent = intent_repo.get_by_user_id(conn, user_id)

            if not intent:
                raise HTTPException(status_code=404, detail="Intent not found")

            return IntentResponse(
                id=intent.id,
                user_id=intent.user_id,
                walk_type=intent.walk_type,
                start_at=intent.start_at.isoformat(),
                flex_minutes=intent.flex_minutes,
                window_start=intent.window_start.isoformat(),
                window_end=intent.window_end.isoformat(),
                lat=intent.lat,
                lng=intent.lng,
                radius_m=intent.radius_m,
                status=intent.status.value,
                created_at=intent.created_at.isoformat(),
                updated_at=intent.updated_at.isoformat(),
            )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
