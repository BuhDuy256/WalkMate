import math
from typing import List, Optional
from app.domain.models.intent import Intent
from app.strategies.matching_strategy import MatchingStrategy


class FirstCompatibleStrategy(MatchingStrategy):
    def select_match(self, current_intent: Intent, candidates: List[Intent]) -> Optional[Intent]:
        if not candidates:
            return None

        compatible = []

        for candidate in candidates:
            if self._is_compatible(current_intent, candidate):
                distance = self._calculate_distance(
                    current_intent.lat,
                    current_intent.lng,
                    candidate.lat,
                    candidate.lng,
                )
                compatible.append((candidate, distance))

        if not compatible:
            return None

        compatible.sort(key=lambda x: (x[1], x[0].created_at, x[0].id))

        return compatible[0][0]

    def _is_compatible(self, intent_a: Intent, intent_b: Intent) -> bool:
        if intent_a.walk_type != intent_b.walk_type:
            return False

        if not self._has_time_overlap(intent_a, intent_b):
            return False

        distance = self._calculate_distance(
            intent_a.lat, intent_a.lng, intent_b.lat, intent_b.lng
        )

        min_radius = min(intent_a.radius_m, intent_b.radius_m)

        return distance <= min_radius

    def _has_time_overlap(self, intent_a: Intent, intent_b: Intent) -> bool:
        return (
            intent_a.window_start < intent_b.window_end
            and intent_b.window_start < intent_a.window_end
        )

    def _calculate_distance(
        self, lat1: float, lng1: float, lat2: float, lng2: float
    ) -> float:
        R = 6371000

        lat1_rad = math.radians(lat1)
        lat2_rad = math.radians(lat2)
        delta_lat = math.radians(lat2 - lat1)
        delta_lng = math.radians(lng2 - lng1)

        a = math.sin(delta_lat / 2) ** 2 + math.cos(lat1_rad) * math.cos(
            lat2_rad
        ) * math.sin(delta_lng / 2) ** 2

        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

        return R * c
