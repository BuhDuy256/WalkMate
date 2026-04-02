package com.walkmate.data.datasource.remote.dto.response.notification;

import java.util.Map;

public class NotificationResponse {

    private String             notificationId;
    private String             type;
    private Map<String, Object> payload;
    private String             status;
    private String             createdAt;
    private String             readAt;

    public NotificationResponse() {
        // Gson
    }

    public String              getNotificationId() { return notificationId; }
    public String              getType()           { return type; }
    public Map<String, Object> getPayload()        { return payload; }
    public String              getStatus()         { return status; }
    public String              getCreatedAt()      { return createdAt; }
    public String              getReadAt()         { return readAt; }
}
