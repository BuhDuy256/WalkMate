from abc import ABC, abstractmethod
from typing import List, Optional
from app.domain.models.intent import Intent


class MatchingStrategy(ABC):
    @abstractmethod
    def select_match(self, current_intent: Intent, candidates: List[Intent]) -> Optional[Intent]:
        pass
