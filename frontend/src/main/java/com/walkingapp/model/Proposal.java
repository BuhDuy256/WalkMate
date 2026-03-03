package com.walkingapp.model;

import com.google.gson.annotations.SerializedName;

public class Proposal {
  @SerializedName("id")
  private Integer id;

  @SerializedName("requester_user_id")
  private Integer requesterUserId;

  @SerializedName("requester_intent_id")
  private Integer requesterIntentId;

  @SerializedName("target_user_id")
  private Integer targetUserId;

  @SerializedName("target_intent_id")
  private Integer targetIntentId;

  @SerializedName("status")
  private String status;

  @SerializedName("expires_at")
  private String expiresAt;

  @SerializedName("created_at")
  private String createdAt;

  @SerializedName("updated_at")
  private String updatedAt;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getRequesterUserId() {
    return requesterUserId;
  }

  public void setRequesterUserId(Integer requesterUserId) {
    this.requesterUserId = requesterUserId;
  }

  public Integer getRequesterIntentId() {
    return requesterIntentId;
  }

  public void setRequesterIntentId(Integer requesterIntentId) {
    this.requesterIntentId = requesterIntentId;
  }

  public Integer getTargetUserId() {
    return targetUserId;
  }

  public void setTargetUserId(Integer targetUserId) {
    this.targetUserId = targetUserId;
  }

  public Integer getTargetIntentId() {
    return targetIntentId;
  }

  public void setTargetIntentId(Integer targetIntentId) {
    this.targetIntentId = targetIntentId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
