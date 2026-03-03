from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from enum import Enum


class SessionStatus(str, Enum):
    CONFIRMED = "CONFIRMED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


@dataclass
class Session:
    id: Optional[int]
    proposal_id: int
    user_a_id: int
    user_b_id: int
    status: SessionStatus
    scheduled_start_at: datetime
    started_at: Optional[datetime] = None
    ended_at: Optional[datetime] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
