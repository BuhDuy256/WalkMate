package com.walkingapp.model;

import com.google.gson.annotations.SerializedName;

public class Session {
  @SerializedName("id")
  private Integer id;

  @SerializedName("proposal_id")
  private Integer proposalId;

  @SerializedName("user_a_id")
  private Integer userAId;

  @SerializedName("user_b_id")
  private Integer userBId;

  @SerializedName("status")
  private String status;

  @SerializedName("scheduled_start_at")
  private String scheduledStartAt;

  @SerializedName("started_at")
  private String startedAt;

  @SerializedName("ended_at")
  private String endedAt;

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

  public Integer getProposalId() {
    return proposalId;
  }

  public void setProposalId(Integer proposalId) {
    this.proposalId = proposalId;
  }

  public Integer getUserAId() {
    return userAId;
  }

  public void setUserAId(Integer userAId) {
    this.userAId = userAId;
  }

  public Integer getUserBId() {
    return userBId;
  }

  public void setUserBId(Integer userBId) {
    this.userBId = userBId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getScheduledStartAt() {
    return scheduledStartAt;
  }

  public void setScheduledStartAt(String scheduledStartAt) {
    this.scheduledStartAt = scheduledStartAt;
  }

  public String getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(String startedAt) {
    this.startedAt = startedAt;
  }

  public String getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(String endedAt) {
    this.endedAt = endedAt;
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
