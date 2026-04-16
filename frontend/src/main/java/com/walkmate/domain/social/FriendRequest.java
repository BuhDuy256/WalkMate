package com.walkmate.domain.social;

public class FriendRequest {

    private final String requestId;
    private final String senderId;
    private final String senderName;
    private final String senderAvatarUrl;
    private final String receiverId;
    private final String status; // "PENDING" | "ACCEPTED" | "DECLINED"
    private final String createdAt;

    public FriendRequest(String requestId, String senderId, String senderName,
                         String senderAvatarUrl, String receiverId,
                         String status, String createdAt) {
        this.requestId      = requestId;
        this.senderId       = senderId;
        this.senderName     = senderName;
        this.senderAvatarUrl = senderAvatarUrl;
        this.receiverId     = receiverId;
        this.status         = status;
        this.createdAt      = createdAt;
    }

    public String getRequestId()       { return requestId; }
    public String getSenderId()        { return senderId; }
    public String getSenderName()      { return senderName; }
    public String getSenderAvatarUrl() { return senderAvatarUrl; }
    public String getReceiverId()      { return receiverId; }
    public String getStatus()          { return status; }
    public String getCreatedAt()       { return createdAt; }
}
