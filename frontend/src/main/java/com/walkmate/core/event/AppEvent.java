package com.walkmate.core.event;

/**
 * Immutable event payload posted to AppEventBus.
 *
 * Add new Type values as the app gains more push-triggered features.
 */
public class AppEvent {

    public enum Type {
        MATCH_FOUND
    }

    public final Type   type;
    public final String intentId;
    public final String proposalId;

    public AppEvent(Type type, String intentId, String proposalId) {
        this.type       = type;
        this.intentId   = intentId;
        this.proposalId = proposalId;
    }
}
