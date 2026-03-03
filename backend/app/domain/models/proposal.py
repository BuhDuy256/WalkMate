from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from enum import Enum


class ProposalStatus(str, Enum):
    PROPOSED = "PROPOSED"
    CONFIRMED = "CONFIRMED"
    EXPIRED = "EXPIRED"
    CANCELLED = "CANCELLED"


@dataclass
class Proposal:
    id: Optional[int]
    requester_user_id: int
    requester_intent_id: int
    target_user_id: int
    target_intent_id: int
    status: ProposalStatus
    expires_at: datetime
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
