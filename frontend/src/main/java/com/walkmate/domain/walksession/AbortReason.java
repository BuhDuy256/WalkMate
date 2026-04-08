package com.walkmate.domain.walksession;

public enum AbortReason {
    SAFETY_CONCERN, EMERGENCY, PARTNER_MISCONDUCT, OTHER;

    public String toApiValue() { return name(); }
}
