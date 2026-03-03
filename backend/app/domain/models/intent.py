from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from enum import Enum


class IntentStatus(str, Enum):
    OPEN = "OPEN"
    LOCKED = "LOCKED"
    CANCELLED = "CANCELLED"


@dataclass
class Intent:
    id: Optional[int]
    user_id: int
    walk_type: str
    start_at: datetime
    flex_minutes: int
    window_start: datetime
    window_end: datetime
    lat: float
    lng: float
    radius_m: int
    status: IntentStatus
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
