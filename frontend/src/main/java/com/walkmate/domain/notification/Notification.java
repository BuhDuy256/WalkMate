package com.walkmate.domain.notification;

import java.util.Map;

public class Notification {

    public enum Status { PENDING, SENT, READ }

    public enum Type {
        PROPOSAL_RECEIVED,
        INVITE_SENT,
        PROPOSAL_ACCEPTED,
        SESSION_CONFIRMED,
        SESSION_ACTIVE,
        REVIEW_REQUESTED,
        FRIEND_REQUEST_RECEIVED,
        FRIEND_REQUEST_ACCEPTED,
        FRIEND_REQUEST_DECLINED
    }

    private final String             notificationId;
    private final Type               type;
    private final Map<String, Object> payload;
    private final Status             status;
    private final String             createdAt; // ISO-8601 string
    private final String             readAt;    // ISO-8601 string or null

    public Notification(String notificationId, Type type, Map<String, Object> payload,
                        Status status, String createdAt, String readAt) {
        this.notificationId = notificationId;
        this.type           = type;
        this.payload        = payload;
        this.status         = status;
        this.createdAt      = createdAt;
        this.readAt         = readAt;
    }

    public String              getNotificationId() { return notificationId; }
    public Type                getType()           { return type; }
    public Map<String, Object> getPayload()        { return payload; }
    public Status              getStatus()         { return status; }
    public String              getCreatedAt()      { return createdAt; }
    public String              getReadAt()         { return readAt; }

    public boolean isRead() { return status == Status.READ; }
}
