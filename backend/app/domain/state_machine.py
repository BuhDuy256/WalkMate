from app.domain.models.intent import IntentStatus
from app.domain.models.proposal import ProposalStatus
from app.domain.models.session import SessionStatus


class StateMachine:
    @staticmethod
    def can_transition_intent(from_status: IntentStatus, to_status: IntentStatus) -> bool:
        valid_transitions = {
            IntentStatus.OPEN: {IntentStatus.LOCKED, IntentStatus.CANCELLED},
            IntentStatus.LOCKED: {IntentStatus.CANCELLED},
            IntentStatus.CANCELLED: set(),
        }
        return to_status in valid_transitions.get(from_status, set())

    @staticmethod
    def can_transition_proposal(from_status: ProposalStatus, to_status: ProposalStatus) -> bool:
        valid_transitions = {
            ProposalStatus.PROPOSED: {
                ProposalStatus.CONFIRMED,
                ProposalStatus.CANCELLED,
                ProposalStatus.EXPIRED,
            },
            ProposalStatus.CONFIRMED: {ProposalStatus.CANCELLED},
            ProposalStatus.EXPIRED: set(),
            ProposalStatus.CANCELLED: set(),
        }
        return to_status in valid_transitions.get(from_status, set())

    @staticmethod
    def can_transition_session(from_status: SessionStatus, to_status: SessionStatus) -> bool:
        valid_transitions = {
            SessionStatus.CONFIRMED: {
                SessionStatus.IN_PROGRESS,
                SessionStatus.CANCELLED,
            },
            SessionStatus.IN_PROGRESS: {
                SessionStatus.COMPLETED,
                SessionStatus.CANCELLED,
            },
            SessionStatus.COMPLETED: set(),
            SessionStatus.CANCELLED: set(),
        }
        return to_status in valid_transitions.get(from_status, set())
