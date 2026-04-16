package com.walkmate.data.datasource.remote.dto.response.social;

import com.google.gson.annotations.SerializedName;

public class FriendRequestResponse {

    @SerializedName("requestId")
    public String requestId;

    @SerializedName("senderId")
    public String senderId;

    @SerializedName("senderName")
    public String senderName;

    @SerializedName("senderAvatarUrl")
    public String senderAvatarUrl;

    @SerializedName("receiverId")
    public String receiverId;

    @SerializedName("status")
    public String status;

    @SerializedName("createdAt")
    public String createdAt;
}
