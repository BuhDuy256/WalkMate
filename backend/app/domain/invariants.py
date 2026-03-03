from typing import List


class Invariants:
    @staticmethod
    def validate_one_active_intent_per_user(user_id: int, active_count: int) -> None:
        if active_count > 0:
            raise ValueError(f"User {user_id} already has an active intent")

    @staticmethod
    def validate_one_active_proposal_per_user(user_id: int, active_count: int) -> None:
        if active_count > 0:
            raise ValueError(f"User {user_id} already has an active proposal")

    @staticmethod
    def validate_one_active_session_per_user(user_id: int, active_count: int) -> None:
        if active_count > 0:
            raise ValueError(f"User {user_id} already has an active session")

    @staticmethod
    def validate_exclusive_pairing(intent_id: int, active_proposal_count: int) -> None:
        if active_proposal_count > 0:
            raise ValueError(f"Intent {intent_id} is already locked by another proposal")

    @staticmethod
    def validate_flex_minutes(flex_minutes: int) -> None:
        if flex_minutes not in {30, 60}:
            raise ValueError("flex_minutes must be either 30 or 60")

    @staticmethod
    def validate_different_users(user_a: int, user_b: int) -> None:
        if user_a == user_b:
            raise ValueError("Cannot match with yourself")
